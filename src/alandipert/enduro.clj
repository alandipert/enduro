(ns alandipert.enduro
  (:refer-clojure :exclude [atom swap! reset!])
  (:require [clojure.java.io :as io]
            [clojure.core :as core])
  (:import (java.io File FileOutputStream OutputStreamWriter PushbackReader)
           java.util.concurrent.atomic.AtomicReference))

(defprotocol IDurableResource
  "Represents a durable resource."
  (-commit! [this value] "Attempt to commit value to resource, returning true if successful and false otherwise.")
  (-release-resource! [this] "Release the underlying resource."))

(def files-in-use (core/atom #{}))

(deftype FileBackend [^File f]
  IDurableResource
  (-commit!
    [this value]
    (let [pending (File/createTempFile "enduro_pending" ".clj")
          pending-fos (FileOutputStream. pending)
          pending-writer (OutputStreamWriter. pending-fos)]
      (try
        (print-method value pending-writer)
        (.flush pending-writer)
        (-> pending-fos .getChannel (.force true))
        (-> pending-fos .getFD .sync)
        (.close pending-fos)
        ;; .delete might return false here because a swap! in another
        ;; thread has already deleted f.  It's OK though, because the
        ;; last .renameTo wins.
        (.delete f)
        (.renameTo pending f))))
  (-release-resource! [this]
    (core/swap! files-in-use disj (.getAbsolutePath f))))

(defprotocol IDurableAtom
  "A durable atom."
  (-swap! [a f args])
  (-reset! [a newval])
  (-release-atom! [a] "Release the underlying IDurableResource."))

(defn validate
  [f v]
  (if f (or (f v)
            (throw (IllegalStateException. "Invalid reference state")))))

(defn notify-watches
  [a watches v newv]
  (doseq [[k f] watches]
    (try
      (f k a v newv)
      (catch Exception e
        (throw (RuntimeException. e))))))

(deftype EnduroAtom [valid?
                     meta
                     validator
                     watches
                     ^alandipert.enduro.IDurableResource resource
                     ^AtomicReference state]
  clojure.lang.IMeta
  (meta [a] meta)
  clojure.lang.IRef
  (setValidator [a vf] (core/reset! validator vf))
  (getValidator [a] @validator)
  (getWatches [a] @watches)
  (addWatch [a k f] (do (core/swap! watches assoc k f) a))
  (removeWatch [a k] (do (core/swap! watches dissoc k) a))
  clojure.lang.IDeref
  (deref [a] (if @valid?
               (.get state)
               (throw (IllegalStateException. "Underlying resource has been released"))))
  IDurableAtom
  (-swap! [a f args]
    (if @valid?
      (loop []
        (let [v (.get state)
              newv (apply f v args)]
          (validate @validator newv)
          (if (locking resource
                (and (.compareAndSet state v newv)
                     (-commit! resource newv)))
            (do (notify-watches a @watches v newv) newv)
            (recur))))
      (throw (IllegalStateException. "Underlying resource has been released"))))
  (-reset! [a v]
    (-swap! a (constantly v) ()))
  (-release-atom! [a]
    (core/reset! valid? false)
    (-release-resource! resource)))

(defn swap!
  "Atomically swaps the value of enduro-atom to be:
   (apply f current-value-of-atom args) and commits the value to the underlying resource.

  Note that f may be called multiple times, and thus should be free of
  side effects.  Returns the value that was swapped in."
  [enduro-atom f & args]
  (-swap! enduro-atom f args))

(defn reset!
  "Sets the value of enduro-atom to newval without regard for the
  current value and commits newval to the underlying resource.
  Returns newval."
  [enduro-atom newval]
  (-reset! enduro-atom newval))

(defn release!
  [enduro-atom]
  "Releases the enduro-atom's underlying IDurableResource and invalidates further derefs."
  (-release-atom! enduro-atom))

(defn atom*
  [initial-state ^alandipert.enduro.IDurableResource resource opts]
  (doto (EnduroAtom. (core/atom true)
                     (:meta opts)
                     (core/atom (:validator opts))
                     (core/atom {})
                     resource
                     (AtomicReference. initial-state))
    (swap! identity)))

(defn read-file
  "Reads the first Clojure expression from f, returning nil if f is empty."
  [^File f]
  (with-open [pbr (PushbackReader. (io/reader f))]
    (read pbr false nil)))

(defn atom
  "Creates and returns a file-backed atom.  If file exists and
  is not empty, it is read and becomes the initial value.  Otherwise,
  the initial value is init and a new file is created and written to.

  file can be a string path to a file or a java.io.File object.

  If the file is already being used by another EnduroAtom, an
  exception is thrown.

  Takes zero or more additional options:

  :meta metadata-map.  Metadata is *not* persisted.

  :validator validate-fn.  Validator is *not* persisted.

  If metadata-map is supplied, it will be come the metadata on the
  atom. validate-fn must be nil or a side-effect-free fn of one
  argument, which will be passed the intended new state on any state
  change. If the new state is unacceptable, the validate-fn should
  return false or throw an exception."
  [init file & opts]
  (let [file (io/file file)
        path (.getAbsolutePath file)]
    (if (@files-in-use path)
      (throw (IllegalArgumentException. (str path " already in use.")))
      (core/swap! files-in-use conj path))
    (atom* (if (.exists file)
             (or (read-file file) init)
             init)
           (FileBackend. file)
           (apply hash-map opts))))
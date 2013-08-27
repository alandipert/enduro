(ns alandipert.enduro
  (:refer-clojure :exclude [swap! reset!])
  (:require [clojure.java.io :as io]
            [clojure.core :as core])
  (:import (java.io File FileOutputStream OutputStreamWriter PushbackReader)
           java.util.concurrent.atomic.AtomicReference))

(defprotocol IDurableBackend
  "Represents a durable resource."
  (-commit! [this value] "Attempt to commit value to resource, returning true if successful and false otherwise."))

(defprotocol IDurableAtom
  "A durable atom."
  (-swap! [a f args]))

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

(deftype EnduroAtom [meta
                     validator
                     watches
                     ^alandipert.enduro.IDurableBackend resource
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
  (deref [a] (.get state))
  IDurableAtom
  (-swap! [a f args]
    (loop []
      (let [v (.get state)
            newv (apply f v args)]
        (validate @validator newv)
        (if (locking resource
              (and (.compareAndSet state v newv)
                   (-commit! resource newv)))
          (do (notify-watches a @watches v newv) newv)
          (recur))))))

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
  (swap! enduro-atom (constantly newval)))

(defn atom*
  [initial-state ^alandipert.enduro.IDurableBackend resource opts]
  (doto (EnduroAtom. (:meta opts)
                     (core/atom (:validator opts))
                     (core/atom {})
                     resource
                     (AtomicReference. initial-state))
    (swap! identity)))

(defn with-options-doc [doc]
  (str doc "

  Takes zero or more additional options:

  :meta metadata-map.  Metadata is *not* persisted.

  :validator validate-fn.  Validator is *not* persisted.

  If metadata-map is supplied, it will become the metadata on the
  atom. validate-fn must be nil or a side-effect-free fn of one
  argument, which will be passed the intended new state on any state
  change. If the new state is unacceptable, the validate-fn should
  return false or throw an exception."))

;;; File-backed atom

(deftype FileBackend [^File f]
  IDurableBackend
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
        (.renameTo pending f)))))

(defn read-file
  "Reads the first Clojure expression from f, returning nil if f is empty."
  [^File f]
  (with-open [pbr (PushbackReader. (io/reader f))]
    (read pbr false nil)))

(defn file-atom
  #=(with-options-doc
      "Creates and returns a file-backed atom.  If file exists and
  is not empty, it is read and becomes the initial value.  Otherwise,
  the initial value is init and a new file is created and written to.

  file can be a string path to a file or a java.io.File object.")
  [init file & opts]
  (let [file (io/file file)
        path (.getAbsolutePath file)]
    (atom* (if (.exists file) (or (read-file file) init) init)
           (FileBackend. (doto file io/make-parents))
           (apply hash-map opts))))

;;; Memory-backed atom

(deftype MemoryBackend [atm]
  IDurableBackend
  (-commit! [this value]
    (core/reset! atm value)))

(defn mem-atom
  #=(with-options-doc "Creates and returns a memory-backed *non durable* atom for testing
  and development purposes with an initial value of init.")
  [init & opts]
  (atom* init
         (MemoryBackend. (core/atom init))
         (apply hash-map opts)))

# enduro

<iframe width="420" height="315" src="http://www.youtube.com/embed/zpHC8HQyKgE" frameborder="0" allowfullscreen></iframe>

Enduro provides a reference type similar to [Clojure's native
atom](http://clojure.org/atoms), except that enduro's is durable - its
contents are persisted to disk as Clojure data.

This is useful anywhere databases aren't feasible.  It works great on the
[Raspberry Pi](http://www.raspberrypi.org/)!

[![Build Status](https://secure.travis-ci.org/alandipert/enduro.png?branch=master)](http://travis-ci.org/alandipert/enduro)

## Usage

### Dependency

```clojure
[alandipert/enduro "1.0.2"]
```

### Example

```clojure
;; Require or use alandipert.enduro in your namespace.  The primary functions it
;; provides are atom, swap!, reset!, and release!.

(ns your-ns
  (:require [alandipert.enduro :as e])

;; Call e/atom with a value and a path to a file to create an
;; EnduroAtom.  If the file is empty or doesn't exist, it will be
;; initialized with value.  If the file isn't empty, your initial
;; value will be ignored and the file will be read.

(def addresses (e/atom {} "/tmp/addresses.clj"))

;; You can add watches to EnduroAtoms like any other reference type.

(add-watch addresses
           :new
           (fn [_ _ _ v]
             (println "new address" v)))

;; The swap! operation is synchronous, and returns only after the new value has
;; been committed to disk.

(e/swap! addresses assoc "Spongebob" "124 Conch Street")

@addresses ;=> {"Spongebob" "124 Conch Street"}
(slurp "/tmp/addresses.clj") ;=> {"Spongebob" "124 Conch Street"}

;; When you're done with addresses, and if you care, you can call
;; release! to make "/tmp/addresses.clj" available to a new
;; EnduroAtom.

(e/release! addresses)

```

## Notes

### Appropriate Usage

Because enduro must write the entire atom contents on every swap!, it
is very slow compared to a real database.  EnduroAtom contents must
also fit in memory.  Use with very small data only.

### In-transaction Data Recovery

Enduro uses temporary files as created by `File/createTempFile` to
store in-transaction data, and depends on the atomicity of
`File.renameTo` on your system.

If a transaction fails irrecoverably, the (likely corrupt)
in-transaction data can be found in your tmp directory with a name
like `enduro_pending5327124809540815880.clj`.

## License

Copyright © 2012 Alan Dipert

Distributed under the Eclipse Public License, the same as Clojure.

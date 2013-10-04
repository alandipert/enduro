# enduro

<a href="http://www.youtube.com/watch?v=zpHC8HQyKgE"><img src="https://dl.dropbox.com/u/12379861/enduro.jpg"></img></a>

enduro provides a reference type similar to [Clojure's native
atom](http://clojure.org/atoms), except that enduro's is durable - its
contents can be persisted as Clojure data to some durable backing
store.

Currently, enduro atoms can be backed by:

* files with `file-atom`: for memory-constrained applications where a database is not feasible, but where disk is available
* PostgreSQL with `postgresql-atom`: for small-data web applications, like those you may deploy to [Heroku](http://www.heroku.com/). For an example of a simple Heroku hit counter project using enduro, see [hitcounter](https://github.com/alandipert/enduro/tree/master/examples/hitcounter).
* memory with `mem-atom`: for local development and testing

A file-backed enduro atom works great on the [Raspberry
Pi](http://www.raspberrypi.org/)!

[![Build Status](https://secure.travis-ci.org/alandipert/enduro.png?branch=master)](http://travis-ci.org/alandipert/enduro)

## Usage

### Dependency

```clojure
[alandipert/enduro "1.1.4"]
```

### Example: File-backed

```clojure
;; Require or use alandipert.enduro in your namespace. The primary functions it
;; provides are atom, swap!, reset!, and release!.

(ns your-ns
  (:require [alandipert.enduro :as e])

;; Call e/file-atom with a value and a path to a file to create a
;; file-backed atom. If the file is empty or doesn't exist, it will be
;; initialized with value. If the file isn't empty, your initial value
;; will be ignored and the file will be read.

;; :pending-dir should be specified if the data file resides on a file
;; system other than the current one.  This ensures atomicity of the
;; underlying file move.

(def addresses (e/file-atom {} "/tmp/addresses.clj" :pending-dir "/tmp"))

;; You can add watches to enduro atoms like any other reference type.

(add-watch addresses
           :new
           (fn [_ _ _ v]
             (println "new address" v)))

;; The swap! operation is synchronous, and returns only after the new value has
;; been committed to disk.

(e/swap! addresses assoc "Spongebob" "124 Conch Street")

@addresses ;=> {"Spongebob" "124 Conch Street"}
(slurp "/tmp/addresses.clj") ;=> {"Spongebob" "124 Conch Street"}
```

### Example: PostgreSQL-backed

```clojure
(ns your-ns
  (:require [alandipert.enduro :as e]
            [alandipert.enduro.pgsql :as pg]
            [clojure.java.jdbc :as sql])

;; In this example, which is compatible with usage on Heroku, we first
;; define a function to return different connection information
;; depending on whether or not DATABASE_URL is defined. If it is, we
;; are likely on Heroku and the connection information is defined for
;; us. Otherwise, we'll connect to a local PostgreSQL database.

(defn db-config []
  (or (System/getenv "DATABASE_URL") "postgresql://localhost:5432/your-db"))

;; Call e/postgresql-atom with a value, a database connection string
;; or configuration map per clojure.java.jdbc, and the name of a table
;; to use to store the atom's value.

;; We wrap the atom in a delay to prevent a connection attempt during
;; ahead-of-time compilation, as enduro atoms attempt to commit their
;; initialization value as soon as they are created.

(def addresses
  (delay
   (pg/postgresql-atom
    {}
    (db-config)
    "enduro")))

;; Note: We assume for the remainder of this example that all calls
;; occur inside functions in order to prevent compile-time database
;; connection attempts.

;; In this call to `swap!` we're derefencing the delay, not the
;; atom.

(e/swap! @addresses assoc "Spongebob" "124 Conch Street")

;; To see the atom's value, it must be dereferenced twice to account
;; for the delay.

@@addresses ;=> {"Spongebob" "124 Conch Street"}

;; You can inspect the table enduro is using with clojure.java.jdbc.

(sql/with-connection (db-config)
  (sql/with-query-results rows
    [(str "SELECT value FROM enduro LIMIT 1")]
    (read-string (:value (first rows)))))

;=> {"Spongebob" "124 Conch Street"}
```

## Notes

### Appropriate Usage

Because enduro must write the entire atom contents on every `swap!`,
writes are very slow compared to a real database.  Enduro atoms,
despite being disk or database-backed, must fit in memory.  Use with
very small data only.

### In-transaction Data Recovery

File-backed enduro atoms use temporary files to store in-transaction
data and depend on the atomicity of `java.nio.Files/move`.

If a transaction fails exceptionally or irrecoverably, the (likely
corrupt) in-transaction data can be found in your :pending-dir
directory with a name like `enduro_pending5327124809540815880.clj`.

Currently, PostgreSQL-backed atoms do not persist failed transaction
data.

### Resource Management

Enduro makes no attempt to prevent you from allocating some underlying
resource, whether file or PostgreSQL table, to multiple atoms.

Enduro also provides no mechanism for deallocating resources, so you
cannot reliably use the same resource for different atoms in the same
program.

Be sure not to modify resources in use by enduro from either inside
your program or elsewhere while your program is running.

## License

Copyright © 2012 Alan Dipert

Distributed under the Eclipse Public License, the same as Clojure.

The photo
"[enduro_rois_3](http://www.flickr.com/photos/arufe/3338411518/)" in
this README is copyright © 2009 José Arufe and made available under a
[Creative
Commons](http://creativecommons.org/licenses/by-nc-sa/2.0/deed.en)
license.
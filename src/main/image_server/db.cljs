(ns image-server.db)

(def default-db {
                 :img-list []
                 :page-idx 0
                 })

(def db (atom {}))

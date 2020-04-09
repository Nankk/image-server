(ns image-server.db)

(def db (atom {:img-data-list []
               :selected-img-idx nil
               :page-idx nil}))

(ns image-server.db)

(def db (atom {:img-list []
               :selected-img-idx nil
               :page-idx nil}))

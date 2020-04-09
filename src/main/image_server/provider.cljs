(ns image-server.provider
  (:require
   ["fs" :as fs]
   ["stream" :as stream]
   [image-server.db :as db]
   [image-server.util :as util]
   [image-server.const :as const]
   ))

(defn handle-get-image [_ res]
  (try
    (let [selected (first (filter #(% :selected?) (@db/db :img-list)))
          path     (str "./img" (selected :id) "." (selected :ext))
          png      (. fs createReadStream path)
          ps       (. stream PassThrough)]
      (. stream pipeline png ps
         (fn [err] (when err (. js/console log err) (. res sendStatus 400))))
      (. ps pipe res))
    (catch js/Object e
      (. js/console log e)
      (. res sendStatus 500))))

(defn handle-get-thumbnail [req res]
  (try
    (let [query      (util/->query req)
          item-idx   (query :item-idx)
          page-idx   (@db/db :page-idx)
          target-idx (+ (* (* const/thumb-rows const/thumb-columns) page-idx) item-idx)
          target     (get (@db/db :img-list) target-idx)
          path       (str "./img/thumbs" (target :id) "." (target :ext))
          png        (. fs createReadStream path)
          ps         (. stream PassThrough)]
      (. stream pipeline png ps
         (fn [err] (when err (. js/console log err) (. res sendStatus 400)))))
    (catch js/Object e
      (. js/console log e)
      (. res sendStatus 500))))

(defn handle-get-image-list [_ res]
  (try
    (let [img-list (@db/db :img-list)]
      (. res json (clj->js {:img-list img-list})))
    (catch js/Object e
      (. js/console log e)
      (. res sendStatus 500))))

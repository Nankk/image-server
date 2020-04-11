(ns image-server.provider
  (:require
   ["fs" :as fs]
   ["stream" :as stream]
   [image-server.db :as db]
   [image-server.util :as util]
   [image-server.const :as const]
   ))

;; Get image (DONE)

(defn handle-get-image [_ res]
  (println "handle-get-image")
  (try
    (let [selected (first (filter #(% :selected?) (@db/db :img-list)))
          path     (if selected
                     (str "public/img/original/" (selected :id) "." (selected :ext))
                     "public/img/dummy.png")
          png      (. fs createReadStream path)
          ps       (. stream PassThrough)]
      (. stream pipeline png ps
         (fn [err] (when err (. js/console log err) (. res sendStatus 400))))
      (. ps pipe res))
    (catch js/Object e
      (. js/console log e)
      (. res sendStatus 500))))

;; Get thumbnail (DONE)

(defn handle-get-thumbnail [req res]
  (println "handle-get-thumbnail")
  (try
    (let [query      (util/->query req)
          item-idx   (js/parseInt (query :item-idx))
          _          (println "item-idx: " item-idx)
          page-idx   (@db/db :page-idx)
          target-idx (+ (* (* const/thumb-rows const/thumb-columns) page-idx) item-idx)
          _          (println "calculated target-idx" target-idx)
          target     (get (@db/db :img-list) target-idx)
          path       (if target
                       (str "public/img/thumb/" (target :id) "." (target :ext))
                       "public/img/dummy.png")
          png        (. fs createReadStream path)
          ps         (. stream PassThrough)]
      (. stream pipeline png ps
         (fn [err] (when err (. js/console log err) (. res sendStatus 400))))
      (. ps pipe res))
    (catch js/Object e
      (. js/console log e)
      (. res sendStatus 500))))

;; Get image list (DONE)

(defn handle-get-image-list [_ res]
  (println "handle-get-image-list")
  (try
    (let [img-list (@db/db :img-list)]
      (. res json (clj->js {:img-list img-list})))
    (catch js/Object e
      (. js/console log e)
      (. res sendStatus 500))))

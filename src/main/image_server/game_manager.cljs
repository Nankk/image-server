(ns image-server.game-manager
  (:require
   ["stream" :as stream]
   ["fs" :as fs]
   ["uuid" :as uuid]
   [image-server.util :as util]
   [image-server.db :as db]
   [image-server.const :as const]))

;; Select (DONE)

(defn- select-img [idx]
  (println "select-img [" idx "]")
  (swap! db/db #(update % :img-list (fn [items] (vec (map (fn [item] (assoc item :selected? false)) items)))))
  (when idx
    (println "img #" idx " selected")
    (swap! db/db #(assoc-in % [:img-list idx :selected?] true))))

(defn handle-select [req res]
  (println "handle-select")
  (try
    (let [query     (util/->query req)
          item-idx  (js/parseInt (query :item-idx))
          idx       (if-let [img-idx (query :img-idx)]
                      (js/parseInt img-idx)
                      (+ item-idx (* (@db/db :page-idx) (* const/thumb-columns const/thumb-rows))))
          _         (select-img (if (< idx (count (@db/db :img-list))) idx nil))
          identicon (util/identicon {:seed (query :identicon-seed)
                                     :size (query :size)})
          tmp-path  (str "public/img/identicons/" (uuid/v4) ".png")
          _         (. fs writeFileSync tmp-path identicon)
          png       (. fs createReadStream tmp-path)
          ps        (. stream PassThrough)]
      (. stream pipeline png ps
         (fn [err] (when err (. js/console log err) (. res sendStatus 400))))
      (. ps pipe res)
      (. fs unlinkSync tmp-path) ; Delete temporary identicon after returning
      )
    (catch js/Object e
      (. js/console log e)
      (. res sendStatus 500))))

;; Change page (DONE)

(defn- change-page [diff]
  (println "change-page [" diff "]")
  (swap! db/db #(update % :page-idx (fn [x] (+ x diff)))))

(defn handle-change-page [req res]
  (println "handle-change-page")
  (try
    (let [query     (util/->query req)
          _         (case (query :type)
                      "increment" (change-page 1)
                      "decrement" (change-page -1))
          identicon (util/identicon {:seed (query :identicon-seed)
                                     :size (query :size)})
          tmp-path  (str "public/img/identicons/" (uuid/v4) ".png")
          _         (. fs writeFileSync tmp-path identicon)
          png       (. fs createReadStream tmp-path)
          ps        (. stream PassThrough)]
      (. stream pipeline png ps
         (fn [err] (when err (. js/console log err) (. res sendStatus 400))))
      (. ps pipe res)
      (. fs unlinkSync tmp-path) ; Delete temporary identicon after returning
      )
    (catch js/Object e
      (. js/console log e)
      (. res sendStatus 500))))

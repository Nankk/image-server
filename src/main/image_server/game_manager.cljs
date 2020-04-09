(ns image-server.game-manager
  (:require
   ["stream" :as stream]
   [image-server.util :as util]
   [image-server.db :as db]
   ))

(defn- select-img [id]
  (swap! @db/db #(map (fn [item] (assoc item :selected? (= (item :id) id))) (% :img-list))))

(defn handle-select [req res]
  (try
    (let [query     (util/->query req)
          id        (query :id)
          _         (select-img id)
          identicon (util/identicon {:seed (query :identicon-seed)
                                     :size (query :size)})
          ps        (. stream PassThrough)]
      (. stream pipeline identicon ps
         (fn [err] (when err (. js/console log err) (. res sendStatus 400)))))
    (catch js/Object e
      (. js/console log e)
      (. res sendStatus 500))))

;;

(defn- change-page [diff]
  (swap! @db/db #(update % :selected-page-idx (fn [x] (+ x diff)))))

(defn handle-change-page [req res]
  (try
    (let [query     (util/->query req)
          _         (case (query :type)
                      "increment" (change-page 1)
                      "decrement" (change-page -1))
          identicon (util/identicon {:seed (query :identicon-seed)
                                     :size (query :size)})
          ps        (. stream PassThrough)]
      (. stream pipeline identicon ps
         (fn [err] (when err (. js/console log err) (. res sendStatus 400)))))
    (catch js/Object e
      (. js/console log e)
      (. res sendStatus 500))))

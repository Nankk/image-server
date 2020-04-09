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

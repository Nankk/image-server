(ns image-server.receiver
  (:require
   ["fs" :as fs]
   ["uuid" :as uuid]
   ["jimp" :as jimp]
   [image-server.db :as db]
   [async-interop.interop :refer-macros [<p!]]
   [cljs.core.async :as async :refer [>! <! go chan timeout go-loop]]
   [clojure.string :as str]
   [clojure.set :as set]))

;; :upload-image (DONE)

(defn- ->buffer [data-uri]
  (let [b64 (str/replace data-uri #"^data:[^;]+;base64," "")]
    (js/Buffer. b64 "base64")))

(defn- resized-image
  "Resizes the img to fit in the wmax-hmax rectangle keeping the aspect ratio.
  Returns a channel containing js buffer object"
  [img wmax hmax mime]
  (println "resized-image")
  (let [ch (chan)]
    (go (let [j-img   (<p! (. jimp read img))
              resized (. j-img scaleToFit wmax hmax)
              buf     (<p! (. resized getBufferAsync mime))]
          (>! ch buf)))
    ch))

(defn- add-image-data [uuid name ext]
  (swap! db/db #(update % :img-list (fn [x] (conj x {:id uuid :name name :ext ext})))))

(defn handle-upload-image
  "Handles 'upload-image' request from clients."
  [req res]
  (println "handle-upload-image")
  (go
    (try
      (let [{:strs [name data-uri]} (js->clj (. req -body)) ; assuming edn string
            uuid                    (uuid/v4)
            ext                     (subs data-uri (inc (str/index-of data-uri "/")) (str/index-of data-uri ";"))
            img-buf                 (->buffer data-uri)
            _                       (. fs writeFileSync (str "public/img/original/" uuid "." ext) img-buf)
            thumb                   (<! (resized-image img-buf 200 100 (str "image/" ext)))
            _                       (. fs writeFileSync (str "public/img/thumb/" uuid "." ext) thumb)
            _                       (add-image-data uuid name ext)]
        (. res sendStatus 200))
      (catch js/Object e
        (. js/console log e)
        (. res sendStatus 500)))))

;; :update-image-list (DONE)

(defn- remove-unlinked-files [fnames]
  (doseq [fname fnames]
    (. fs unlinkSync (str "public/img/original/" fname))
    (. fs unlinkSync (str "public/img/thumb/" fname))
    (println "Deleted " fname)))

(defn handle-upload-image-list [req res]
  (println "handle-upload-image-list")
  (try
    (let [img-list-raw (js->clj (. req -body)) ; assuming edn string
          ids-kept     (vec (map #(get % "id") img-list-raw))
          cur-list     (@db/db :img-list)
          new-list     (loop [v   []
                              ids ids-kept]
                         (if (not-empty ids)
                           (let [id   (first ids)
                                 elem (first (filter #(= (% :id) id) cur-list))]
                             (recur (if elem (conj v elem) v) (rest ids)))
                           v))
          fname-fn     (fn [item] (str (item :id) "." (item :ext)))
          redundants   (set/difference (set (map fname-fn cur-list)) (set (map fname-fn new-list)))]
      (swap! db/db #(assoc % :img-list new-list))
      (remove-unlinked-files redundants)
      (println "img-list updated:")
      (. res sendStatus 200))
    (catch js/Object e
      (. js/console log e)
      (. res sendStatus 500))))

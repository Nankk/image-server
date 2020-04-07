(ns image-server.receiver
  (:require
   ["fs" :as fs]
   ["uuid" :as uuidv4]
   ["jimp" :as jimp]
   [image-server.db :as db]
   [cljs.core.async :as async :refer [>! <! go chan timeout go-loop]]
   [cljs.reader :refer [read-string]]
   [clojure.string :as str]))

(defn- ->buffer [data-uri]
  (let [b64 (str/replace data-uri #"^data:[^;]+;base64," "")]
    (js/Buffer. b64 "base64")))

(defn- resized-image
  "Resizes the img to fit in the wmax-hmax rectangle keeping the aspect ratio.
  Returns a js buffer object"
  [img wmax hmax]
  (let [j-img   (<! (. jimp read img))
        resized (. j-img scaleToFit wmax hmax)]
    (. resized getBuffer)))

(defn- add-image-data [uuid name]
  (let [db @db/db]
    (swap! db conj {:id uuid :name name})))

(defn handle-upload-image
  "Handles 'upload-image' request from clients."
  [req res]
  (try
    (let [{:keys [name data-uri]} (read-string (. req -body)) ; assuming edn string
          uuid                    (uuidv4)
          ext                     (subs data-uri (inc (str/index-of data-uri "/")) (str/index-of data-uri ";"))
          img-buf                 (->buffer data-uri)
          _                       (. fs writeFile (str "img/" uuid "." ext) img-buf)
          thumb                   (resized-image img-buf 300 300)
          _                       (. fs writeFile (str "img/thumb" uuid "." ext) thumb)
          _                       (add-image-data uuid name)]
      (. res sendStatus 200))
    (catch js/Object e
      (. res sendStatus 500))))

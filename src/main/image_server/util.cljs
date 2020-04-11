(ns image-server.util
  (:require
   ["fs" :as fs]
   ["jdenticon" :as jd]
   ["stream" :as stream]
   ["uuid" :as uuid]
   [clojure.string :as str]
   [cljs.pprint :refer [pprint]]
   [cljs.core.async :as async :refer [>! <! go chan timeout go-loop]]
   [async-interop.interop :refer-macros [<p!]]
   ))

(defn identicon
  "Creates random identicon using 20-letters-truncated uuid_v4
  and returns as 128x128 png buffer.
  You can optionally specify identicon seed and size in options map:
  :seed as string, and :size as integer."
  [options]
  (. jd toPng (or (options :seed) (uuid/v4)) (or (options :size) 128)))

(defn pretty-string [data]
  (with-out-str (pprint data)))

(defn return-success [res msg]
  (. res send (str "<pre>" msg "</pre>")))

(defn return-png [res url]
  (println "return-png")
  (go (try
        (let [png (. fs createReadStream url)
              ps  (. stream PassThrough)]
          (. stream pipeline png ps
             (fn [err] (when err (. js/console log err) (. res sendStatus 400))))
          (. ps pipe res))
        (catch js/Object e
          (. js/console log e)
          (. res sendStatus 500)))))

(defn ->query
  "Extracts query map from the request.
  Keys of the extracted map are converted to kebab-case keyword."
  [req]
  (let [q (js->clj (. req -query))
        v (into [] q)]
    (into {} (map #(update % 0 (fn [k] (keyword (str/replace k "_" "-")))) v))))

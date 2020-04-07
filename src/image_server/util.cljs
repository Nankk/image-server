(ns image-server.util
  (:require
   ["fs" :as fs]
   [clojure.string :as str]
   [cljs.pprint :refer [pprint]]
   [cljs.core.async :as async :refer [>! <! go chan timeout go-loop]]
   [async-interop.interop :refer-macros [<p!]]
   ["stream" :as stream]
   ))

(defn create-identicon []
  ;; Maybe use Jdenticon
  (let [uuid ()
        ]))

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
          (do (. js/console log e)
              (. res sendStatus 500))))))

(defn insanitize
  "Replaces '_' with '-'."
  [s]
  (str/replace s #"_" "-"))

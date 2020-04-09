(ns image-server.core
  (:require
   ["express" :as express]
   ["stream" :as stream]
   ["fs" :as fs]
   ["uuid" :as uuidv4]
   ["process" :as process]
   [clojure.string :as str]
   [cljs.core.async :as async :refer [>! <! go chan timeout go-loop]]
   [async-interop.interop :refer-macros [<p!]]
   [image-server.util :as util]
   [image-server.db :as db]
   [image-server.provider :as pro]
   [image-server.receiver :as rcv]
   [image-server.game-manager :as mng]))

;; Handlers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- handle-init [req res]
  (println "handle-init")
  (go (try
        (do
          (reset! @db/db {})
          (util/return-success res "db initialized."))
        (catch js/Object e
          (. res sendStatus 500)))))

;; Main ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce server (atom nil))

(defonce reload-counter
  (let [counter (atom 0)]
    (fn []
      (swap! counter inc)
      @counter)))

(defn ^:dev/after-load start-server []
  (let [app (express.)]

    ;; Static
    (. app use (. express static "public/img"))

    ;; Global
    (. app get "/init" handle-init)
    (. app post "/upload-image" rcv/handle-upload-image)

    ;; Reloading indicator
    (when (some? @server)
      (. @server close)
      (println "Server reloaded"))
    (println (str "### You have re-loaded the server " (reload-counter) " times. ###"))

    ;; Listen process.env.PORT or fixed port 55555
    (let [env-port (.. js/process -env -PORT)
          port (if env-port env-port 55555)]
      (reset! server (. app listen port,
                        #(. js/console log
                            (str "Listening on port " port)))))))

(defn main []
  ;; To catch the uncathcable exception thrown to main loop of Express
  (. process on "uncaughtException"
     (fn [err origin]
       (println "###########################################")
       (println "Caught exception: " err)
       (println "Exception origin: " origin)
       (println "###########################################")))

  ;; Starting server
  (start-server))

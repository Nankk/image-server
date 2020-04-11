(ns image-server.core
  (:require
   ["express" :as express]
   ["process" :as process]
   ["cors" :as cors]
   ["body-parser" :as bp]
   [cljs.core.async :as async :refer [>! <! go chan timeout go-loop]]
   [image-server.util :as util]
   [image-server.db :as db]
   [image-server.provider :as prv]
   [image-server.receiver :as rcv]
   [image-server.game-manager :as gm]))

;; Handlers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- handle-init [_ res]
  (println "handle-init")
  (go (try
        (reset! @db/db {})
        (util/return-success res "db initialized.")
        (catch js/Object e
          (. js/console log e)
          (. res sendStatus 500)))))

;; Main ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce server (atom nil))

(defonce reload-counter
  (let [counter (atom 0)]
    (fn []
      (swap! counter inc)
      @counter)))

(defn- init [app]
  ;; CORS settings
  (. app use (cors))

  ;; body-parser
  (. app use (. bp urlencoded (clj->js {:limit "20mb" :extended true})))
  (. app use (. bp json (clj->js {:limit "20mb" :extended true})))

  ;; Static files
  (. app use (. express static "public/img")))

(defn- set-handlers [app]
  ;; Global
  (. app get "/init" handle-init)

  ;; Receiver
  (. app post "/upload-image" rcv/handle-upload-image) ; DONE
  (. app post "/upload-image-list" rcv/handle-upload-image) ; TODO

  ;; Provider
  (. app get "/get-image" prv/handle-get-image)
  (. app get "/get-thumbnail" prv/handle-get-thumbnail)
  (. app get "/get-image-list" prv/handle-get-image-list)

  ;; Game Manager
  (. app get "/select" gm/handle-select)
  (. app get "/change-page" gm/handle-change-page))

(defn- display-reload-times []
  ;; Reloading indicator
  (when (some? @server)
    (. @server close)
    (println "Server reloaded"))
  (println (str "### You have re-loaded the server " (reload-counter) " times. ###")))

(defn- start-listening [app]
  ;; Listen process.env.PORT or fixed port 55555
  (let [env-port (.. js/process -env -PORT)
        port (if env-port env-port 55555)]
    (reset! server (. app listen port,
                      #(. js/console log
                          (str "Listening on port " port)))))
  )

(defn ^:dev/after-load start-server []
  (let [app (express.)]
    (init app)
    (set-handlers app)
    (display-reload-times)
    (start-listening app)))

(defn main []

  ;; Initialize state
  (reset! db/db db/default-db)

  ;; To catch the uncathcable exception thrown to main loop of Express
  (. process on "uncaughtException"
     (fn [err origin]
       (println "###########################################")
       (println "Caught exception: " err)
       (println "Exception origin: " origin)
       (println "###########################################")))

  ;; Starting server
  (start-server))

(ns image-server.core
  (:require
   ["fs" :as fs]
   ["express" :as express]
   ["process" :as process]
   ["cors" :as cors]
   ["body-parser" :as bp]
   [cljs.core.async :as async :refer [>! <! go chan timeout go-loop]]
   [image-server.util :as util]
   [image-server.db :as db]
   [image-server.provider :as prv]
   [image-server.receiver :as rcv]
   [image-server.game-manager :as gm]
   [image-server.const :as const]
   [clojure.string :as str]))

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

(defn- config-server [app]
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
  (. app post "/upload-image-list" rcv/handle-upload-image-list) ; TODO

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
  (let [port const/port]
    (reset! server (. app listen port,
                      #(. js/console log
                          (str "Listening on port " port))))))

(defn ^:dev/after-load start-server []
  (let [app (express.)]
    (config-server app)
    (set-handlers app)
    (display-reload-times)
    (start-listening app)))

(defn- delete-dir-contents [dir-path]
  (println "delete-dir-contents [" dir-path "]")
  (let [path (str/replace dir-path #"/$" "")
        files (js->clj (. fs readdirSync dir-path))]
    (doseq [file files]
      (. fs unlinkSync (str path "/" file))
      (println file "was deleted."))))

(defn- init []
  ;; Initialize state
  (reset! db/db db/default-db)

  ;; Delete remaining files of previous session
  (delete-dir-contents "public/img/identicons")
  (delete-dir-contents "public/img/original")
  (delete-dir-contents "public/img/thumb"))

(defn main []

  (init)

  ;; To catch the uncathcable exception thrown to main loop of Express
  (. process on "uncaughtException"
     (fn [err origin]
       (println "###########################################")
       (println "Caught exception: " err)
       (println "Exception origin: " origin)
       (println "###########################################")))

  ;; Starting server
  (start-server))

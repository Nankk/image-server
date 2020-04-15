(ns image-server.core
  (:require
   ["fs" :as fs]
   ["express" :as express]
   ["https" :as https]
   ["process" :as process]
   ["cors" :as cors]
   ["body-parser" :as bp]
   [cljs.core.async :as async :refer [>! <! go chan timeout go-loop]]
   [image-server.util :as util]
   [image-server.db :as db]
   [image-server.provider :as prv]
   [image-server.receiver :as rcv]
   [image-server.game-manager :as gm]
   [image-server.const :as const]))

(def debug?
  ^boolean goog.DEBUG)

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
  (println (str "### Server was reloaded " (reload-counter) " times. ###")))

(defn- start-listening [app options]
  (let [s-serv (if options
                 (. https createServer options app)
                 app)
        port   const/port]
    (reset! server (. s-serv listen port
                      #(. js/console log
                          (str "Listening on port " port))))))

(defn ^:dev/after-load start-server []
  (let [app     (express.)
        options (if debug?
                  nil
                  (clj->js {:key  (. fs readFileSync const/privkey)
                            :cert (. fs readFileSync const/cert)
                            :ca   (. fs readFileSync const/ca)}))]
    (config-server app)
    (set-handlers app)
    (display-reload-times)
    (start-listening app options)))

(defonce renewed-map (atom {:privkey false
                            :cert    false
                            :ca      false}))

(defonce ch (chan))

(defn- watch-certs []
  (go-loop []
    (when-some [file-type (<! ch)]
      (swap! renewed-map #(assoc % file-type true))
      (println file-type " became true")
      (when (every? true? (vals @renewed-map))
        (try
          (println "All certification files were renewed.")
          (start-server)
          (println "Server")
          (catch js/Error e
            (println e))
          (finally
            (reset! renewed-map {:privkey false
                                 :cert    false
                                 :ca      false})))))
    (recur)))

(defn- register-file-watch []
  ;; (fs.watch() seems reasonable for watching but may not detect the file replacing)
  (. fs watchFile const/privkey(clj->js {:interval 5000})
     (fn [_ _]
       (println const/privkey " changed")
       (go (>! ch :privkey))))
  (. fs watchFile const/cert (clj->js {:interval 5000})
     (fn [_ _]
       (println const/cert " changed")
       (go (>! ch :cert))))
  (. fs watchFile const/ca "./c.txt" (clj->js {:interval 5000})
     (fn [_ _]
       (println const/ca " changed")
       (go (>! ch :ca)))))

(defn main []

  ;; To catch the uncathcable exception thrown to main loop of Express
  (. process on "uncaughtException"
     (fn [err origin]
       (println "###########################################")
       (println "Caught exception: " err)
       (println "Exception origin: " origin)
       (println "###########################################")))

  ;; Starting server
  (start-server)

  ;; Watching certification renewal
  (when (not debug?)
    (watch-certs)
    (register-file-watch)))

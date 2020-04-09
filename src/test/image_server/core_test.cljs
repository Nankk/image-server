(ns image-server.core-test
  (:require
   [cljs.test :as t :include-macros true]
   [cljs-http.client :as http]
   [cljs.core.async :as async :refer [>! <! go chan timeout go-loop]]
   [image-server.core :as sut]
   ))

(t/deftest test-providers
  (t/testing "get-image-list"
    (let [res (js->clj (<! (http/get "http://localhost:55555/get-image-list")))]
      (println res)
      (t/is false
            ;; (contains? res :img-list)
            ))
    ))

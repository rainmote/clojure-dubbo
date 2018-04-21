(ns clojure-dubbo.dubbo-test
  (:require [clojure-dubbo.core :as core]
            [clojure-dubbo.protocol :as protocol]
            [manifold.stream :as s]))

(defn run []
  (let [c (core/invoke "127.0.0.1" 30889 {})
        data (protocol/construct-request :content {})]
    @(s/put! c data)
    (println @(s/take! c))))


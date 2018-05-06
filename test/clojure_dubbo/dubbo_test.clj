(ns clojure-dubbo.dubbo-test
  (:require [clojure-dubbo.core :as core]
            [clojure-dubbo.protocol :as protocol]
            [taoensso.tufte :as tufte :refer (defnp p profiled profile)]
            [manifold.stream :as s]))

(defn sub-map [m s]
  (zipmap s (map #(get m %) s)))

(defn request []
  @(core/invoke "127.0.0.1" 30889 {:parameter "HelloWorld!123123123123"}))

(defn run []
  (tufte/add-basic-println-handler! {})
  (profile
    {}
    (dotimes [_ 200]
      (p :request (request)))))


(ns clojure-dubbo.core
  (:require [aleph.tcp :as tcp]
            [gloss.io :as io]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [clojure-dubbo.protocol :as dubbo-pr]))

(def ^:dynamic *connect-atom* (atom {}))

(defn wrap-duplex-stream
  [protocol stream]
  (let [out (s/stream)]
    (s/connect (s/map #(io/encode protocol %) out)
               stream)
    (s/splice out
              (io/decode-stream stream protocol))))

(defn client
  [host port]
  (d/chain (tcp/client {:host host :port port})
           #(wrap-duplex-stream dubbo-pr/protocol %)))

(defn connect-if-needed [host port]
  (when-not (get-in @*connect-atom* [host port])
    (swap! *connect-atom*
           assoc-in [host port] @(client host port))))

(defn invoke
  [host port opts]
  (let [{:keys [interface method parameter-type parameter]} opts
        content {:service-name interface
                 :method method
                 :parameter-type parameter-type
                 :parameter parameter}
        frame (dubbo-pr/construct-request :content content)]
    (connect-if-needed host port)
    (get-in @*connect-atom* [host port])))
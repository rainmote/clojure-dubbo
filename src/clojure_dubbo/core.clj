(ns clojure-dubbo.core
  (:require [aleph.tcp :as tcp]
            [gloss.io :as io]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [clojure-dubbo.protocol :as dubbo-pr])
  (:import [io.netty.bootstrap Bootstrap]
           [io.netty.channel ChannelOption]
           [io.netty.buffer PooledByteBufAllocator]))

;; 存放tcp链接
(def ^:dynamic *connect-atom* (atom {}))

;; 存放rpc-id到response映射
(def ^:dynamic *rpc-id-to-resp* (atom {}))

(defn on-dubbo-response [msg]
  (when (= (some-> msg :flags :type) false)                 ; 0 mean is response
    (let [rpc-id (some-> msg :rpc-id)
          result (get @*rpc-id-to-resp* rpc-id)]
      (when (and rpc-id result)
        (swap! *rpc-id-to-resp* dissoc rpc-id)
        (if (= (some-> msg :status) (:OK dubbo-pr/status-map))
          (d/success! result msg)
          (d/error! result msg))))))

(defn wrap-duplex-stream
  [protocol stream]
  (let [out (s/stream)
        ss (io/decode-stream stream protocol)]
    ;; construct request stream
    (s/connect (s/map #(io/encode protocol %) out)
               stream)
    ;; add response process
    (s/consume on-dubbo-response ss)
    ;; construct response stream
    (s/splice out ss)))

(defn client
  [host port]
  (d/chain (tcp/client {:host host
                        :port port
                        :bootstrap-transform (fn [x]
                                               (doto x
                                                 (.option ChannelOption/TCP_NODELAY true)
                                                 (.option ChannelOption/ALLOCATOR PooledByteBufAllocator/DEFAULT)))
                        ;:pipeline-transform
                        :raw-stream? true                   ;; minimize copying
                        :epoll? false})
           #(wrap-duplex-stream dubbo-pr/protocol %)))

(defn get-connect [host port]
  (get-in @*connect-atom* [host port :connect]))

(defn connect-if-needed [host port]
  (when-not (get-connect host port)
    (let [c (client host port)]
      (swap! *connect-atom*
             assoc-in [host port :connect] c))))

;; reference: http://aleph.io/manifold/streams.html
(defn invoke
  [host port opts]
  (let [{:keys [interface method parameter-type parameter timeout]
         :or {timeout 1000}} opts
        content {:service-name interface
                 :method method
                 :parameter-type parameter-type
                 :parameter parameter}
        frame (dubbo-pr/construct-request :content content)
        frame-content (:content frame)]
    (connect-if-needed host port)
    (let [result (d/deferred)]
      ;;(println frame)
      (swap! *rpc-id-to-resp*
             assoc (:rpc-id frame) result)
      (d/let-flow [c (get-connect host port)]
                  (s/try-put! c frame timeout ::timeout))
      ;; wait 100 ms
      (d/timeout! result 100 nil))))
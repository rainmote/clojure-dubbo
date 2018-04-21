(ns clojure-dubbo.protocol
  (:require [gloss.core :refer :all]
            [gloss.io :refer :all])
  (:import [com.alibaba.fastjson JSON]))

(def dubbo-magic 0xdabb)

(def seri-map {:fastjson 6})

;; dubbo response code
(def status-map {:OK 20
                 :CLIENT_TIMEOUT 30
                 :SERVER_TIMEOUT 31
                 :BAD_REQUEST 40
                 :BAD_RESPONSE 50
                 :SERVICE_NOT_FOUND 60
                 :SERVICE_ERROR 70
                 :SERVER_ERROR 80
                 :CLIENT_ERROR 90
                 :SERVER_THREADPOOL_EXHAUSTED_ERROR 100})

;; internal counter for rpc request
(def ^:dynamic *rpc-request-id* (atom 0))
(defn get-request-id [] (swap! *rpc-request-id* inc))

;;;; define dubbo protocol
(def pr-flags (bit-map :type 1
                       :two-way 1
                       :event 1
                       :seri-id 5))

(def pr-content (finite-frame :uint32
                              (repeated (string :utf-8 :delimiters "\n")
                                        :prefix :none)))

(defn protocol-encode [data]
  (let [f (fn [m] (map #(JSON/toJSONString %) m))]
    (update data :content f)))

(defn protocol-decode [data]
  (let [f (fn [m] (map #(JSON/parseObject % java.lang.String) m))]
    (update data :content f)))

(def protocol
  (compile-frame
    (ordered-map :magic :uint16
                 :flags pr-flags
                 :status :byte
                 :rpc-id :uint64
                 :content pr-content)
    protocol-encode
    protocol-decode))

;;;; define construct frame
(defn construct-request
  [& {:keys [magic flags content]
      :or {magic dubbo-magic
           flags {:type 1                                   ;; 1 is request
                  :two-way 1                                ;; need return value
                  :event 0
                  :seri-id (:fastjson seri-map)}}}]
  (let [default-service-name "com.alibaba.dubbo.performance.demo.provider.IHelloService"
        default-content {:dubbo-version "2.6.0"
                         :service-name default-service-name
                         :service-version "0.0.0"
                         :method "hash"
                         :parameter-type "Ljava/lang/String;"
                         :parameter "HelloWorld"
                         :attachments {"path" default-service-name}}
        _content (merge-with (fn [v1 v2]
                               (when-not v2
                                 v1))
                             default-content
                             content)]
    (println _content)
    {:magic magic
     :flags flags
     :status (:OK status-map)
     :rpc-id (get-request-id)
     :content [(:dubbo-version _content)
               (:service-name _content)
               (:service-version _content)
               (:method _content)
               (:parameter-type _content)
               (:parameter _content)
               (:attachments _content)]}))



(ns flow-storm.remote-websocket-client
  (:refer-clojure :exclude [send])
  (:require [flow-storm.json-serializer :as serializer]
            [flow-storm.utils :refer [log log-error] :as utils])
  (:import [org.java_websocket.client WebSocketClient]
           [java.net URI]
           [org.java_websocket.handshake ServerHandshake]))

(def remote-websocket-client nil)

(defn stop-remote-websocket-client []
  (when remote-websocket-client
    (.close remote-websocket-client))
  (alter-var-root #'remote-websocket-client (constantly nil)))

(defn remote-connected? []
  (boolean remote-websocket-client))

(defn start-remote-websocket-client [{:keys [host port on-connected run-command]
                                      :or {host "localhost"
                                           port 7722}}]
  (let [uri-str (format "ws://%s:%s/ws" host port)
        ^WebSocketClient ws-client (proxy
                                       [WebSocketClient]
                                       [(URI. uri-str)]

                                     (onOpen [^ServerHandshake handshake-data]
                                       (log (format "Connection opened to %s" uri-str))
                                       (when on-connected (on-connected)))

                                     (onMessage [^String message]
                                       ;; this is sketchy since it assumes the only messages
                                       ;; we receive are commands
                                       (let [[comm-id method args-map] (serializer/deserialize message)
                                             ret-packet (run-command comm-id method args-map)
                                             ret-packet-ser (serializer/serialize ret-packet)]
                                         (.send remote-websocket-client ret-packet-ser)))

                                     (onClose [code reason remote?]
                                       (log (format "Connection with %s closed. code=%s reson=%s remote?=%s"
                                                    uri-str code reason remote?)))

                                     (onError [^Exception e]
                                       (log-error (format "WebSocket error connection %s" uri-str) e)))]
    (.setConnectionLostTimeout ws-client 0)
    (.connect ws-client)

    (alter-var-root #'remote-websocket-client (constantly ws-client))
    ws-client))

(defn send [ser-packet]
  ;; websocket library isn't clear about thread safty of send
  ;; lets synchronize just in case
  (locking remote-websocket-client
    (.send ^WebSocketClient remote-websocket-client ^String ser-packet)))

(defn send-event-to-debugger [ev-packet]
  (let [ser-packet (serializer/serialize ev-packet)]
    (send ser-packet)))

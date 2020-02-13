(ns atomist.main
  (:require [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<! >! timeout chan]]
            [goog.string.format]
            [atomist.cljs-log :as log]
            [atomist.api :as api]
            [atomist.cljfmt :as cljfmt]
            ["@atomist/automation-client" :as ac]
            [atomist.deps :as deps])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn run-cljfmt
  [request project]
  (go
   (try
     (cljfmt/cljfmt (. ^js project -baseDir))
     (catch :default ex
       (log/error "unable to run cljfmt")
       (log/error ex)
       {:error ex
        :message "unable to run cljfmt"}))))

(defn- handle-push-event [request]
  ((-> (api/finished :message "handling Push" :success "successfully handled Push event")
       (api/run-sdm-project-callback run-cljfmt)
       (api/extract-github-token)
       (api/create-ref-from-push-event)) request))

(defn ^:export handler
  "handler
    must return a Promise - we don't do anything with the value
    params
      data - Incoming Request #js object
      sendreponse - callback ([obj]) puts an outgoing message on the response topic"
  [data sendreponse]
  (api/make-request
   data
   sendreponse
   handle-push-event))

(ns atomist.main
  (:require [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<! >! timeout chan]]
            [goog.string.format]
            [atomist.cljs-log :as log]
            [atomist.api :as api]
            [atomist.cljfmt :as cljfmt]
            [atomist.sdmprojectmodel :as sdm]
            ["@atomist/automation-client" :as ac]
            [atomist.deps :as deps]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn run-cljfmt
  [project]
  (go
    (try
     ;; weird that cljfmt is synchronous
      (cljfmt/cljfmt (. ^js project -baseDir))
      :done
      (catch :default ex
        (log/error "unable to run cljfmt")
        (log/error ex)
        {:error ex
         :message "unable to run cljfmt"}))))

(defn compose-wrapper
  [request project]
  (go
    (<! (((:wrapper request) run-cljfmt) project))))

(defn is-default-branch?
  [request]
  (let [push (-> request :data :Push first)]
    (= (:branch push) (-> push :repo :defaultBranch))))

(defn- check-configuration [handler]
  (fn [request]
    (let [title "Format code in line with current guidelines"
          body (str "Configuration that triggered this change:\n" (:configuration request))]
      (cond (= "inPR" (:policy request))
            (handler (assoc request :wrapper (fn [cb] (sdm/edit-inside-PR cb {:branch (gstring/format "cljfmt-%s" (-> request :ref :branch))
                                                                              :target-branch (-> request :ref :branch)
                                                                              :body body
                                                                              :title title}))))
            (or (= "onBranch" (:policy request))
                (and (= "onDefaultBranch" (:policy request))
                     (is-default-branch? request)))
            (handler (assoc request :wrapper (fn [cb] (sdm/commit-then-push cb title))))

            (and
             (= "onDefaultBranch" (:policy request))
             (not (is-default-branch? request)))
            (api/finish request :success "Not formatting as this is not the default branch" :visibility :hidden)

            :else
            (api/finish request :failure "skill requires either 'update directly' or 'update in PR' to be configured")))))

(defn- handle-push-event [request]
  ((-> (api/finished :message "handling Push"
                     :send-status (fn [request]
                                    (cond (= :raised (-> request :results))
                                          (gstring/format "**cljfmt skill** raised a PR")
                                          :else
                                          "handled Push successfully")))
       (api/run-sdm-project-callback compose-wrapper)
       (check-configuration)
       (api/add-skill-config :policy :default-branch)
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

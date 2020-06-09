(ns atomist.main
  (:require [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<! >! timeout chan]]
            [goog.string.format]
            [atomist.cljs-log :as log]
            [atomist.api :as api]
            [atomist.cljfmt :as cljfmt]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- is-default-branch?
  [request]
  (let [push (-> request :data :Push first)]
    (= (:branch push) (-> push :repo :defaultBranch))))

(defn- check-configuration [handler]
  (fn [request]
    (go
      (cond (= "inPR" (:policy request))
            (<! (handler (assoc request
                                :configuration {:branch (gstring/format "cljfmt-%s" (-> request :ref :branch))
                                                :target-branch (-> request :ref :branch)
                                                :body (str "Configuration that triggered this change:\n" (:configuration request))
                                                :title "Format code in line with current guidelines"})))

            (or (= "onBranch" (:policy request))
                (and (= "onDefaultBranch" (:policy request))
                     (is-default-branch? request)))
            (<! (handler (assoc request :commit-on-master true)))

            (and
             (= "onDefaultBranch" (:policy request))
             (not (is-default-branch? request)))
            (<! (api/finish request :success "Not formatting as this is not the default branch" :visibility :hidden))

            :else
            (<! (api/finish request :failure "skill requires either 'update directly' or 'update in PR' to be configured"))))))

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
   (api/dispatch {:OnAnyPush (-> (api/finished)
                                 (api/from-channel #(go (<! (try
                                                              (cljfmt/cljfmt (-> % :project :path))
                                                              :done
                                                              (catch :default ex
                                                                (log/error "unable to run cljfmt")
                                                                (log/error ex)
                                                                {:error ex
                                                                 :message "unable to run cljfmt"})))))
                                 (api/edit-inside-PR :configuration)
                                 (api/clone-ref)
                                 (check-configuration)
                                 (api/add-skill-config :policy :default-branch)
                                 (api/extract-github-token)
                                 (api/create-ref-from-event)
                                 (api/status :send-status (fn [request]
                                                            (cond (= :raised (-> request :edit-result))
                                                                  (gstring/format "**cljfmt skill** raised a PR")
                                                                  :else
                                                                  "handled Push successfully"))))})))

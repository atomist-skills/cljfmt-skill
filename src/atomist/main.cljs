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
      (cond (or
             (= "inPR" (:fix request))
             (and
              (= "inPROnDefaultBranch" (:fix request))
              (is-default-branch? request)))
            (<! (handler (assoc request
                           :atomist.gitflows/configuration
                           {:branch (gstring/format "cljfmt-%s" (-> request :ref :branch))
                            :target-branch (-> request :ref :branch)
                            :body (str "Configuration that triggered this change:\n" (:configuration request))
                            :title "cljfmt fix"
                            :type :in-pr})))

            (or
             (= "onBranch" (:fix request))
             (and
              (= "inPROnDefaultBranch" (:fix request))
              (not (is-default-branch? request)))
             (and
              (= "onDefaultBranch" (:fix request))
              (is-default-branch? request)))
            (<! (handler (assoc request
                           :atomist.gitflows/configuration
                           {:message "cljfmt fix"
                            :type :commit-then-push})))

            :else
            (<! (api/finish request :success (gstring/format "not fixing %s" (:fix request)) :visibility :hidden))))))

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
                                 ((fn [request]
                                    (go (try
                                          (cljfmt/cljfmt (-> request :project :path))
                                          :done
                                          (catch :default ex
                                            (log/error "unable to run cljfmt")
                                            (log/error ex)
                                            {:error ex
                                             :message "unable to run cljfmt"})))))
                                 (api/edit-inside-PR :atomist.gitflows/configuration)
                                 (api/clone-ref)
                                 (check-configuration)
                                 (api/add-skill-config :fix :config)
                                 (api/extract-github-token)
                                 (api/create-ref-from-event)
                                 (api/status :send-status (fn [request]
                                                            (cond
                                                              (= :raised (-> request :edit-result))
                                                              (gstring/format "**cljfmt skill** raised a PR")
                                                              (= :committed (-> request :edit-result))
                                                              (gstring/format "**cljfmt skill** pushed a Commit")
                                                              (= :skipped (-> request :edit-result))
                                                              (gstring/format "**cljfmt skill** made no fixes")
                                                              :else
                                                              "handled Push successfully"))))})))

(comment
 (require 'atomist.local-runner)
 (enable-console-print!)
 (atomist.local-runner/set-env :prod)
 ;; should be a PR
 (-> (atomist.local-runner/fake-push "T095SFFBK" "atomisthq" "internal-skill" "master")
     (assoc :configuration {:name "default"
                            :parameters [{:name "fix" :value "inPROnDefaultBranch"}]})
     (atomist.local-runner/call-event-handler handler))
 ;; should be a straight Commit
 (-> (atomist.local-runner/fake-push "T095SFFBK" "atomisthq" "internal-skill" "master")
     (assoc :configuration {:name "default"
                            :parameters [{:name "fix" :value "inPROnDefaultBranch"}]})
     (atomist.local-runner/call-event-handler handler)))

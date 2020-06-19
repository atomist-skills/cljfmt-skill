(ns atomist.main
  (:require [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<! >! timeout chan]]
            [goog.string.format]
            [atomist.cljs-log :as log]
            [atomist.api :as api]
            [atomist.cljfmt :as cljfmt]
            [goog.string :as gstring]
            [goog.string.format]
            [clojure.edn :as edn]
            [cljs-node-io.core :as io])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- is-default-branch?
  [request]
  (let [push (-> request :data :Push first)]
    (= (:branch push) (-> push :repo :defaultBranch))))

(defn- check-cljfmt-config [handler]
  (fn [request]
    (go
      (if (:config request)
        (try
          (let [c (edn/read-string (:config request))]
            (if (not (map? c))
              (<! (api/finish request :failure (gstring/format "%s is not a valid cljmt map" (:config request))))
              (<! (handler (assoc request :cljfmt-opts c)))))
          (catch :default ex
            (log/warn ex "error parsing edn " (:config request))
            (<! (api/finish request :failure (gstring/format "%s is not a valid cljfmt map" (:config request))))))
        (<! (handler request))))))

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
            (<! (api/finish request :success (gstring/format "nothing to do: %s policy" (:fix request)) :visibility :hidden))))))

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
                                 (api/from-channel (fn [request]
                                                     (go (try
                                                           (cljfmt/cljfmt
                                                            (-> request :project :path)
                                                            (merge
                                                             {}
                                                             (:cljfmt-opts request)))
                                                           :done
                                                           (catch :default ex
                                                             (log/error "unable to run cljfmt")
                                                             (log/error ex)
                                                             {:error ex
                                                              :message "unable to run cljfmt"})))))
                                 (api/edit-inside-PR :atomist.gitflows/configuration)
                                 (api/clone-ref)
                                 (check-configuration)
                                 (check-cljfmt-config)
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
 (atomist.local-runner/set-env :prod-github-auth)

 ;; should fail because of invalid config
 (-> (atomist.local-runner/fake-push "T29E48P34" "atomist-skills" "jrday-testing" "master")
     (assoc :configuration {:name "default"
                            :parameters [{:name "fix" :value "inPR"}
                                         {:name "config" :value ":cljfmt {:indents ^:replace {#\".*\" [[:inner 0]]}}"}]})
     (atomist.local-runner/call-event-handler handler))
 (-> (atomist.local-runner/fake-push "T29E48P34" "atomist-skills" "jrday-testing" "master")
     (assoc :configuration {:name "default"
                            :parameters [{:name "fix" :value "inPR"}
                                         {:name "config" :value "{:indents ^:replace {#\".*\" [[:inner 0]]}}"}]})
     (atomist.local-runner/call-event-handler handler))
 (-> (atomist.local-runner/fake-push "T29E48P34" "atomist-skills" "jrday-testing" "master")
     (assoc :configuration {:name "default"
                            :parameters [{:name "fix" :value "inPR"}
                                         {:name "config" :value "{org.me/foo [[:inner 0]]}"}]})
     (atomist.local-runner/call-event-handler handler))

 ;; should be a straight Commit
 (-> (atomist.local-runner/fake-push "AEIB5886C" "slimslender" "clj1" "master")
     (assoc :configuration {:name "default"
                            :parameters [{:name "fix" :value "inPR"}
                                         #_{:name "config" :value "{:indents {org.me/foo [[:inner 1]]}}"}]})
     (atomist.local-runner/call-event-handler handler))

 (-> (atomist.local-runner/fake-push "AEIB5886C" "slimslender" "clj1" "master")
     (assoc :configuration {:name "default"
                            :parameters [{:name "fix" :value "onDefaultBranch"}
                                         #_{:name "config" :value "{:indents {org.me/foo [[:inner 1]]}}"}]})
     (atomist.local-runner/call-event-handler handler))

 ;; nothing to do not on default branch
 (-> (atomist.local-runner/fake-push "AEIB5886C" "slimslender" "clj1" "slimslenderslacks-patch-1")
     (assoc :configuration {:name "default"
                            :parameters [{:name "fix" :value "onDefaultBranch"}
                                         {:name "config" :value "{:indents {org.me/foo [[:inner 1]]}}"}]})
     (atomist.local-runner/call-event-handler handler))

 ;; something to do on
 (-> (atomist.local-runner/fake-push "AEIB5886C" "slimslender" "clj1" "slimslenderslacks-patch-1")
     (assoc :configuration {:name "default"
                            :parameters [{:name "fix" :value "onBranch"}
                                         {:name "config" :value "{:indents {org.me/foo [[:inner 1]]}}"}]})
     (atomist.local-runner/call-event-handler handler))

 (-> (atomist.local-runner/fake-push "AEIB5886C" "slimslender" "clj1" "slimslenderslacks-patch-1")
     (assoc :configuration {:name "default"
                            :parameters [{:name "fix" :value "inPROnDefaultBranch"}
                                         #_{:name "config" :value "{:indents {org.me/foo [[:inner 1]]}}"}]})
     (atomist.local-runner/call-event-handler handler)))

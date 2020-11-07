;; Copyright © 2020 Atomist, Inc.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns atomist.main
  (:require [cljs.core.async :refer [<!]]
            [goog.string.format]
            [atomist.cljs-log :as log]
            [atomist.api :as api]
            [atomist.cljfmt :as cljfmt]
            [goog.string :as gstring]
            [clojure.edn :as edn])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn run-cljfmt [request]
  (go
    (try
      (cljfmt/cljfmt (-> request :project :path)
                     (merge {} (:cljfmt-opts request)))
      :done
      (catch :default ex
        (log/error ex "unable to run cljfmt")
        {:error ex
         :message "unable to run cljfmt"}))))

(defn- is-default-branch?
  [request]
  (let [push (-> request :data :Push first)]
    (= (:branch push) (-> push :repo :defaultBranch))))

(defn check-cljfmt-config [handler]
  (fn [request]
    (api/trace "check-cljfmt-config")
    (go
      (if-let [config (-> request :config :config)]
        (try
          (let [c (edn/read-string config)]
            (if (not (map? c))
              (<! (api/finish request :failure (gstring/format "%s is not a valid cljmt map" config)))
              (<! (handler (assoc request :cljfmt-opts c)))))
          (catch :default ex
            (log/warn ex "error parsing edn " config)
            (<! (api/finish request :failure (gstring/format "%s is not a valid cljfmt map" config)))))
        (<! (handler request))))))

(defn check-configuration [handler]
  (fn [request]
    (api/trace (gstring/format "check-configuration %s, %s" (:fix request) (:config request)))
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
                                 :body (gstring/format
                                        "running [cljfmt fix](https://github.com/weavejester/cljfmt) with configuration %s"
                                        (-> request :skill :configuration :name))
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
                                {:message "running cljfmt fix"
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
                                 (api/from-channel run-cljfmt)
                                 (api/edit-inside-PR :atomist.gitflows/configuration)
                                 (api/clone-ref)
                                 (check-configuration)
                                 (check-cljfmt-config)
                                 (api/add-skill-config)
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

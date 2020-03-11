(ns user
  (:require [atomist.main]
            [atomist.cljs-log :as log]
            [atomist.api :as api]
            [cljs.core.async :refer [<!]]
            [atomist.sdmprojectmodel :as sdm]
            ["@atomist/automation-client" :as ac]
            ["@atomist/automation-client/lib/operations/support/editorUtils" :as editor-utils]
            [atomist.json :as json])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(def token (.. js/process -env -API_KEY_SLIMSLENDERSLACKS_STAGING))
(def github-token (.. js/process -env -GITHUB_TOKEN))

(defn fake-handler [& args]
  (log/info "args " args))

(defn fake-push-on-clj1-in-staging [policy]
 (.catch
  (.then
   (atomist.main/handler #js {:data {:Push [{:branch "master"
                                             :repo {:name "clj1"
                                                    :org {:owner "atomisthqa"
                                                          :scmProvider {:providerId "zjlmxjzwhurspem"
                                                                        :credential {:secret github-token}}}}
                                             :after {:message ""}}]}
                              :configuration {:name "clj-format"
                                              :parameters [{:name "policy" :value policy}]}
                              :secrets [{:uri "atomist://api-key" :value token}]
                              :extensions [:team_id "AK748NQC5"]}
                         fake-handler)
   (fn [v] (log/info "value " v)))
  (fn [error] (log/info "error " error))))

(comment
 (fake-push-on-clj1-in-staging "inPR")
 (fake-push-on-clj1-in-staging "onBranch"))


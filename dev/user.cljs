(ns user
  (:require [atomist.main]
            [atomist.local-runner])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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
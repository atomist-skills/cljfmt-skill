(ns user
  (:require [atomist.main :refer [handler]]
            [atomist.local-runner]
            [cljfmt.core :refer [reformat-string default-indents]]))

(defn reformat [s]
  (print (reformat-string
          s
          {:insert-missing-whitespace? true
           :remove-surrounding-whitespace? true
           :remove-trailing-whitespace? true
           :remove-consecutive-blank-lines? true
           :indents {'ns [[:block 1]]}})))

(comment

  (enable-console-print!)
  (atomist.local-runner/set-env :prod-github-auth)

 ;; alter in a PR
  (-> (atomist.local-runner/fake-push "AEIB5886C" "slimslender" {:name "clj1"} "master")
      (assoc :configuration {:name "default"
                             :parameters [{:name "fix" :value "onDefaultBranch"}
                                          {:name "config" :value "{:indents {org.me/foo [[:inner 1]]}}"}]})
      (atomist.local-runner/call-event-handler handler))

  ;; then bring it back with a straight commit
  (-> (atomist.local-runner/fake-push "AEIB5886C" "slimslender" {:name "clj1"} "master")
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
  (-> (atomist.local-runner/fake-push "AEIB5886C" "slimslender" {:name "clj1"} "slimslenderslacks-patch-1")
      (assoc :configuration {:name "default"
                             :parameters [{:name "fix" :value "onBranch"}
                                          #_{:name "config" :value "{:indents {org.me/foo [[:inner 1]]}}"}]})
      (atomist.local-runner/call-event-handler handler))

  (-> (atomist.local-runner/fake-push "AEIB5886C" "slimslender" "clj1" "slimslenderslacks-patch-1")
      (assoc :configuration {:name "default"
                             :parameters [{:name "fix" :value "inPROnDefaultBranch"}
                                          #_{:name "config" :value "{:indents {org.me/foo [[:inner 1]]}}"}]})
      (atomist.local-runner/call-event-handler handler)))
(ns atomist.main-t
  (:require [cljs.test :refer-macros [deftest is use-fixtures async run-tests]]
            [cljs.core.async :refer-macros [go] :refer [<!]]
            [atomist.main :as main]))

(deftest check-config-t
  (async done
         (go
           (is (= {:type :in-pr
                   :title "cljfmt fix"
                   :branch "cljfmt-hey"
                   :target-branch "hey"
                   :body "running [cljfmt fix](https://github.com/weavejester/cljfmt) with configuration config"}
                  (:atomist.gitflows/configuration
                   (<! ((main/check-configuration #(go %))
                        {:fix "inPR"
                         :ref {:branch "hey"}
                         :configuration {:name "config"}
                         :data {:Push [{:branch "master"
                                        :repo {:defaultBranch "master"}}]}})))))
           (done))))

(deftest check-default-branch-inPRWithDefaultBranch
  (async done
         (go
           (is (= :in-pr
                  (:type
                   (:atomist.gitflows/configuration
                    (<! ((main/check-configuration #(go %))
                         {:fix "inPROnDefaultBranch"
                          :ref {:branch "hey"}
                          :configuration {:name "config"}
                          :data {:Push [{:branch "master"
                                         :repo {:defaultBranch "master"}}]}}))))))
           (done))))

(deftest check-non-default-branch-inPRWithDefaultBranch
  (async done
         (go
           (is (= :commit-then-push
                  (:type
                   (:atomist.gitflows/configuration
                    (<! ((main/check-configuration #(go %))
                         {:fix "inPROnDefaultBranch"
                          :ref {:branch "hey"}
                          :configuration {:name "config"}
                          :data {:Push [{:branch "non-master"
                                         :repo {:defaultBranch "master"}}]}}))))))
           (done))))

(deftest check-on-branch-with-non-default-branch
  (async done
         (go
           (is (= :commit-then-push
                  (:type
                   (:atomist.gitflows/configuration
                    (<! ((main/check-configuration #(go %))
                         {:fix "onBranch"
                          :sendreponse (fn [& args] (go true))
                          :ref {:branch "hey"}
                          :configuration {:name "config"}
                          :data {:Push [{:branch "non-master"
                                         :repo {:defaultBranch "master"}}]}}))))))
           (done))))

(deftest check-on-default-branch
  (async done
         (go
           (is (= {:success "nothing to do: onDefaultBranch policy"
                   :visibility :hidden}
                  (:api/status (<! ((main/check-configuration #(go %))
                                    {:fix "onDefaultBranch"
                                     :sendreponse (fn [& args] (go (first args)))
                                     :ref {:branch "hey"}
                                     :configuration {:name "config"}
                                     :correlation_id "corrid"
                                     :api_version "1"
                                     :data {:Push [{:branch "non-master"
                                                    :repo {:defaultBranch "master"}}]}})))))
           (done))))

(deftest cljfmt-config-success
  (let [opts {:indents {'defn [[:inner 0]]}}]
    (async done
           (go
             (is (= opts
                    (:cljfmt-opts (<! ((main/check-cljfmt-config #(go %))
                                       {:config (pr-str opts)})))))
             (done)))))

(deftest cljfmt-config-bad-edn
  (async done
         (go
           (is (= {:failure "{bad edn :: & is not a valid cljfmt map"}
                  (:api/status
                   (<! ((main/check-cljfmt-config #(go %))
                        {:config "{bad edn :: &"
                         :correlation_id "corrid"
                         :api_version "1"
                         :sendreponse (fn [& args] (go (first args)))})))))
           (done))))

(enable-console-print!)
(run-tests)
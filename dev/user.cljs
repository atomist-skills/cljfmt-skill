(ns user
  (:require [atomist.main]
            [atomist.local-runner])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(comment
 (enable-console-print!)
 (atomist.local-runner/set-env :prod)
 (-> (atomist.local-runner/fake-push "T095SFFBK" "atomisthq" "bot-service" "master")
     (assoc-in [:configuration] {:name "default"
                                 :parameters [{:name "policy" :value "inPR"}]})
     (atomist.local-runner/call-event-handler atomist.main/handler)))

(ns user
  (:require [atomist.main]
            [atomist.cljs-log :as log]
            [atomist.api :as api]
            [cljs.core.async :refer [<!]]
            [atomist.sdmprojectmodel :as sdm]
            ["@atomist/sdm" :as atomistsdm]
            ["@atomist/automation-client" :as ac]
            ["@atomist/automation-client/lib/operations/support/editorUtils" :as editor-utils]
            [atomist.json :as json])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(def token (.. js/process -env -API_KEY_SLIMSLENDERSLACKS_STAGING))
(def github-token (.. js/process -env -GITHUB_TOKEN))


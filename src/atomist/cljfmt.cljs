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

(ns atomist.cljfmt
  (:require [cljfmt.core]
            [cljs-node-io.core :as io :refer [slurp spit]]
            [atomist.cljs-log :as log]))

(def default-options
  {:project-root "."
   :file-pattern #"\.clj[csx]?$"
   :ansi? true
   :indentation? true
   :insert-missing-whitespace? true
   :remove-surrounding-whitespace? true
   :remove-trailing-whitespace? true
   :remove-consecutive-blank-lines? true
   :indents cljfmt.core/default-indents
   :alias-map {}})

(defn find-files [dir]
  (let [f (io/file dir)]
    (if (.isDirectory f)
      (filter #(re-find #"\.clj[sx]?$" %) (io/file-seq dir))
      [f])))

(defn cljfmt
  "Format files with cljfmt"
  [dir opts]
  (let [merged-opts (merge-with merge default-options
                                (select-keys opts [:remove-surrounding-whitespace?
                                                   :remove-trailing-whitespace?
                                                   :insert-missing-whitespace?
                                                   :remove-consecutive-blank-lines?
                                                   :indents
                                                   :alias-map]))
        edited-files (->> (for [f (find-files dir) :let [original (slurp f)]]
                            (let [[k v e] (try
                                            [:formatted (cljfmt.core/reformat-string original merged-opts)]
                                            (catch :default ex
                                              [:failed-to-process f ex]))]
                              (case k
                                :formatted (when (not= original v)
                                             (log/info "Reformatting " f)
                                             (spit f v)
                                             f)
                                :failed-to-process (log/warnf "cljformat could not process %s - %s" v e))))
                          (filter identity))]
    (if (> (count edited-files) 0)
      (log/infof "edited %d files" (count edited-files))
      (log/info "No clojure files formatted"))))
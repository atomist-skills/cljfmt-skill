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
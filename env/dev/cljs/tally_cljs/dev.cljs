(ns ^:figwheel-no-load tally-cljs.dev
  (:require
    [tally-cljs.core :as core]
    [devtools.core :as devtools]))

(devtools/install!)

(enable-console-print!)

(core/init!)

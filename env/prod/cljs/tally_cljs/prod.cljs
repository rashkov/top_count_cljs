(ns tally-cljs.prod
  (:require [tally-cljs.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)

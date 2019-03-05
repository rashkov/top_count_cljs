(ns tally-cljs.components.home
  (:require [reagent.core :as reagent :refer [atom]]))

(def app-state (reagent/atom {:sort-val :total
                              :ascending false
                              :medals-data []
                              :network-error nil}))
(def medals-data-cursor (reagent/cursor app-state [:medals-data]))
(def sort-val-cursor (reagent/cursor app-state [:sort-val]))
(def ascending-cursor (reagent/cursor app-state [:ascending]))
(def network-error-cursor (reagent/cursor app-state [:network-error]))

(defn weighted-sort-by-column [get-column, data]
  "Sorts by a particular column (gold, silver, bronze, or total).
   Breaks ties by doing a secondary sort on gold, then silver."
  (sort-by (fn [item]
             (+
              (get-column item)
              (/ (:gold item) 10)
              (/ (:silver item) 100)))
           data))

;; From reagent-cookbook example:
(defn update-sort-value [new-val]
  (if (= new-val @sort-val-cursor)
    (swap! ascending-cursor not)
    (reset! ascending-cursor false))
  (reset! sort-val-cursor new-val))

;; From reagent-cookbook example:
(defn sorted-contents []
  (let [sorted-contents (weighted-sort-by-column @sort-val-cursor @medals-data-cursor)]
    (if @ascending-cursor
      sorted-contents
      (rseq (vec sorted-contents)))))

(defn get-flag [country-code]
  (let [FLAG_ORDER ["AUT" "BLR" "CAN" "CHN" "FRA" "GER" "ITA" "NED" "NOR" "RUS" "SUI" "SWE" "USA"]
        FLAG_URL "https://s3-us-west-2.amazonaws.com/reuters.medals-widget/flags.png"
        FLAG_WIDTH 28
        FLAG_HEIGHT 17
        flag-index (.indexOf FLAG_ORDER country-code)
        url-style (str "url(" FLAG_URL ") 0px -" (* flag-index FLAG_HEIGHT) "px")
        flag-style {:width FLAG_WIDTH
                    :height FLAG_HEIGHT
                    :background url-style}]
    [:div {:style flag-style}]))

(defn get-medal-header [medal]
  (case medal
    :gold [:div.medal-header.gold]
    :silver [:div.medal-header.silver]
    :bronze [:div.medal-header.bronze]))

(defn sort-class [medal]
  (let [sort-val @sort-val-cursor
        ascending @ascending-cursor
        sorting (= medal @sort-val-cursor)
        sort-class (if (and sorting ascending)
                     :ascending
                     (if (and sorting (not ascending))
                       :descending))]
    sort-class
))

(defn home []
  (let [err @network-error-cursor]
    (if err
      [:div (.-message err)]
      [:div.app
       [:div.title "MEDAL COUNT"]
       [:table.medals-table
        [:thead
         [:tr
          [:th ""]
          [:th {:on-click #(update-sort-value :gold)
                :class (sort-class :gold)}
           (get-medal-header :gold)]
          [:th {:on-click #(update-sort-value :silver)
                :class (sort-class :silver)}
           (get-medal-header :silver)]
          [:th {:on-click #(update-sort-value :bronze)
                :class (sort-class :bronze)}
           (get-medal-header :bronze)]
          [:th.total-th {:on-click #(update-sort-value :total)
                         :class (sort-class :total)}
           "TOTAL"]]]
        [:tbody
         (map-indexed #(identity
                        ^{:key (:code %2)}
                        [:tr
                         [:td.flag-column
                          [:span.row-num (+ 1 %1)]
                          [:span.flag (get-flag (:code %2))]
                          [:span.country-code (:code %2)]]
                         [:td (:gold %2)]
                         [:td (:silver %2)]
                         [:td (:bronze %2)]
                         [:td.total-td (:total %2)]]) (sorted-contents))]]])))

(defn home-did-mount []
  (->
   (js/fetch "https://s3-us-west-2.amazonaws.com/reuters.medals-widget/medals.json")
   (.then (fn [response]
            (if (.-ok response)
              (.json response)
              (throw (js/Error. (str "Failed to fetch medals data: " (.-status response) " " (.-statusText response)))))))
   (.then (fn [data]
            (.map data #(do
                          (set! (.-total %) (+ (.-gold %) (.-silver %) (.-bronze %)))
                          %))
            (reset! medals-data-cursor (js->clj data :keywordize-keys true))))
   (.catch (fn [error]
             (reset! network-error-cursor error)))))

(defn home-component []
  (reagent/create-class {:reagent-render home
                         :component-did-mount home-did-mount}))

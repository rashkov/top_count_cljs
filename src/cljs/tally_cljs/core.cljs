(ns tally-cljs.core
  (:require
   [reagent.core :as reagent :refer [atom]]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [clerk.core :as clerk]
   [accountant.core :as accountant]))

;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :index]
    ;; ["/items"
    ;;  ["" :items]
    ;;  ["/:item-id" :item]]
    ;; ["/about" :about]
    ]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

(path-for :about)
;; -------------------------
;; Page components

(def app-state (reagent/atom {:sort-val :total :ascending true :medals-data [] :network-error nil}))

(defn medals-data [] (:medals-data @app-state))

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
  (if (= new-val (:sort-val @app-state))
    (swap! app-state update-in [:ascending] not)
    (swap! app-state assoc :ascending false))
  (swap! app-state assoc :sort-val new-val))

;; From reagent-cookbook example:
(defn sorted-contents []
  (let [sorted-contents (weighted-sort-by-column (:sort-val @app-state) (medals-data))]
    (if (:ascending @app-state)
      sorted-contents
      (rseq sorted-contents))))

(defn get-flag [country-code]
  (let [FLAG_ORDER ["AUT"
                    "BLR"
                    "CAN"
                    "CHN"
                    "FRA"
                    "GER"
                    "ITA"
                    "NED"
                    "NOR"
                    "RUS"
                    "SUI"
                    "SWE"
                    "USA"]
        FLAG_URL "https://s3-us-west-2.amazonaws.com/reuters.medals-widget/flags.png"
        FLAG_WIDTH 28
        FLAG_HEIGHT 17
        flag-index (.indexOf FLAG_ORDER country-code)
        url-style (str "url("
                       FLAG_URL
                       ") 0px -"
                       (* flag-index FLAG_HEIGHT)
                       "px")
        flag-style {:width FLAG_WIDTH
                    :height FLAG_HEIGHT
                    :background url-style}]

    [:div {:style flag-style}]))

(defn get-medal-header [medal]
  (case medal
    :gold [:div.medal-header.gold]
    :silver [:div.medal-header.silver]
    :bronze [:div.medal-header.bronze]))

(defn home []
  (let [err (:network-error @app-state)]
    (if err
      [:div (.-message err)]
      [:span.app
       [:div.title "MEDAL COUNT"]
       [:table.medals-table
        [:thead
         [:tr
          [:th ""]
          [:th {:on-click #(update-sort-value :gold)} (get-medal-header :gold)]
          [:th {:on-click #(update-sort-value :silver)} (get-medal-header :silver)]
          [:th {:on-click #(update-sort-value :bronze)} (get-medal-header :bronze)]
          [:th.total-th {:on-click #(update-sort-value :total)} "TOTAL"]]]
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
            (swap! app-state assoc :medals-data (js->clj data :keywordize-keys true))))
   (.catch (fn [error]
             (swap! app-state assoc :network-error error)))))

(defn home-component []
  (reagent/create-class {:reagent-render home
                         :component-did-mount home-did-mount}))
(defn home-page []
  (fn []
    (home-component)))

;; (defn items-page []
;;   (fn []
;;     [:span.main
;;      [:h1 "The items of tally-cljs"]
;;      [:ul (map (fn [item-id]
;;                  [:li {:name (str "item-" item-id) :key (str "item-" item-id)}
;;                   [:a {:href (path-for :item {:item-id item-id})} "Item: " item-id]])
;;                (range 1 60))]]))


;; (defn item-page []
;;   (fn []
;;     (let [routing-data (session/get :route)
;;           item (get-in routing-data [:route-params :item-id])]
;;       [:span.main
;;        [:h1 (str "Item " item " of tally-cljs")]
;;        [:p [:a {:href (path-for :items)} "Back to the list of items"]]])))


;; (defn about-page []
;;   (fn [] [:span.main
;;           [:h1 "About tally-cljs"]]))


;; -------------------------
;; Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page
    ;; :about #'about-page
    ;; :items #'items-page
    ;; :item #'item-page
    ))


;; -------------------------
;; Page mounting component


(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       ;; [:header
       ;;  [:p [:a {:href (path-for :index)} "Home"] " | "
       ;;   [:a {:href (path-for :about)} "About tally-cljs"]]]
       [page]
       ;; [:footer
       ;;  [:p "tally-cljs was generated by the "
       ;;   [:a {:href "https://github.com/reagent-project/reagent-template"} "Reagent Template"] "."]]
       ])))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data  match))
            route-params (:path-params match)]
        (reagent/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))

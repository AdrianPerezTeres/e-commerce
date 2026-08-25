(ns ecommerce.core
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [ecommerce.events]
            [ecommerce.subs]
            [ecommerce.routes :as routes]
            [ecommerce.views.app :as app]))

(defn ^:dev/after-load mount-root []
  (rf/clear-subscription-cache!)
  (rdom/render [app/main-panel]
               (.getElementById js/document "app")))

(defn init []
  (rf/dispatch-sync [:initialize-db])
  (routes/start!)
  (mount-root)
  (when ^boolean goog.DEBUG
    (rf/dispatch [:login {:username "admin" :password "admin123"}])))

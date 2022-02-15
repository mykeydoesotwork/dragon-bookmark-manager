(ns dragon-bookmark-manager.core
  (:require
   [cljsjs.react]
   [dragon-bookmark-manager.dndmenu :as dndmenu]))

(defn init! []
  (dndmenu/mount-dndmenu))

(init!)



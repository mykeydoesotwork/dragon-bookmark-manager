
(ns dragon-bookmark-manager.subs
  (:require [re-frame.core :as re-frame]
            [dragon-bookmark-manager.utilities :refer [fid->dkey dkey->fid subfolder? zip-walk-closure get-all-subfolders
                                                       chrome-getSubTree-bookmark get-dz-element
                                                       zip-walk map-vec-zipper px-to-int setstyle setattrib get-property
                                                       get-computed-style edge-overlay-ondrop lastZIndex gen-next-zindex]]
            [dragon-bookmark-manager.events :as h]            
            [clojure.pprint]))

  
  ;; not used in subs.cljs
(re-frame/reg-sub
 :dnd/db
 (fn [db _]
   db))
  
  ;; used in :dnd/dropped-item-overlap-id and :dnd/get-colliding-drop-zone-and-index
(re-frame/reg-sub
 :dnd/mouse-position
 (fn [db _]
   (get-in db [:dnd/state :mouse-position])))
  
  ;;used in  :dnd/get-colliding-drop-zone-and-index
(re-frame/reg-sub
 :dnd/drop-zones
 (fn [db _]
   (get-in db [:dnd/state :drop-zones])))
  
  ;;used in  :my-drop-dispatch
(re-frame/reg-sub
 :dnd/bookmark-atom
 (fn [db _]
   (get-in db [:dnd/state :bookmark-atom])))

;;used for rightclicked border highlighting of elements while contextmenu is visible
(re-frame/reg-sub
 :dnd/get-contextmenu-visible
 (fn [db _]
   (get db :contextmenuVisible)))

(re-frame/reg-sub
 :dnd/get-dz-element
 (fn [db [_ dz-id elementId]]
   (->>
    (get-in db [:dnd/state :drop-zones dz-id])
    (filter (comp (partial = elementId) :id));; filter previous line by draggable id equal to "elementId"
    first)))

(re-frame/reg-sub
 :dnd/get-recently-modified
 ;; can be :tabselected or :historyselected
 (fn [db _]
   (get-in db [:dnd/state :recently-modified])))

(re-frame/reg-sub
 :dnd/get-tabOrHistorySelected
 ;; can be :tabselected or :historyselected
 (fn [db _]
   (get-in db [:dnd/state :tab-history-options :tabOrHistorySelected])))

(re-frame/reg-sub
 :dnd/get-searchHistoryText
 ;; can be :tabselected or :historyselected
 (fn [db _]
   (get-in db [:dnd/state :tab-history-options :searchHistoryText])))

(re-frame/reg-sub
 :dnd/get-searchTabsText
 (fn [db _]
   (get-in db [:dnd/state :tab-history-options :searchTabsText])))

(re-frame/reg-sub
 :dnd/get-historyDays
 (fn [db _]
   (get-in db [:dnd/state :tab-history-options :historyDays])))

;; new ids will be created by chrome when dropped, or randomly by stub-create-bookmark, but :id (:id y) is used here because
;; at least then the generated reagent key in the dropzone ^{:key (:id de) will be unique temporarily 
(re-frame/reg-sub
 :dnd/get-tabs
 (fn [db _]
   (let [dropped-elements (get-in db [:dnd/state :tabs])]
     (if (= (first dropped-elements) :search-not-found) dropped-elements
         (reduce (fn [x y] (conj x {:title (:title y) :id (str (:id y)) :windowId (str (:windowId y)) :url (:url y) :type :tablink}))  []
                 dropped-elements)))))

(re-frame/reg-sub 
 :dnd/get-history
 (fn [db _]
   (let [dropped-elements (get-in db [:dnd/state :history])]
     (if (= (first dropped-elements) :search-not-found) dropped-elements
         (reduce (fn [x y] (conj x {:title (:title y) :id (str (:id y)) :url (:url y) :lastVisitTime (:lastVisitTime y) :type :historylink}))  []
                 dropped-elements)))))

(re-frame/reg-sub
 :dnd/get-selected
 ;; tabOrHistorySelected is :tabselected, :historyselected
 ;; :tab-history-options {:tabOrHistorySelected :tabselected [], :historyselected []}
 (fn [db [_ dropzone-id tabOrHistorySelected]]
   (if (= :tab-history dropzone-id)
     (get-in db [:dnd/state :tab-history-options tabOrHistorySelected])
     (get-in db [:dnd/state :drop-zone-options dropzone-id :selected]))))

(re-frame/reg-sub
 :dnd/get-keystate
 (fn [db [_ key]]
   (key db)))

(re-frame/reg-sub
 :dnd/get-cutoff-elements
 ;; id is :dropzone-1 or :tab-history
 ;; tabOrHistorySelected is :tabselected, :historyselected
 (fn [db [_ id tabOrHistorySelected]]
   (if (= :tab-history id)
     (let [cutoffKey (if (= :tabselected tabOrHistorySelected) :cutoffTabElements :cutoffHistoryElements)]
       (get-in db [:dnd/state :tab-history-options cutoffKey]))
     (get-in db [:dnd/state :drop-zone-options id :cutoffDropzoneElements]))))

;; used in :dnd/dropped-item-overlap-id and :dnd/dropped-elements-with-drop-marker 
(re-frame/reg-sub
 :dnd/dropped-elements
 (fn [db [_ id]]
   (get-in db [:dnd/state :drop-zones id])))
  
;; used in  :dnd/dropped-elements-with-drop-marker
(re-frame/reg-sub
 :dnd/dragdrop-options
 (fn [db [_ id]]
   (get-in db [:dnd/state :drop-zone-options id])))

(re-frame/reg-sub
 :dnd/dropzone-options
 (fn [db _]
   (get-in db [:dnd/state :drop-zone-options])))

;; fetch the collapsedstartuptoggle which is initialized from embeddedmenuconfiguration on embeddedmenu initialization
;; defaults to false in floating menus. Returns true or false.
(re-frame/reg-sub
 :dnd/get-collapsedStartupToggle
 (fn [db [_ dropzone-id]]
   ;; dropzone-id is of the form :dropzone-1 or :tab-history
   (if (= dropzone-id :tab-history)
     (get-in db [:dnd/state :tab-history-options :collapsedStartupToggle])
     (get-in db [:dnd/state :drop-zone-options dropzone-id :collapsedStartupToggle]))))

;; note that if dropzones don't exist @pinned-state and @menuOpen-state return nil instead of false
(re-frame/reg-sub
 :dnd/menuOpen-state
 (fn [db [_ id]]
   (get-in db [:dnd/state :drop-zone-options id :menuOpen])))
  
(re-frame/reg-sub
 :dnd/pinned-state
 (fn [db [_ id]]
   (get-in db [:dnd/state :drop-zone-options id :pinned])))
  
(re-frame/reg-sub
 :dnd/get-zindex
 (fn [db [_ id]]
   (get-in db [:dnd/state :drop-zone-options id :z-index])))
  
(re-frame/reg-sub
 :dnd/get-drag-state
 (fn [db _]
   (get-in db [:dnd/state :dragging])))
  
(re-frame/reg-sub
 :dnd/get-mouse-button-state
 (fn [db _]
   (get-in db [:mouse-button])))
  
(re-frame/reg-sub
 :dnd/get-clipboard
 (fn [db _]
   (get-in db [:dnd/state :clipboard])))

(re-frame/reg-sub
 :dnd/get-view-titles
 (fn [db _]
   (get-in db [:dnd/state :views :view-titles])))


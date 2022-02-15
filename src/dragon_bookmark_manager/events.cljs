(ns dragon-bookmark-manager.events
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [dragon-bookmark-manager.utilities :refer [chrome-create-bookmark chrome-getSubTree-bookmark stub-create-bookmark stub-removeTree-bookmark
                                                       stub-update-bookmark find-id map-vec-zipper dkey->fid select_all_elements_at_point
                                                       onevent-dispatch-refresh-fnhandle]]
            [clojure.zip :as zip]
            [clojure.pprint]))

;; << register events, bounding rect >>

    ;;--------------------------------------------
    ;;                Viewport
    ;;--------------------------------------------
    ;;                   ^                      ^
    ;;                   | y / top              |
    ;;                   v                      |
    ;;             ------+--------------------  |
    ;;<----------->|                         |  | bottom
    ;;   x / left  |                         |  |
    ;;             |     Element             |  |
    ;;             |                         |  |
    ;;             |                         |  |
    ;;             |                         |  |
    ;;             |                         |  v
    ;;             |-------------------------|-----
    ;;<------------------------------------->|
    ;;            right


;; only used in dndmenu.cljs (defn FolderButton ...)    
(defn bounding-rect
  [e]
  (if (nil? e)
    nil
    (let [rect (.getBoundingClientRect e) ;;--returns the size of an element and its position relative to the viewport.
    	  pos  {:top    (.-top rect)
    		:left   (.-left rect)
    		:bottom (.-bottom rect)
    		:right  (.-right rect)}
          
    	  pos' (-> pos
                   (update :top    + (.-scrollY js/window))
                   (update :right  + (.-scrollX js/window))
                   (update :bottom + (.-scrollY js/window))
                   (update :left   + (.-scrollX js/window)))]
      ;;(debug pos)
      ;;(debug pos')
      pos')))
    



;; << create, delete, update bookmark events >>

;; For online mode: id "999999" argument is ignored. Note if http:// not included in url then error will occur!
;; (rf/dispatch [:dnd/create-bookmark "999999" "1" 0 "NEWBOOKMARK" "http://NEWBOOKMARKURL.COM"])
(re-frame/reg-event-db
 :dnd/create-bookmark
 (fn [db [_ id parentId index title url]]
   (if (.hasOwnProperty js/chrome "bookmarks")
     (do (chrome-create-bookmark :parentId parentId :index index :title title :url  url)
         db)
     (let [bkmrks @(re-frame/subscribe [:dnd/bookmark-atom])
           newdb (assoc-in db [:dnd/state :bookmark-atom]
                 (stub-create-bookmark bkmrks :id id :parentId parentId :index index :title title :url url))]
       (re-frame/dispatch [:dnd/synch-all-dropzones-to-folderId])
       newdb)))) 

;; only triggers "chrome.bookmarks.onRemoved called" synch-all once, because removeTree counts as 1 remove call for an entire folder
(re-frame/reg-event-fx
 :dnd/delete-drop-zone-elements
 (fn [{db :db} [_ bookmarkIdArray]]
   (if (.hasOwnProperty js/chrome "bookmarks")
     (do (.removeListener js/chrome.bookmarks.onRemoved onevent-dispatch-refresh-fnhandle)
         (doseq [bookmarkId bookmarkIdArray]
           (if (some? (:url bookmarkId))
             (.. js/chrome -bookmarks (remove (:id bookmarkId)))
             (.. js/chrome -bookmarks (removeTree (:id bookmarkId)))))
         (.addListener js/chrome.bookmarks.onRemoved onevent-dispatch-refresh-fnhandle)
         (re-frame/dispatch [:chrome-synch-all-dropzones-to-folderId {:type :updateAll}]))
     
     (loop [bkmrks @(re-frame/subscribe [:dnd/bookmark-atom]) chopBookmarkIdArray (map :id bookmarkIdArray)]
       (if (seq chopBookmarkIdArray) ;; seq used instead of (not (empty? x))
         (do
           ;; (binding [*print-level* 9 *print-length* 10 ]
           ;;   (prn ["now deleting " (first chopBookmarkIdArray) "chopBookmarkIdArray: " chopBookmarkIdArray
           ;;         "bookmarkIdArray: " bookmarkIdArray]))
           (recur (stub-removeTree-bookmark bkmrks (first chopBookmarkIdArray)) (rest chopBookmarkIdArray)))
         {:db (assoc-in db [:dnd/state :bookmark-atom] bkmrks)         
          :fx [[:dispatch [:dnd/synch-all-dropzones-to-folderId]]]})))))



(re-frame/reg-event-db
 :dnd/update-bookmark
 (fn [db [_ id title url]]
   (let [bkmrks @(re-frame/subscribe [:dnd/bookmark-atom])
         newdb (assoc-in db [:dnd/state :bookmark-atom]
                         (stub-update-bookmark bkmrks id :title title :url url))]
     (re-frame/dispatch [:dnd/synch-all-dropzones-to-folderId])
     newdb)))

;; << search related events >>


(defn find-text [searchText cljsBookmarksList dropzone-id]
  (let [accumSearchResults (reagent/atom [])
        ;; split string into vector of strings with space seperator
        ;; "full folder +alskdfalksjfhalsk" ;;=> ["full" "folder" "alskdfalksjfhalsk"]
        searchVec (remove #(clojure.string/blank? %) (clojure.string/split searchText #" "))

        ;; (zip/node (-> (map-vec-zipper mysubtree) (zip/next)  ))
        ;; => {:dateAdded 1634252090524, :id "6457", :index 0, :parentId "6450", :title "google222", :url "http://www.google222.com/"}
        zipper (map-vec-zipper cljsBookmarksList)

        find-text-inner (fn [loc]
                          (when (not (empty? searchVec)) 
                            (when (or (when (string? (:title (zip/node loc)))
                                        ;; every? Returns true if (pred x) is logical true for every x in coll, else false.
                                        ;; some Returns the first logical true value of (pred x) for any x in coll,
                                        ;; some? "Returns true if x is not nil, false otherwise" note: (some? false) is true (some? nil) is false
                                        ;; searchVec: ["full" "folder" "alskdfalksjfhalsk"]
                                        ;; clojure.string/index-of is not .indexOf: when item is not found, it returns nil, or else the 0 based index
                                        (every? some? (for [searchString searchVec]
                                                        (clojure.string/index-of (clojure.string/lower-case (:title (zip/node loc)))
                                                                                 (clojure.string/lower-case searchString)))))

                                      (when (string? (:url (zip/node loc)))
                                        ;; every? Returns true if (pred x) is logical true for every x in coll, else false.
                                        ;; some Returns the first logical true value of (pred x) for any x in coll,
                                        ;; SOME? "Returns true if x is not nil, false otherwise" note: (some? false) is true (some? nil) is false
                                        ;; searchVec: ["full" "folder" "alskdfalksjfhalsk"]
                                        ;; clojure.string/index-of is not .indexOf: when item is not found, it returns nil, or else the 0 based index
                                        (every? some? (for [searchString searchVec]
                                                        (clojure.string/index-of (clojure.string/lower-case (:url (zip/node loc)))
                                                                                 (clojure.string/lower-case searchString))))))
                              (reset! accumSearchResults (conj @accumSearchResults (zip/node loc)))))
                          (if (zip/end? loc)
                            ;; ONLY top level elements are given types not children
                            (map (fn [x] (if (nil? (:children x)) (assoc x :type :link :searchDropzone dropzone-id)
                                             (assoc x :type :folderbox :searchDropzone dropzone-id)))
                                 @accumSearchResults)
                            (recur (zip/next loc))))
        searchResult (find-text-inner zipper)]
    (cond (= 0 (count searchResult)) '(:search-not-found)
          :else searchResult)))

(re-frame/reg-event-db
 :dnd/reset-tab-history
 ;; tabs-or-history is: :tabs or :history
 (fn [db [_ tabs-or-history droppedElementList]]
   (assoc-in db [:dnd/state tabs-or-history] droppedElementList)))

(re-frame/reg-event-db
 :dnd/reset-drop-zone
 (fn [db [_ dropzoneKey droppedElementList]]
   (assoc-in db [:dnd/state :drop-zones dropzoneKey] droppedElementList)))



(defn recursive-dropzone-search [dropzone-id inputBoxValue]
  (if (.hasOwnProperty js/chrome "bookmarks")
    (let [trimmedInputBoxValue (clojure.string/trim inputBoxValue)
          folderId (dkey->fid dropzone-id)]
      (chrome-getSubTree-bookmark
       folderId
       (fn [chromeSubtree]
         ;; take first because chrome returns a vector of a single map
         (let [subtree (first (js->clj chromeSubtree :keywordize-keys true))
               droppedElementList (if (clojure.string/blank? trimmedInputBoxValue)
                                    (map (fn [x] (if (nil? (:children x)) (assoc x :type :link)
                                                     (assoc x :type :folderbox)))
                                         (:children subtree))
                                    ;; limited to the first 50 search results:
                                    (find-text trimmedInputBoxValue (:children subtree) dropzone-id))] 
           #_(println ["dropzone: " dropzone-id "inputBoxValue: " trimmedInputBoxValue 
                       (if (clojure.string/blank? trimmedInputBoxValue) " Input Box is Empty" " Input Box is not empty")])
           #_(binding [*print-level* 9 *print-length* 5 ] (println subtree))
           ;; if a dropzone is reset to '() then the component window will close and react will fail silently 
           (if (= 0 (count droppedElementList))
             (re-frame/dispatch [:dnd/reset-drop-zone dropzone-id '(:folder-has-no-children)])
             (re-frame/dispatch [:dnd/reset-drop-zone dropzone-id droppedElementList]))
           ))))
    ;;else offline 
    (let [trimmedInputBoxValue (clojure.string/trim inputBoxValue)
          folderId (dkey->fid dropzone-id)
          subtree (zip/node (find-id (map-vec-zipper @(re-frame/subscribe [:dnd/bookmark-atom])) folderId))
          droppedElementList (if (clojure.string/blank? trimmedInputBoxValue)
                               (map (fn [x] (if (nil? (:children x)) (assoc x :type :link)
                                                (assoc x :type :folderbox)))
                                    (:children subtree))
                               ;; limited to the first 50 search results:
                               (find-text trimmedInputBoxValue (:children subtree) dropzone-id))]
      ;; if a dropzone is reset to '() then the component window will close and react will fail silently 
      (if (= 0 (count droppedElementList))
        (re-frame/dispatch [:dnd/reset-drop-zone dropzone-id '(:folder-has-no-children)])
        (re-frame/dispatch [:dnd/reset-drop-zone dropzone-id droppedElementList])))))

;; find-text sets a :searchDropzone for the elements, which is not done here
;; (may really not matter since this isn't a dz target anyways), also return value is different from find-text
;; tabs-or-history is: :tabs or :history
(defn filter-tabhistory-with-searchkeys [tabs-or-history inputBoxValue]
  (let [trimmedInputBoxValue (clojure.string/trim inputBoxValue)
        vecOfKeys (clojure.string/split trimmedInputBoxValue #" ")]
    (if (= :tabs tabs-or-history)
      (.query (.-tabs js/chrome) #js {}
              (fn [tabs]
                (let [rawTabs (js->clj tabs :keywordize-keys true)
                      dropzone-elements
                      (reduce (fn [x y] (conj x {:title (:title y) :id (str (:id y)) :windowId (str (:windowId y)) :url (:url y) :type :tablink}))  []
                              rawTabs)
                      totalElements (count dropzone-elements)
                      searchResult (if (clojure.string/blank? trimmedInputBoxValue) dropzone-elements
                                       (filterv #(or (when (string? (:title %))
                                                       ;; every? Returns true if (pred x) is logical true for every x in coll, else false.
                                                       ;; some Returns the first logical true value of (pred x) for any x in coll,
                                                       ;; some? "Returns true if x is not nil, false otherwise" note: (some? false) is true (some? nil) is false
                                                       ;; vecOfKeys eg.: ["full" "folder" "alskdfalksjfhalsk"]
                                                       ;; clojure.string/index-of is not .indexOf: when item is not found, it returns nil, or else the 0 based index
                                                       (every? some? (for [searchString vecOfKeys]
                                                                       (clojure.string/index-of (clojure.string/lower-case (:title %))
                                                                                                (clojure.string/lower-case searchString)))))

                                                     (when (string? (:url %))
                                                       (every? some? (for [searchString vecOfKeys]
                                                                       (clojure.string/index-of (clojure.string/lower-case (:url %))
                                                                                                (clojure.string/lower-case searchString))))))
                                                dropzone-elements))]
                  (cond (or (= totalElements 0) (and (= totalElements 1) (= (first dropzone-elements) :folder-has-no-children)))
                        (re-frame/dispatch [:dnd/reset-tab-history tabs-or-history
                                            (list {:id "dummyid-newcol0" :type :dummy}
                                                  {:type :blank :id "blank-element1" :parentId "dropzone-empty"})])
                        (= 0 (count searchResult))
                        (re-frame/dispatch [:dnd/reset-tab-history tabs-or-history '(:search-not-found)])
                        :else (re-frame/dispatch [:dnd/reset-tab-history tabs-or-history searchResult])))))
      (let [currentUnixTime (.getTime (js/Date.))
            days @(re-frame/subscribe [:dnd/get-historyDays]) ;; current days set in history tab
            ;; milliseconds in 1 day
            startTime (- currentUnixTime (* days 86400000))]
        (.search js/chrome.history
                 #js {"text" "" "maxResults" 0 "startTime" startTime}
                 (fn [historyItems]
                   (let [rawHistoryItems (js->clj historyItems :keywordize-keys true)
                         dropzone-elements
                         (reduce
                          (fn [x y]
                            (conj x {:title (:title y) :id (str (:id y)) :url (:url y) :lastVisitTime (:lastVisitTime y) :type :historylink}))
                                 []
                                 rawHistoryItems)
                         totalElements (count dropzone-elements)
                         searchResult (if (clojure.string/blank? trimmedInputBoxValue) dropzone-elements
                                          (filterv #(or (when (string? (:title %))
                                                          ;; every? Returns true if (pred x) is logical true for every x in coll, else false.
                                                          ;; some Returns the first logical true value of (pred x) for any x in coll,
                                                          ;; some? "Returns true if x is not nil, false otherwise" note: (some? false) is true (some? nil) is false
                                                          ;; vecOfKeys eg.: ["full" "folder" "alskdfalksjfhalsk"]
                                                          ;; clojure.string/index-of is not .indexOf: when item is not found, it returns nil, or else the 0 based index
                                                          (every? some? (for [searchString vecOfKeys]
                                                                          (clojure.string/index-of (clojure.string/lower-case (:title %))
                                                                                                   (clojure.string/lower-case searchString)))))

                                                        (when (string? (:url %))
                                                          (every? some? (for [searchString vecOfKeys]
                                                                          (clojure.string/index-of (clojure.string/lower-case (:url %))
                                                                                                   (clojure.string/lower-case searchString))))))
                                                   dropzone-elements))]
                     (cond (or (= totalElements 0) (and (= totalElements 1) (= (first dropzone-elements) :folder-has-no-children)))
                        (re-frame/dispatch [:dnd/reset-tab-history tabs-or-history
                                            (list {:id "dummyid-newcol0" :type :dummy}
                                                  {:type :blank :id "blank-element1" :parentId "dropzone-empty"})])
                        (= 0 (count searchResult))
                        (re-frame/dispatch [:dnd/reset-tab-history tabs-or-history '(:search-not-found)])
                        :else (re-frame/dispatch [:dnd/reset-tab-history tabs-or-history searchResult])))))))))


;; << set state events >>

;;used for rightclicked border highlighting of elements while contextmenu is visible
(re-frame/reg-event-db
 :dnd/set-contextmenu-visible
 (fn [db [_ boolean]]
   (assoc db :contextmenuVisible boolean)))

(re-frame/reg-event-db
 :dnd/set-drag-state
 (fn [db [_ state]]
   (assoc-in db [:dnd/state :dragging] state))) 

(re-frame/reg-event-db
 :dnd/set-tabOrHistorySelected
 (fn [db [_ tabOrHistorySelected]]
   ;;tabOrHistorySelected can be :tabselected or :historyselected
   (-> db
       (assoc-in [:dnd/state :tab-history-options :tabOrHistorySelected] tabOrHistorySelected))))

(re-frame/reg-event-db
 :dnd/set-searchHistoryText
 (fn [db [_ searchHistoryText]]
   (-> db
       (assoc-in [:dnd/state :tab-history-options :searchHistoryText] searchHistoryText))))

(re-frame/reg-event-db
 :dnd/set-searchTabsText
 (fn [db [_ searchTabsText]]
   (-> db
       (assoc-in [:dnd/state :tab-history-options :searchTabsText] searchTabsText))))

(re-frame/reg-event-fx
 ;;When Dispatched By the mousedown or mouseup eventlisteners, the mouse-button status is entered into the ;;app db.
 :dnd/set-mouse-button-status
 ;;--destructuring maps is backwards {symbol :key symbol :key ...} eg.-- let [{a :a d :d} my-hashmap]
 (fn [{db :db} [_ down?]]
   {:db (assoc db :mouse-button down?)}))
    


(re-frame/reg-event-fx
 :dnd/mouse-moves
 (fn [{db :db} [_ x y]] 
   (let [db' (assoc-in db [:dnd/state :mouse-position] {:x x :y y})]
     {:db db'})))

;; << initialization events >>

(re-frame/reg-event-db
 :dnd/initialize-drop-zone
 (fn [db [_ id opts]]   
   (-> db
       (assoc-in [:dnd/state :drop-zone-options id] opts)
       (assoc-in [:dnd/state :drop-zones id] []))))

(re-frame/reg-event-db
 :dnd/initialize-tab-history-options
 (fn [db [_ opts]]
   (-> db
       (assoc-in [:dnd/state :tab-history-options] opts))))

;; usage: (rf/dispatch [:dnd/initialize-or-update-tabs (js->clj tabs :keywordize-keys true)])
(re-frame/reg-event-db
 :dnd/initialize-or-update-tabs
 (fn [db [_ tabs-vector]]
   (assoc-in db [:dnd/state :tabs] tabs-vector)))

(re-frame/reg-event-db
 :dnd/initialize-or-update-history
 (fn [db [_ history-vector days]]
   (-> db
    (assoc-in [:dnd/state :history] history-vector)
    (assoc-in [:dnd/state :tab-history-options :historyDays] days))))

(re-frame/reg-event-db
 :dnd/update-history-days
 (fn [db [_ days]]
   (-> db
    (assoc-in [:dnd/state :tab-history-options :historyDays] days))))

(re-frame/reg-event-db
 :dnd/initialize-or-update-clipboard
 ;; if rightClicked or selected omitted, they are nil
 (fn [db [_ clipboard-contents rightClicked selected]]
   (if (some? clipboard-contents) (assoc-in db [:dnd/state :clipboard] clipboard-contents)
       (-> (assoc-in db [:dnd/state :clipboard :rightClicked] rightClicked)
           (assoc-in [:dnd/state :clipboard :selected] selected)))))



;; << drop-zone-options, modifying events >>


;; prevent creation of partial dropzone option entries, which confuses :dnd/*-synch-all-dropzones-to-folderId, unless dropzone
;; has been fully initialized and :dnd/*-synch-all-dropzones-to-folderId afterwards 
(defn verify-option-entry [db dropzone-id] 
  (let [option-entry (get-in db [:dnd/state :drop-zone-options dropzone-id])]
    (apply = (conj (map #(nil? (% option-entry)) [:folderId :pinned :menuOpen :collapsedStartupToggle :cutoffDropzoneElements :selected :z-index])
                   false))))

;; modify the collapsedstartuptoggle which is initialized from embeddedmenuconfiguration on embeddedmenu initialization
;; defaults to false in floating menus. Set to booleanValue: true or false.
(re-frame/reg-event-db
 :dnd/set-collapsedStartupToggle
 ;; if tabOrHistorySelected (:tabselected or :historyselected) is omitted, fn ignores it
 (fn [db [_ dropzone-id booleanValue]]
   (if (= :tab-history dropzone-id)
     (assoc-in db [:dnd/state :tab-history-options :collapsedStartupToggle] booleanValue)
     (assoc-in db [:dnd/state :drop-zone-options dropzone-id :collapsedStartupToggle] booleanValue))))

(re-frame/reg-event-db
 :dnd/reset-cutoff-elements
 ;; if tabOrHistorySelected (:tabselected or :historyselected) is omitted, fn ignores it 
 (fn [db [_ id cutoffDropzoneElementsValue tabOrHistorySelected]]
   ;; (println ":dnd/set-menuOpen-state is being run! with arguments: (str [id boolean]) " (str [id boolean])) 
   (if (= :tab-history id)
     (let [cutoffKey (if (= :tabselected tabOrHistorySelected) :cutoffTabElements :cutoffHistoryElements)]
       (assoc-in db [:dnd/state :tab-history-options cutoffKey] cutoffDropzoneElementsValue))
     (if (verify-option-entry db id)
       (assoc-in db [:dnd/state :drop-zone-options id :cutoffDropzoneElements] cutoffDropzoneElementsValue)
       db))))

(re-frame/reg-event-db
 :dnd/set-menuOpen-state
 (fn [db [_ id boolean]]
   ;; (println ":dnd/set-menuOpen-state is being run! with arguments: (str [id boolean]) " (str [id boolean]))
   (if (verify-option-entry db id)
     (assoc-in db [:dnd/state :drop-zone-options id :menuOpen] boolean) db)))

(re-frame/reg-event-db
 :dnd/append-selected
 ;; if tabOrHistorySelected (:tabselected or :historyselected) is omitted, fn ignores it
 (fn [db [_ dropzone-id selectedFolderId tabOrHistorySelected]]
   (if (= :tab-history dropzone-id)
     (let [currentVector @(re-frame/subscribe [:dnd/get-selected :tab-history tabOrHistorySelected])]
       (assoc-in db [:dnd/state :tab-history-options tabOrHistorySelected] (conj (vec currentVector) selectedFolderId)))
     (let [currentVector @(re-frame/subscribe [:dnd/get-selected dropzone-id])]
       (assoc-in db [:dnd/state :drop-zone-options dropzone-id :selected] (conj (vec currentVector) selectedFolderId))))))

(re-frame/reg-event-db
 :dnd/remove-selected
 ;; if tabOrHistorySelected (:tabselected or :historyselected) is omitted, fn ignores it 
 (fn [db [_ dropzone-id selectedFolderId tabOrHistorySelected]]
   (if (= :tab-history dropzone-id)
     (let [currentVector @(re-frame/subscribe [:dnd/get-selected :tab-history tabOrHistorySelected])]
       (assoc-in db [:dnd/state :tab-history-options tabOrHistorySelected] (vec (remove #{selectedFolderId} currentVector))))
     (let [currentVector @(re-frame/subscribe [:dnd/get-selected dropzone-id])]
       (assoc-in db [:dnd/state :drop-zone-options dropzone-id :selected] (vec (remove #{selectedFolderId} currentVector)))))))

(re-frame/reg-event-db
 :dnd/reset-selected
 ;; if tabOrHistorySelected (:tabselected or :historyselected) is omitted, fn ignores it 
 (fn [db [_ newValue dropzone-id tabOrHistorySelected]]
   (if (= :tab-history dropzone-id)
     (let [currentVector @(re-frame/subscribe [:dnd/get-selected :tab-history tabOrHistorySelected])]
       (assoc-in db [:dnd/state :tab-history-options tabOrHistorySelected] newValue))
     (let [currentVector @(re-frame/subscribe [:dnd/get-selected dropzone-id])]
       (assoc-in db [:dnd/state :drop-zone-options dropzone-id :selected] newValue)))))

(re-frame/reg-event-db
 :dnd/set-pinned-state
 (fn [db [_ id boolean]]
   ;; (println ":dnd/set-menuOpen-state is being run! with arguments: (str [id boolean]) " (str [id boolean]))
   (if (verify-option-entry db id)
     (assoc-in db [:dnd/state :drop-zone-options id :pinned] boolean) db)))
     
(re-frame/reg-event-db
 :dnd/initialize-bookmark-atom
 (fn [db [_ bookmarks]]
   (-> db
       (assoc-in [:dnd/state :bookmark-atom] bookmarks))))

(re-frame/reg-event-db
 :dnd/update-recently-modified
 (fn [db [_ recently-modified-list]]
   (-> db
       (assoc-in [:dnd/state :recently-modified] recently-modified-list))))

(re-frame/reg-event-db
 :dnd/set-zindex
 (fn [db [_ id num]]
   (if (verify-option-entry db id)
     (assoc-in db [:dnd/state :drop-zone-options id :z-index] num) db)))
  


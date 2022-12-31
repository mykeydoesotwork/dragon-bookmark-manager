(ns dragon-bookmark-manager.dndmenu
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [re-frame.core :as rf]
   [dragon-bookmark-manager.utilities :refer [fid->dkey dkey->fid subfolder? zip-walk-closure get-all-subfolders
                                              get-all-subfolders-from-map chrome-move-bookmark onevent-dispatch-refresh-fnhandle
                                              chrome-move-bookmark-selected-wrapper chrome-getSubTree-bookmark 
                                              recursive-drop-dispatch-multiple get-dz-element zip-walk
                                              map-vec-zipper px-to-int setstyle getstyle setattrib get-property get-computed-style
                                              edge-overlay-ondrop center-overlay-ondrop delta-out delta-out2 alert-msg run-fade-alert
                                              lastZIndex gen-next-zindex obj->clj clear-all-selections-except chrome-create-bookmark
                                              chrome-update-bookmark find-id insert-child-and-reindex-array stub-create-bookmark
                                              stub-update-bookmark stub-removeTree-bookmark stub-move-bookmark destroymenu
                                              fetch-all-selected select_all_elements_at_point defaultCutoffDropzoneElements
                                              embeddedMenuConfiguration themeColor]]
   [dragon-bookmark-manager.events :as dnd]
   [dragon-bookmark-manager.subs :as dnds]
   [dragon-bookmark-manager.views :as dndv]
   [dragon-bookmark-manager.contextmenu :as dndc]
   [goog.object :as gobj]
   [cljs.reader]
   [clojure.pprint]
   [clojure.zip :as zip])
   (:require-macros [dragon-bookmark-manager.macros :as macros]))


(declare Menu)
(declare cleanup-child-dropzones)
(declare twomenus)
(declare FolderButton) 


;; +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-
;; | HOW TO GENERATE OFFLINE BOOKMARKS:
;; | FIGWHEEL MAIN BOOKMARK LOCALSTORAGE: uncomment this and open chrome exension
;; | COPY THIS FROM console.log and paste into  "otherbookmarks" localstorage of localhost:9500/admin.html (ie. figwheel)
;; | see: (def allbookmarks (reader/read-string (js/localStorage.getItem "otherbookmarks"))) below!
;; +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-
;; BOOKMARK IDs: "0" root, "1" "Bookmarks bar", "2" "Other bookmarks", "2068" test 
#_(.. js/chrome -bookmarks (getSubTree "0"
				       #(do (js/console.log "getSubTree returns:")
					    (prn (js->clj % :keywordize-keys true)))))


;; << setup events, appdb tab, history and keypress >>

(def last-id (r/atom 0))


(defonce reg-chrome-bookmark-event-listeners
  (memoize ; evaluate fn only once
   (if (.hasOwnProperty js/chrome "bookmarks")
     (fn []
       (.addListener js/chrome.bookmarks.onCreated onevent-dispatch-refresh-fnhandle)
       (.addListener js/chrome.bookmarks.onRemoved onevent-dispatch-refresh-fnhandle)
       (.addListener js/chrome.bookmarks.onChanged
                     #(do (println "chrome.bookmarks.onChanged called")
                          (rf/dispatch [:chrome-synch-all-dropzones-to-folderId {:type :updateAll}])))
       (.addListener js/chrome.bookmarks.onMoved onevent-dispatch-refresh-fnhandle)
       (.addListener js/chrome.bookmarks.onChildrenReordered
                     #(do (println "onChildrenReordered called") 
                          (rf/dispatch [:chrome-synch-all-dropzones-to-folderId {:type :updateAll}]))))
     (fn [] (do (println "reg-chrome-bookmark-event-listeners: chrome.bookmarks is undefined"))))))

(reg-chrome-bookmark-event-listeners)

(defonce reg-event-listeners 
  (memoize ; evaluate fn only once
   (fn []
     (.addEventListener js/document "mousemove" #(rf/dispatch [:dnd/mouse-moves (.-clientX %) (.-clientY %)]))
     (.addEventListener js/document "mousedown" #(rf/dispatch [:dnd/set-mouse-button-status true]))
     (.addEventListener js/document "mouseup" #(rf/dispatch [:dnd/set-mouse-button-status false]))
     (.addEventListener js/document "keydown" #(rf/dispatch [:dnd/set-keystate % true]))
     (.addEventListener js/document "keyup" #(rf/dispatch [:dnd/set-keystate % false])))))
(reg-event-listeners)

;; this event is declared here to prevent circular dependancies between utilities.cljs, events.cljs and contextmenu.cljs
(rf/reg-event-db
 :dnd/set-keystate
 (fn [db [_ event boolean]]
   ;; when no args given, fn ignores and sets event to nil, handle as initialization: 
   (cond (nil? event) (assoc db :ctrlIsDown false :shiftIsDown false)

         ;; if there is any modal dialogs present, suppress all key handling and just return db
         (some identity (map #(not= "none" (get-computed-style % "display" ))
                             (array-seq (.querySelectorAll js/document "[class~=modal-dialog]"))))
         db ;; return db and ignore keyhandling
         
         (= (.-keyCode event) 17) (assoc db :ctrlIsDown boolean)
         (= (.-keyCode event) 16) (assoc db :shiftIsDown boolean)

         (and (= (.-keyCode event) 65) (.-ctrlKey event)) (do (select_all_elements_at_point) db)

         ;; true? boolean ensures only run once on keydown not on keyup ;; esc (27)
         (and (true? boolean) (= (.-keyCode event) 27))
         (do (rf/dispatch [:dnd/shortcut-esc]) db)
         
         ;; true? boolean ensures only run once on keydown not on keyup ;; ctrl-c (67) copy
         (and (true? boolean) (= (.-keyCode event) 67) (.-ctrlKey event))
         (do (rf/dispatch [:dnd/mouseover-copyToClipboard]) db)

         ;; true? boolean ensures only run once on keydown not on keyup ;; ctrl-x (88) cut
         (and (true? boolean) (= (.-keyCode event) 88) (.-ctrlKey event))
         (do (rf/dispatch [:dnd/mouseover-cutToClipboard]) db)

         ;; true? boolean ensures only run once on keydown not on keyup ;; del (46)
         (and (true? boolean) (= (.-keyCode event) 46))
         (do (rf/dispatch [:dnd/shortcut-confirmDelete]) db)

         ;; true? boolean ensures only run once on keydown not on keyup ;; ctrl-v (86)
         (and (true? boolean) (= (.-keyCode event) 86) (.-ctrlKey event))
         (do (rf/dispatch [:dnd/mouseover-pasteFromClipboard]) db)

         
         ;; true? boolean ensures only run once on keydown not on keyup ;; F2 (113) rename
         (and (true? boolean) (= (.-keyCode event) 113))
         (do (dndc/rename-mouseover) db)

         ;; if keycode is not recognized you must return db, or else cond will return nil and
         ;; db will be destroyed and set to nil 
         :else db)))

;; onCreated, onRemoved, onMove (within window), onAttached (between windows)
(declare create-tabs-key)
(defonce reg-chrome-tab-event-listeners
  (memoize ; evaluate fn only once
   (if (.hasOwnProperty js/chrome "tabs")
     (fn []
       (.addListener js/chrome.tabs.onCreated #(create-tabs-key))
       (.addListener js/chrome.tabs.onRemoved #(create-tabs-key))
       (.addListener js/chrome.tabs.onMoved   #(create-tabs-key))
       (.addListener js/chrome.tabs.onAttached #(create-tabs-key))
       (.addListener js/chrome.tabs.onUpdated #(create-tabs-key)))
     (fn [] (do (println "reg-chrome-tab-event-listeners: chrome.bookmarks is undefined"))))))
(reg-chrome-tab-event-listeners)

(declare create-history-key)
;; onVisitRemoved, onVisited
(defonce reg-chrome-history-event-listeners
  (memoize ; evaluate fn only once 
   (if (.hasOwnProperty js/chrome "history")
     (fn []
       (.addListener js/chrome.history.onVisited #(create-history-key @(rf/subscribe [:dnd/get-historyDays])))
       (.addListener js/chrome.history.onVisitRemoved #(create-history-key @(rf/subscribe [:dnd/get-historyDays]))))
     (fn [] (do (println "reg-chrome-history-event-listeners: chrome.bookmarks is undefined"))))))
(reg-chrome-history-event-listeners)

;;initialize keypress entries in the database
(rf/dispatch [:dnd/set-keystate])

;;testing global event click listner
(.addEventListener js/document "click" #(do (dndc/hide-menu dndc/link-context-menu dndc/link-move-submenu-container)
                                            (dndc/hide-menu dndc/folder-context-menu dndc/folder-move-submenu-container)))


;; otherbookmarks and bookmarksbar are both strings manually put in chrome local storage for the page http://localhost:9500/admin.html
;; but otherbookmarks has all the bookmarks so the db is just initialized to it, bookmarksbar is kept in case a smaller db is needed
(def allbookmarks (cljs.reader/read-string (js/localStorage.getItem "otherbookmarks"))) ;; id is 2 parentid is 0 and already contains bkmrksbar id 1


;; initialize the bookmark atom to allbookmarks which contains the bookmarks bar within it
;;(rf/dispatch [:dnd/initialize-bookmark-atom bkmrks])
(rf/dispatch [:dnd/initialize-bookmark-atom allbookmarks])

;; initialize the tabs key to currently open tabs
(defn create-tabs-key []
  (when (.hasOwnProperty js/chrome "bookmarks")
    (let [searchTabsText @(rf/subscribe [:dnd/get-searchTabsText])]
      (if (clojure.string/blank? searchTabsText)
        (.query (.-tabs js/chrome) #js {}
              (fn [tabs]
                (rf/dispatch [:dnd/initialize-or-update-tabs (js->clj tabs :keywordize-keys true)])))
        (dnd/filter-tabhistory-with-searchkeys :tabs searchTabsText)))))
(create-tabs-key)

(defn create-history-key [days]
  (when (.hasOwnProperty js/chrome "bookmarks")
    (let [searchHistoryText @(rf/subscribe [:dnd/get-searchHistoryText])
          currentUnixTime (.getTime (js/Date.))
          ;; milliseconds in 1 day
          startTime (- currentUnixTime (* days 86400000))]
      (if (clojure.string/blank? searchHistoryText)
        (.search js/chrome.history
                 #js {"text" "" "maxResults" 0 "startTime" startTime}
                 (fn [historyItems]
                   (rf/dispatch [:dnd/initialize-or-update-history (js->clj historyItems :keywordize-keys true) days])))
        (dnd/filter-tabhistory-with-searchkeys :history searchHistoryText)))))
(create-history-key 1)

;; initialize the clipboard
(rf/dispatch [:dnd/initialize-or-update-clipboard {:rightClicked nil :selected nil}])



;; << dragging menus >>


(def dragfunc (r/atom nil))
(def enddragfunc (r/atom nil))
(def menuhasmoved? (r/atom false))
(def resizefunc (r/atom nil))
(def endresizefunc (r/atom nil))

(def mouseposition (rf/subscribe [:dnd/mouse-position]))

;; disables link hovering
(defn freeze-all-dropzones []
  (let [dropzonesFound (.querySelectorAll js/document "[class^=drop-zone]")] 
    (dotimes [i (.-length dropzonesFound)]
      (.add (.-classList (gobj/get dropzonesFound i)) "disable-pointer-events"))))

(defn unfreeze-all-dropzones []
  (let [dropzonesFound (.querySelectorAll js/document "[class^=drop-zone]")] 
    (dotimes [i (.-length dropzonesFound)]
      (.remove (.-classList (gobj/get dropzonesFound i)) "disable-pointer-events"))))

;; disables menu hovering
(defn freeze-menus [dropzone-id]
  (let [dropzoneElement (.querySelector js/document (str "#drop-zone-" (name dropzone-id)))
        menusFound (.querySelectorAll js/document ".Menu,.MenuTitleBar") ;; remove this
        menuElement (.-parentNode dropzoneElement)
        menuTitleBar (.querySelector menuElement "div.MenuTitleBar")
        menuArrowBtnNodeList (.querySelectorAll menuTitleBar ".MenuArrowBtn")
        menuPinBtn (.querySelector menuTitleBar "div.MenuPinBtn")]
    (.add (.-classList menuElement) "frozen-menu")
    (.add (.-classList menuTitleBar) "frozen-menutitlebar")
    (.forEach menuArrowBtnNodeList #(.add (.-classList %) "frozen-MenuArrowBtn"))
    (.add (.-classList menuPinBtn) "frozen-menupinbtn"))) 

(defn unfreeze-menus [dropzone-id]
  (let [dropzoneElement (.querySelector js/document (str "#drop-zone-" (name dropzone-id)))
        menusFound (.querySelectorAll js/document ".Menu,.MenuTitleBar") ;; remove this
        menuElement (.-parentNode dropzoneElement)
        menuTitleBar (.querySelector menuElement "div.MenuTitleBar")
        menuArrowBtnNodeList (.querySelectorAll menuTitleBar ".MenuArrowBtn")
        menuPinBtn (.querySelector menuTitleBar "div.MenuPinBtn")]
    (.remove (.-classList menuElement) "frozen-menu")
    (.remove (.-classList menuTitleBar) "frozen-menutitlebar")
    (.forEach menuArrowBtnNodeList #(.remove (.-classList %) "frozen-MenuArrowBtn"))
    (.remove (.-classList menuPinBtn) "frozen-menupinbtn")))

;; This sets the top and left style attributes using relative to parent offsetTop/Left element attributes and mouse deltas
;; bound to document wide mousemove eventlistener by dragMouseDown which is fired on-mouse-down in div.MenuTitleBar
(defn elementDrag [dropzone-id offsetTopClick offsetLeftClick] 
  (fn [mmEvent]
    (let [dropzoneElement (.querySelector js/document (str "#drop-zone-" (name dropzone-id)))
          menuElement (.-parentNode dropzoneElement)
          oldTop (getstyle menuElement "top")
          oldLeft (getstyle menuElement "left")
          newTop (str  (max 0 (- (:y @mouseposition) offsetTopClick)) "px")
          newLeft (str  (- (:x @mouseposition) offsetLeftClick) "px")]
      (when dropzoneElement ;; if the dropzone hasn't been destroyed
        ;; (setstyle menuElement "top"   (str (- (gobj/get menuElement "offsetTop") (:y @mouseposition)) "px"))
        ;; (setstyle menuElement "left"  (str (- (gobj/get menuElement "offsetLeft") (:x @mouseposition)) "px"))
        (setstyle menuElement "top" newTop)
        (setstyle menuElement "left" newLeft)
        (if (or (not= oldTop newTop) (not= oldLeft newLeft)) (reset! menuhasmoved? true))))))

(defn endElementDrag [dropzone-id]  
  (fn [e]
    (unfreeze-all-dropzones) 
    (unfreeze-menus dropzone-id)
    (js/document.removeEventListener "mouseup" @enddragfunc false)
    (js/document.removeEventListener "mousemove" @dragfunc false)

    #_(println "endElementDrag closed! " )))


;; bound to :on-mouse-down in dndmenu.org :div.MenuTitleBar ie. the menu header
(defn dragMouseDown [e dropzone-id] 
  ;; Solution to: Warning: This synthetic event is reused for performance reasons. If you're seeing this, you're accessing the property 'target'
  ;; on a released/nullified synthetic event. This is set to null. If you must keep the original synthetic event around, use event.persist().
  ;;from: htps://www.duncanleung.com/blog/2017-08-14-fixing-react-warnings-synthetic-events-in-setstate/
  ;;(.persist e)
  (.preventDefault e) 
  ;; set z-index of menu
  (rf/dispatch [ :dnd/set-zindex dropzone-id (gen-next-zindex) ])

  (let [dropzoneElement (.querySelector js/document (str "#drop-zone-" (name dropzone-id)))
        menuElement (.-parentNode dropzoneElement)
        offsetTopClick (- (:y @mouseposition) (px-to-int (get-computed-style menuElement "top")) )
        offsetLeftClick (- (:x @mouseposition) (px-to-int (get-computed-style menuElement "left")))]
    (freeze-all-dropzones)
    (freeze-menus dropzone-id)
    (reset! dragfunc  (elementDrag dropzone-id offsetTopClick offsetLeftClick))) 

  (reset! enddragfunc (endElementDrag dropzone-id))

  (js/document.addEventListener "mouseup" @enddragfunc false)
  (js/document.addEventListener "mousemove" @dragfunc false))


(defn elementResize [dropzone-id menuPos dropzonePos border]
  (fn [mmEvent]
    (let [dropzoneElement (.querySelector js/document (str "#drop-zone-" (name dropzone-id)))
          menuElement (.-parentNode dropzoneElement)
          
          ;;getstyle (fn [e x] (gobj/get (gobj/get e "style") x)) 
	  minWidth (get-computed-style dropzoneElement "min-width")
	  minHeight (get-computed-style dropzoneElement "min-height")]


      (when dropzoneElement ;; if the dropzone hasn't been destroyed
        (case border ;;note symbols in a case body must not be quoted
          ;; 40px is titlebar height, 13px is 4px + 4px border + 5px padding
	  rBorder (setstyle dropzoneElement "width" (str (- (:x @mouseposition) (:left menuPos) ) "px") ) 
	  lBorder (do (setstyle dropzoneElement "width" (str (- (:right menuPos) (:x @mouseposition) 13)  "px") )
                      (setstyle menuElement "left" (str (min (- (:right menuPos) (px-to-int minWidth) 13) (:x @mouseposition)) "px") ))
	  bBorder (setstyle dropzoneElement "height" (str (- (:y @mouseposition) (:top dropzonePos) ) "px") )
	  tBorder (do (setstyle dropzoneElement "height" (str (- (:bottom menuPos) (max 0 (:y @mouseposition)) 53) "px"))
		      #_(setstyle menuElement "top" (str (max 0
                                                        (min  (:y @mouseposition) (- (:bottom menuPos) (px-to-int minHeight) 53 ))) "px"))

                      (setstyle menuElement "top" (str "max(" (str (:bottom menuPos)) "px - 80vh - 53px, "
                                                       (min  (:y @mouseposition) (- (:bottom menuPos) (px-to-int minHeight) 53 ))
                                                       "px)")))
	  :else)))))

 

(defn endResize [dropzone-id element maintainCurrentHeight restoreMinHeight border]  
  (fn [e]
 
    (let [setstyle (fn [e x y] (gobj/set (gobj/get e  "style") x y))]
      (case border ;;note symbols in a case body must not be quoted
        (lBorder rBorder) 
	(do 
                  ;; in case width is set below minimum width reset it to auto, also snaps to column width, set to non-auto after corner pulltab resize
                  ;; (setstyle element "width" "auto") ;; omitted because snapping prevents partial columns display which is more useful than snapping

                  ;; prevents collapsing to elements contained height after mouse-up if the element hasn't been resized at least once.
                  (setstyle element "height" maintainCurrentHeight) 
                  ;; (setstyle element "height" "auto") ;; collapse height to contained elements omitted because jarring 
                  ;; restore the minimum height which was changed to maintain height during resizing
                  (setstyle element "min-height" restoreMinHeight))
        :else ))
    (unfreeze-all-dropzones) 
    (unfreeze-menus dropzone-id)
    (js/document.removeEventListener "mouseup" @endresizefunc false)
    (js/document.removeEventListener "mousemove" @resizefunc false)))


(defn resizeMouseDown [e dropzone-id border] 
  ;; (.persist e)
  (.preventDefault e)
  
  (let [dropzoneElement (.querySelector js/document (str "#drop-zone-" (name dropzone-id)))
        dropzoneRect (.getBoundingClientRect dropzoneElement)
	dropzonePos  {:top  (.-top dropzoneRect) :left (.-left dropzoneRect) :bottom (.-bottom dropzoneRect) :right (.-right dropzoneRect)} 

        savedMinHeight (get-computed-style dropzoneElement "min-height")
        maintainCurrentHeight (get-computed-style dropzoneElement "height" )

	menuElement (.-parentNode dropzoneElement)
        menuRect (.getBoundingClientRect menuElement)
	menuPos  {:top  (.-top menuRect) :left (.-left menuRect) :bottom (.-bottom menuRect) :right (.-right menuRect)}]

    ;; set z-index of menu
    (rf/dispatch [ :dnd/set-zindex dropzone-id (gen-next-zindex) ])

    (case border ;;note symbols in a case body must not be quoted
      ;; Ensures height is maintained during dragging. The original min-height is restored in endResize.
      (lBorder rBorder) (do (setstyle dropzoneElement "min-height" maintainCurrentHeight))
      :else )
    (freeze-all-dropzones)
    (freeze-menus dropzone-id)

    ;; using an atom because endResize returns a closure
    (reset! endresizefunc (endResize dropzone-id dropzoneElement maintainCurrentHeight savedMinHeight border)) 
    (js/document.addEventListener "mouseup" @endresizefunc false)
    
    (reset! resizefunc (elementResize dropzone-id menuPos dropzonePos border)) 
    (js/document.addEventListener "mousemove" @resizefunc false)))

;; << menu synchronization functions >>

(declare appended-container)
(rf/reg-event-fx
 :chrome-synch-all-dropzones-to-folderId-with-menu
 (fn [{db :db} [_ menu-xyposition menu-dimensions show-containing-folder-element-id dropzone-id]]
   (let [folderId (:folderId @(rf/subscribe [:dnd/dragdrop-options dropzone-id]))] 

     (chrome-getSubTree-bookmark 
      folderId 
      (fn [subtree]
        (let [cljsSubtree (first (js->clj subtree :keywordize-keys true))
              title  (:title cljsSubtree)
              _ (with-out-str  (binding [*print-level* 9 *print-length* 5 ] (clojure.pprint/pprint cljsSubtree)))
              children (if-let [children (:children cljsSubtree)] children 
                               (throw {:type :custom-arg-error :message (str "folder-id-elements: folderId " 
                                                                             folderId " has nil :children")}))
              ;; map over the children and specify type :link or :folderbox depending on if :children is nil or not
              returnVal (map (fn [x] (if (nil? (:children x)) (assoc x :type :link) 
                                         (assoc x :type :folderbox  ))) 
                             children)]
          (rf/dispatch [:synch-one-dropzone-to-folderContents dropzone-id
                        (if (empty? returnVal) '(:folder-has-no-children) returnVal) ])
          (rf/dispatch [:dnd/set-menuOpen-state dropzone-id true])
          (rf/dispatch [ :dnd/set-zindex dropzone-id (gen-next-zindex) ])
          (rdom/render [Menu title menu-xyposition menu-dimensions show-containing-folder-element-id dropzone-id]
                       (appended-container (.getElementById js/document "my-panel") dropzone-id)))))
     {:db db})))



;; for each ... folders in dropzone options ... : 
;; chrome-getSubtree the folder 
;; (... for each dropzone, callback of chrome-getSubtree dispatches :synch-one-dropzone-to-folderContents
;;      which synchs the dropzone to the arguments of the callback ...)
;; dropzoneUpdateMap is {:type :updateAll} or {:type :updateSome :updateDzs updateDzs }
(rf/reg-event-fx
 :chrome-synch-all-dropzones-to-folderId
 (fn [{db :db} [_ dropzoneUpdateMap] ] 
   (let [dzFolderIdPairs (if (or (nil? dropzoneUpdateMap) (= (:type dropzoneUpdateMap) :updateAll))
                           (for [[x y] (get-in db [:dnd/state :drop-zone-options])] [x (:folderId y)])
                           (for [x (:updateDzs dropzoneUpdateMap)] [x (dkey->fid x)]))]

     (doseq [[dropzoneId folderId] dzFolderIdPairs]

       (let [dz dropzoneId
             dz-found (.getElementById js/document (str "drop-zone-" (name dz)))
             parentMenuOfDropzone (when dz-found (.-parentNode dz-found))
             searchInput (when parentMenuOfDropzone (.querySelector parentMenuOfDropzone "#searchBox"))
             searchValue (when searchInput (get-property searchInput "value"))]

         ;; if searchValue is not nil or blank, run a search on the dropzone using searchValue
         (if (not (clojure.string/blank? searchValue))
           (dnd/recursive-dropzone-search dropzoneId searchValue)
           ;; else the searchValue is nil (because searchInput does not exist) or blank, then synch dropzone
           (chrome-getSubTree-bookmark 
            folderId 
            ;; callback function is a closure enclosed by doseq [dropzoneId folderId]
            (fn [subtree]
              (let [cljsSubtree (first (js->clj subtree :keywordize-keys true))
                    children (if-let [children (:children cljsSubtree)] children 
                                     (throw {:type :custom-arg-error :message (str "folder-id-elements: folderId " 
                                                                                   folderId " has nil :children")}))
                    ;; map over the children and specify type :link or :folderbox depending on if :children is nil or not
                    returnVal (map (fn [x] (if (nil? (:children x)) (assoc x :type :link) 
                                               (assoc x :type :folderbox   ))) 
                                   children)]
                ;; (println "this is from callback, subtree is: " (js->clj subtree :keywordize-keys true))
                ;; (println "this is from callback, returnVal is: " returnVal)
                ;; (println "calling (rf/dispatch [:synch-one-dropzone-to-folderContents dropzoneId returnVal ])" dropzoneId returnVal)
                (rf/dispatch [:synch-one-dropzone-to-folderContents dropzoneId
                              (if (empty? returnVal) '(:folder-has-no-children) returnVal) ])))))))
     ;; (println "end running chrome-synch-all-dropzones-to-folderId")
     {:db db})))




(rf/reg-event-db
 :synch-one-dropzone-to-folderContents
 (fn [db [_ dropzoneId folderContents]]
   (assoc-in db [:dnd/state :drop-zones dropzoneId] folderContents)))

;; loops and extracts the folderId out of existing dropzones in :drop-zone-options of the appdb and populates them with 
;; their respective folderIds
(rf/reg-event-db
 :dnd/synch-all-dropzones-to-folderId-with-menu
 (fn [db [_ title menu-xyposition menu-dimensions show-containing-folder-element-id dropzone-id]]
   (let [;; synch-dropzone-to-folderId : a dropzone -> a new database
         ;;_ (println "running synch-all-dropzones-to-folderId!")
         synch-dropzone-to-folderId 
         (fn  [dz-id newdb]
           (let [;; get the folder id from the dropzone id
                 folderId (:folderId @(rf/subscribe [:dnd/dragdrop-options dz-id]))
                 ;;_ (prn "the folderId is: " folderId) 
                 ;; If offline, then fetch children from the bookmark atom, if online fetch using chrome api
                 bookmark-atom (get-in db [:dnd/state :bookmark-atom])
                 nodeIdFound (zip/node (find-id (map-vec-zipper bookmark-atom) folderId)) ;; will throw an error if the folder doesn't exist
                 children (if-let [children (:children nodeIdFound)] children 
                                  (throw {:type :custom-arg-error :message (str "folder-id-elements: folderId " 
                                                                                folderId " has nil :children")}))
                 ;; map over the children and specify type :link or :folderbox depending on if :children is nil or not
                 returnVal (map (fn [x] (if (nil? (:children x)) (assoc x :type :link) 
                                            (assoc x :type :folderbox  ))) 
                                children)]
             ;;["nodeIdfound"  nodeIdFound "children" children "returnVal" returnVal "vector argument" folderId] 
             ;;(js/console.log (str returnVal))
             (assoc-in newdb [:dnd/state :drop-zones dz-id] (if (empty? returnVal) '(:folder-has-no-children)
                                                                returnVal))))
         ;; first get all the drop zones into a list 
         ;; (prn (str (keys (get-in db [:dnd/state :drop-zone-options]))))
         newdb (loop [dz-list (keys (get-in db [:dnd/state :drop-zone-options])) newdb db]
                 (if (seq dz-list)
                   (do #_(prn ["inside recur loop: [(first dz-list) (rest dz-list)] " (first dz-list) (rest dz-list) ])
                       (recur (rest dz-list) 
                              (synch-dropzone-to-folderId (first dz-list) newdb)))
                   newdb))]
     (rf/dispatch [:dnd/set-menuOpen-state dropzone-id true])
     (rf/dispatch [:dnd/set-zindex dropzone-id (gen-next-zindex) ])
     (rdom/render [Menu title menu-xyposition menu-dimensions show-containing-folder-element-id dropzone-id]
                     (appended-container (.getElementById js/document "my-panel") dropzone-id))
     newdb)))

(rf/reg-event-db
 :dnd/synch-all-dropzones-to-folderId
 (fn [db _]
   (let [;; synch-dropzone-to-folderId : a dropzone -> a new database
         _ (println "running synch-all-dropzones-to-folderId!")
         synch-dropzone-to-folderId 
         (fn  [dz-id newdb]
           (let [;; get the folder id from the dropzone id
                 folderId (:folderId @(rf/subscribe [:dnd/dragdrop-options dz-id]))
                 ;;_ (prn "the folderId is: " folderId) 
                 ;; If offline, then fetch children from the bookmark atom, if online fetch using chrome api
                 bookmark-atom (get-in db [:dnd/state :bookmark-atom])
                 nodeIdFound (zip/node (find-id (map-vec-zipper bookmark-atom) folderId)) ;; will throw an error if the folder doesn't exist
                 children (if-let [children (:children nodeIdFound)] children 
                                  (throw {:type :custom-arg-error :message (str "folder-id-elements: folderId " 
                                                                                folderId " has nil :children")}))
                 ;; map over the children and specify type :link or :folderbox depending on if :children is nil or not
                 returnVal (map (fn [x] (if (nil? (:children x)) (assoc x :type :link) 
                                            (assoc x :type :folderbox ))) 
                                children)]
             ;;["nodeIdfound"  nodeIdFound "children" children "returnVal" returnVal "vector argument" folderId] 
             ;;(js/console.log (str returnVal))
             (assoc-in newdb [:dnd/state :drop-zones dz-id] (if (empty? returnVal) '(:folder-has-no-children)
                                                                returnVal))))
         
         ;; for each dropzone:
         ;; 1. cond searchInput is nil or value is blank then synch the dropzone
         ;; 2. cond it has a searchInput and it is not empty then skip it and call dnd/recursive-dropzone-search
         newdb (loop [dz-list (keys (get-in db [:dnd/state :drop-zone-options])) newdb db]
                 (if (seq dz-list)
                   (let [dz (first dz-list)
                         dz-found (.getElementById js/document (str "drop-zone-" (name dz)))
                         parentMenuOfDropzone (when dz-found (.-parentNode dz-found))
                         searchInput (when parentMenuOfDropzone (.querySelector parentMenuOfDropzone "#searchBox"))
                         searchValue (when searchInput (get-property searchInput "value"))]
                     (if (not (clojure.string/blank? searchValue))
                       (do (dnd/recursive-dropzone-search (first dz-list) searchValue) (recur (rest dz-list) newdb))
                       (recur (rest dz-list) (synch-dropzone-to-folderId (first dz-list) newdb))))
                   newdb))]
     newdb)))






;; << EmbeddedMenuTabs, EmbeddedMenu, Menu, drop-zone-tabs >>


;; function: update-recently-modified: using offline bookmarkatom, or online getsubtree callback function, computes recently modified
;; event:    then dispatches an event :dnd/update-recently-modified, offline, or in the callback online, to set the appdb entry for :recently-modified
;; subs:     @(rf/subscribe [:dnd/get-recently-modified]) fetches the appdb entry [:dnd/state :recently-modified]

;; online (dateadded is always unchanged): when a folder has an element deleted, or moved out of the folder, dategroupmodified is not updated.
;; online (dateadded is always unchanged): when a folder is renamed or a link/folder within is renamed, dategroupmodified is not updated.
;; online (dateadded is always unchanged): when adding a new element, or moving an element into a folder, dategroupmodified updates
;; online (dateadded is always unchanged): when moving an element within a folder, dategroupmodified updates
(defn fetch-recently-modified [rawTree]
  (let [flattenBookmarksTree
        (loop [subtreeZipper (zip/next (map-vec-zipper rawTree))  accumNodes '()]
          (if (zip/end? subtreeZipper) accumNodes
              (recur (zip/next subtreeZipper) (let [node (zip/node subtreeZipper)]
                                                (if (not (:url node))
                                                  (let [dateGroupModified
                                                        (if-let [dateGroupModified (:dateGroupModified node)] dateGroupModified 0)
                                                        dateAdded
                                                        (if-let [dateAdded (:dateAdded node)] dateAdded 0)
                                                        modifiedDate (max dateGroupModified dateAdded)]
                                                    (concat accumNodes (list {:id (:id node) :title (:title node) :modified modifiedDate})))
                                                  accumNodes
                                                  )))))]
    (sort-by :modified > flattenBookmarksTree)))

(defn update-recently-modified
  [cutoff]
  (if (.hasOwnProperty js/chrome "bookmarks")
    (.. js/chrome -bookmarks (getSubTree "0" (fn [x]
                                               (rf/dispatch [:dnd/update-recently-modified
                                                             (remove #(= (:id %) "0")
                                                                     (take cutoff (fetch-recently-modified (js->clj x :keywordize-keys true))))]))))
    (rf/dispatch-sync [:dnd/update-recently-modified
                       (remove #(= (:id %) "0")
                               (take cutoff (fetch-recently-modified (first @(rf/subscribe [:dnd/bookmark-atom])))))])))


;; there is no dropzone-id argument because the id is set to (str "drop-zone-tab-history") 
(defn drop-zone-tabs
  [embedded? changeColumns selectTabHistory searchTabsText searchHistoryText]
  (let [generate-tabs
        (fn [n selectTabHistory]
          (if (= selectTabHistory :tabselected)
            (for [x (range n)]
              {:id (str (+ 9000000000 x)) :windowId (str (+ 900000 x))  :title "DuckDuckGo - Privacy, simplified." :url "https://duckduckgo.com/"
               :type :tablink})
            (for [x (range n)]
              {:id (str (+ 9000000000 (* 10 x))) :title "Google History" :url "https://google.com/" :type :historylink})))

        colorList ["Aqua" "red" "Lime" "Yellow" "Magenta" "NavajoWhite"]

        allDroppedElements (if (.hasOwnProperty js/chrome "bookmarks")
                             @(rf/subscribe [:dnd/get-tabs])
                             (generate-tabs 3 selectTabHistory))

        cutoffDropzoneElements (rf/subscribe [:dnd/get-cutoff-elements :tab-history selectTabHistory])

        dropped-elements (take @cutoffDropzoneElements allDroppedElements)

        s (r/atom {}) ;; hold on to a dom reference to the button using reagent :ref attribute below

        
        ;; embeddedMenuConfiguration
        ;; [{:optionClass "tabOption", :show true, :startCollapsed true, :defaultColumns 4, :minRows 103, :maxRows 109}
        ;;  {:optionClass "historyOption", :show true, :startCollapsed false, :defaultColumns 4, :minRows 85, :maxRows 89}
        ;;:dropzone-1 is index 2:  {:optionClass "barOption", :show true, :startCollapsed true, :defaultColumns 5, :minRows 55, :maxRows 69}
        ;;:dropzone-2 is index 3:  {:optionClass "otherOption", :show false, :startCollapsed false, :defaultColumns 5, :minRows 34, :maxRows 54}]
        storedArrayIndex (if (= selectTabHistory :tabselected) 0 1)
        possibleColumnsEmbedded (r/atom (max (min (:defaultColumns (nth embeddedMenuConfiguration storedArrayIndex)) 8) 1))
        minRows (max (min (:minRows (nth embeddedMenuConfiguration storedArrayIndex)) 999) 1)
        maxRows (max (min (:maxRows (nth embeddedMenuConfiguration storedArrayIndex)) 999) 1)

        
        next-biggest-num (fn [possibleColumnsEmbeddedArray x] (if-let [nextBiggestFound (first (filter #(< x %) possibleColumnsEmbeddedArray))]
                                                                nextBiggestFound (last possibleColumnsEmbeddedArray)))
        prev-smallest-num (fn [possibleColumnsEmbeddedArray x] (if-let [prevSmallestFound (last (filter #(< % x) possibleColumnsEmbeddedArray))]
                                                                 prevSmallestFound (first possibleColumnsEmbeddedArray)))

        cRows-fn (fn [totalElements possibleColumnsEmbedded] (js/Math.ceil (max (/ totalElements possibleColumnsEmbedded) 1)))]
    
    (fn [embedded? changeColumns selectTabHistory searchTabsText searchHistoryText]
      (let [allDroppedElements (if (.hasOwnProperty js/chrome "bookmarks")
                                 (if (= selectTabHistory :tabselected)
                                   @(rf/subscribe [:dnd/get-tabs])
                                   @(rf/subscribe [:dnd/get-history]))
                                 (generate-tabs 10 selectTabHistory))

            cutoffDropzoneElements (rf/subscribe [:dnd/get-cutoff-elements :tab-history selectTabHistory])

            dropped-elements (take @cutoffDropzoneElements allDroppedElements)

            windowIdVec (when (= :tabselected selectTabHistory) (vec (distinct (map #(:windowId %) dropped-elements))))
            colorToWindowIdMap (reduce (fn [x y] (assoc x (first y) (second y) )) {}
                                       (map-indexed (fn [x y] [(keyword y) (nth colorList (mod x (count colorList)) )]) windowIdVec))

            ;; embeddedMenuConfiguration
            ;; [{:optionClass "tabOption", :show true, :startCollapsed true, :defaultColumns 4, :minRows 103, :maxRows 109}
            ;;  {:optionClass "historyOption", :show true, :startCollapsed false, :defaultColumns 4, :minRows 85, :maxRows 89}
            ;;:dropzone-1 is index 2:  {:optionClass "barOption", :show true, :startCollapsed true, :defaultColumns 5, :minRows 55, :maxRows 69}
            ;;:dropzone-2 is index 3:  {:optionClass "otherOption", :show false, :startCollapsed false, :defaultColumns 5, :minRows 34, :maxRows 54}]
            ;; repeated from above inside update fn because unlike in views.cljs drop-zone selectTabHistory will change, while id will not
            storedArrayIndex (if (= selectTabHistory :tabselected) 0 1)
            ;;possibleColumnsEmbedded (r/atom (max (min (:defaultColumns (nth embeddedMenuConfiguration storedArrayIndex)) 8) 1))
            minRows (max (min (:minRows (nth embeddedMenuConfiguration storedArrayIndex)) 999) 1)
            maxRows (max (min (:maxRows (nth embeddedMenuConfiguration storedArrayIndex)) 999) 1)

            ;; the "start collapsed?" option for tabs is used for history with storedArrayIndex 0
            collapsedStartupToggle (rf/subscribe [:dnd/get-collapsedStartupToggle :tab-history])

            totalElements (if (> (count allDroppedElements) @cutoffDropzoneElements)
                            (inc (count dropped-elements)) (count dropped-elements)) 
            
            possibleColumnsEmbeddedArray (reduce (fn [x y] (conj x (first y))) []
                                                 (vals (group-by (partial cRows-fn totalElements) [1 2 3 4 5 6 7 8])))

            ;; The thirdOfScreen for dropped-elements is 0.3, thirdOfScreenPadding is 0.32 to provide empty 2% padding with the
            ;; scrollbar. for 1440px monitor 30% is 432px 32% is 461px, so 29px of padding 
            thirdOfScreenPadding (int (* (.-innerWidth js/window) 0.32))  ;; padding of 2% for scrollbar
            computedStyle (when (:componentRef @s) (.getComputedStyle js/window (:componentRef @s) nil))
            padding (when computedStyle ( + (js/parseFloat (.-paddingLeft computedStyle)) (js/parseFloat (.-paddingRight computedStyle))))
            scrollbar (when (:componentRef @s) (- (.-offsetWidth (:componentRef @s) )
                                                  (.-clientWidth (:componentRef @s) )))
            ;; element width
            stringWidth (when computedStyle (.-width computedStyle)) ;; eg// returns "1296px"
            intWidth (js/parseInt (when stringWidth (subs stringWidth 0 (- (count stringWidth) 2)))) ;; returns 1296
            
            ;; intWidth - 30 for floating dropzone causes 30px resizable before possibleColumnsFloating is incremented
            possibleColumnsFloating (js/Math.ceil (max (/ (- intWidth 30) thirdOfScreenPadding) 1)) 

            ;; the initial value of possiblecolumnsembedded is set before possiblecolumnsembeddedarray is created
            ;; therefore it is is possible the current state of possiblecolumnsembedded is not in the array, in which case
            ;; call the next-biggest-num or prev-smallest-num function twice, or else the first click will do nothing
            _ (cond (= @changeColumns :increase) (do (reset! changeColumns :steady)
                                                     (if (nat-int? (.indexOf possibleColumnsEmbeddedArray @possibleColumnsEmbedded))
                                                       (swap! possibleColumnsEmbedded
                                                              (partial next-biggest-num possibleColumnsEmbeddedArray))
                                                       (swap! possibleColumnsEmbedded
                                                              #(next-biggest-num possibleColumnsEmbeddedArray
                                                                                 (next-biggest-num possibleColumnsEmbeddedArray %))))
                                                     (setstyle (:componentRef @s) "height" "auto"))
                    
                    (= @changeColumns :decrease) (do (reset! changeColumns :steady)
                                                     (if (nat-int? (.indexOf possibleColumnsEmbeddedArray @possibleColumnsEmbedded))
                                                       (swap! possibleColumnsEmbedded
                                                              (partial prev-smallest-num possibleColumnsEmbeddedArray))
                                                       (swap! possibleColumnsEmbedded
                                                              #(prev-smallest-num possibleColumnsEmbeddedArray
                                                                                  (prev-smallest-num possibleColumnsEmbeddedArray %))))
                                                     (setstyle (:componentRef @s) "height" "auto"))
                    :else nil)

            ;; this decreases with every possiblecolumnsembedded increase even if number of actual columns is unchanged
            embeddedWidgetWidth (int (/ (- intWidth padding scrollbar) (min totalElements @possibleColumnsEmbedded)) )

            ;; ceil ensures the last col will never be full
            cRows (if embedded? (js/Math.ceil (max (/ totalElements @possibleColumnsEmbedded) 1)) ;; difference is in possible columns 
                      (js/Math.ceil (max (/ totalElements possibleColumnsFloating) 1)))
            
            gridElements ;; gridElements: alternate dummy with dropped-elements, but add two dummy elements for every new column
            ;; Failsafe checks if not loaded yet, but this will have been checked in filter-tabhistory-with-searchkeys anyways with a
            ;; :blank type dropped-elements list returned anyways
            ;; Case (and (= totalElements 1) (= (first dropzone-elements) :folder-has-no-children)) is not handled because no folder
            ;; exists in tabs or history
            ;; NB: intermediate processing is done in [:dnd/get-tabs] and [:dnd/get-history]
            (cond (= totalElements 0) (do #_(prn ["from drop-zone-tabs: branch 1: dropped-elements" dropped-elements])
                                          (list {:id "dummyid-newcol0" :type :dummy}
                                                {:type :blank :id "blank-element1" :parentId "dropzone-empty"}))

                  ;; NB: intermediate processing is done in [:dnd/get-tabs] and [:dnd/get-history]
                  (and (= totalElements 1) (= (first dropped-elements) :search-not-found)) 
                  (when (:componentRef @s)
                    #_(prn ["from drop-zone-tabs: branch 2: dropped-elements" dropped-elements])
                    (list {:type :search-not-found :id "search-not-found-id"
                           :searchText (if (= selectTabHistory :tabselected) @searchTabsText @searchHistoryText)
                           :searchBoxRef (.querySelector (.-parentNode (:componentRef @s)) "#search")
                           :parentId "search-not-found-parent-id"}))
                  ;; NB: intermediate processing is done in [:dnd/get-tabs] and [:dnd/get-history]
                  :else
                  (let [alternating-elts (flatten (for [x dropped-elements] (list x {:id (str "dummyid" (:id x)) :type :dummy})))
                        part-result (partition-all (* 2 cRows) alternating-elts)
                        part-result-withlastcol
                        ;; mark the last element in every column to set it's bottom overlay to extend to the bottom of the 
                        ;; dropzone in the widget
                        (map (fn [part] 
                               (let [[before after] (split-at (dec (count part)) part)]
                                 (concat (butlast before) (list (assoc (last before) :last-in-col true)) after)))  part-result)]

                    ;;(split-at 2 [1 2 3 4 5]) ;;=> [(1 2) (3 4 5)]
                    ;;(let [[before after] (split-at 3 [1 2 3 4 5 6])] (vec (concat before ['a 'b 'c] after))) ;; => [1 2 3 a b c 4 5 6]
                    (if (> (count allDroppedElements) @cutoffDropzoneElements)
                      ;; if each column has equal number of elements and there is more than one columnt, then the loadmorebutton starts a new column, 
                      ;; and should have an extra "dummyid-newcol" element preceding it, or not otherwise.
                      ;; in case cutoff is to display only 1 element then a new col for loadmore must be started
                      (if (or (= 1 @cutoffDropzoneElements) 
                              (and (> (count part-result-withlastcol) 1) (apply = (map count part-result-withlastcol))))                          
                        (concat
                         (flatten (map-indexed (fn [idx itm] (concat (list {:id (str "dummyid-newcol" idx) :type :dummy} ) itm))
                                               part-result-withlastcol))
                         [{:id (str "dummyid-newcol" (count part-result-withlastcol) ) :type :dummy}
                          {:type :loadmorebutton :id :tab-history :countAll (count allDroppedElements)}])
                        (concat
                         (flatten (map-indexed (fn [idx itm] (concat (list {:id (str "dummyid-newcol" idx) :type :dummy} ) itm))
                                               part-result-withlastcol))
                         [{:type :loadmorebutton :id :tab-history :countAll (count allDroppedElements)}]))

                      ;; if there is no cutoff necessary then don't add a loadmore button
                      (flatten (map-indexed (fn [idx itm] (concat (list {:id (str "dummyid-newcol" idx) :type :dummy} ) itm))
                                            part-result-withlastcol)))))]
        [:div
         {;; DANGER WITHOUT "when" to guard: :ref #(swap! s assoc :componentRef %) ;; will cause infinite updates!
          ;; :ref (fn [el] (when el (swap! s assoc :componentRef el)))
          ;; :ref #(swap! s assoc :componentRef %)
          :ref (fn [el] (when el (swap! s assoc :componentRef el))) 
          :draggable "false"  
          :id        (str "drop-zone-tab-history") 
          :class (if embedded? "drop-zone-embedded" "drop-zone-floating") 
          ;; this line only executes in embeddedmenutabs, :dropzone-1 and :dropzone-2 not floating dropzones where it is always grid
          ;; the purpose of this line is to ensure the embedded dropzone collapses to nothing "block" with height 0 when no elements exist
          ;; that way if there is no history or no tabs (impossible) you don't have to deal with special case of :blank as target dropzone
          :style {:display (cond (and embedded? @collapsedStartupToggle) "none"
                                 (or (= totalElements 0) (and (= totalElements 1) (= (first dropped-elements) :search-not-found))) "block"
                                 :else "grid")
                  :grid-template-rows (str "repeat("  cRows ", 4px 28px) 4px")
                  :width (when (not embedded?) thirdOfScreenPadding) 
                  :min-width (when (not embedded?) thirdOfScreenPadding)
                  ;; :max-height : is 9 rows or : 297 = 9 * (32=28+4) + 4 + 5
                  ;; :min-height : if unset, an empty embeddedmenu will take on the minimum height of a :blank element which is 4px dummy
                  ;; 28px dropmarker 4px lastincol = 36 pixels + 5 pixels dropzone-embedded bottom padding = 41 pixels
                  ;; :min-height (if embedded? "41px" (/ thirdOfScreenPadding 4))
                  ;; :max-height "297px"}
                  :min-height (if embedded? (str (+ (* minRows 32) 9) "px") (/ thirdOfScreenPadding 4))
                  :max-height (when embedded? (str (+ (* maxRows 32) 9) "px"))}


          
          :on-context-menu (fn [e] (.preventDefault e)) ;; disable chrome's context menu

          :on-click #(let [ targetClass (.-className (.-target %))]
                       (cond (= targetClass "drop-zone-embedded") (clear-all-selections-except)
                             (= targetClass "drop-zone-floating") (clear-all-selections-except)
                             (= targetClass "dummy-element") (clear-all-selections-except)))} 
         (map (fn [de]
                ;; Danger if ^{:key de} is identical in tabs as well as history, then the history element will have the same color
                ;; as the tab element. selecttabhistory ensures the same de in tab or history will be differentiated.
                ;; Using (gen-key) causes infinite refresh because the the update fn changes on each refresh.
                ;; Do not use @ derefs in meta data for react components will cause error:
                ;; Warning: Reactive deref not supported in lazy seq, it should be wrapped in doall: [...]
                ^{:key [de selectTabHistory]}
                ;;^{:key de}
                [dndv/dropped-widget de embedded? embeddedWidgetWidth s (if (:last-in-col de) true false)
                 (if-let [keyColor (keyword (:windowId de))]  (keyColor colorToWindowIdMap) "white") ])
              gridElements)]))))


;; (set-row-height-dropzone ...) ;; was only called in embeddedmenutabs and embeddedmenu, until the up and down chevrons were removed:
;; [:div.EmbeddedMenuButton {:style {:justify-self "end" :display "flex" :align-items "center" :justify-content "center"
;;                                            :padding-right "10px" :padding-left "10px"} :on-click (set-row-height-dropzone s :decrease)}
;;           [:img {:src "images/up-chevron.png" :style {:vertical-align "middle"} }]]

;; [:div.EmbeddedMenuButton {:style {:justify-self "end" :display "flex" :align-items "center" :justify-content "center"
;;                                   :padding-right "10px" :padding-left "10px"} :on-click (set-row-height-dropzone s :increase)}
;;  [:img {:src "images/down-chevron.png" :style {:vertical-align "middle"} }]]
(defn set-row-height-dropzone [s direction]
  (fn [e]
    (when (:componentRef @s)
      (let [EmbeddedMenuTabs (:componentRef @s)
            dropzoneOfEmbeddedMenu (.querySelector (:componentRef @s) ".drop-zone-embedded")
            gridTemplateRows (get-computed-style dropzoneOfEmbeddedMenu "grid-template-rows")
            maxelement (apply max (map #(js/parseFloat %)  (clojure.string/split gridTemplateRows " ")))
            minelement (apply min (map #(js/parseFloat %) (clojure.string/split gridTemplateRows " ")))
            currentHeight (- (js/parseFloat (get-computed-style dropzoneOfEmbeddedMenu "height" )) minelement)
            desiredrows (if (= direction :increase)
                          (inc (int (/ currentHeight (+ 28 4) )))
                          (max 1 (dec (int (/ currentHeight (+ 28 4) )))))
            newheight  (+ minelement (* desiredrows (+ minelement maxelement)))] 
        (setstyle dropzoneOfEmbeddedMenu "height" (str newheight "px"))))))

(defn isSearchNotFound? [dropzone-id]
  (case dropzone-id
    :tabselected (= :search-not-found (first @(rf/subscribe [:dnd/get-tabs])))
    :historyselected (= :search-not-found (first @(rf/subscribe [:dnd/get-history])))
    ;; default case
    (= :search-not-found (first @(rf/subscribe [:dnd/dropped-elements dropzone-id]) ))))

(defn collapse-dropzone [s dropzone-id]
  ;; if there are no elements or some elements, then ".drop-zone-embedded" has display: "grid".
  ;;         with no elements there are two elements in the grid: a dummy element and an absolutely positioned blank-element
  ;; if :search-not-found is in effect, then ".drop-zone-embedded" has display: "block".
  ;; and in appdb :dnd/state -> :drop-zones {:dropzone-1 (:serach-not-found), :dropzone-2 ({:children ... :dateAdded ... :id ....}) ....}
  (when (:componentRef @s)
    (let [dropzoneOfEmbeddedMenu (.querySelector (:componentRef @s) ".drop-zone-embedded")
          displayState (get-computed-style dropzoneOfEmbeddedMenu "display")]
      (prn ["from collapse-dropzone: displayState: " displayState])
      (cond (#{"grid" "block"} displayState) (setstyle dropzoneOfEmbeddedMenu "display" "none")
            (= "none" displayState) (if (isSearchNotFound? dropzone-id) (setstyle dropzoneOfEmbeddedMenu "display" "block")
                                        (setstyle dropzoneOfEmbeddedMenu "display" "grid"))
            :else (setstyle dropzoneOfEmbeddedMenu "display" "grid")))))


(defn recently-modified-dropdown []
  (let [_ (update-recently-modified 20) ;; initialize upon first load 20 MAGIC ref 1/2
        recently-modified-list (rf/subscribe [:dnd/get-recently-modified])
        highlight (r/atom false)
        s (r/atom {})]
    (fn []  
      [:select.RecentlyModified
       {;; DANGER WITHOUT "when" to guard: :ref #(swap! s assoc :componentRef %) ;; will cause infinite updates!
        ;; :ref (fn [el] (when el (swap! s assoc :componentRef el)))
        ;; :ref #(swap! s assoc :componentRef %)
        :ref (fn [el] (when el (swap! s assoc :componentRef el))) 
        
        :on-mouse-over #(reset! highlight true)
        :on-mouse-out #(reset! highlight false)
        :on-click #(do (update-recently-modified 20)) ;; update whenever clicked 20 MAGIC ref 2/2
        :on-change #(do (dndc/show-folder-wrapper (.-value (:componentRef @s)))
                        (setattrib (:componentRef @s) "selectedIndex" 0))
        :style {:background-color (if @highlight "#ff9100" "#840a01" )}}
       ;; nb: there are no events for options because they are handled by the os
       (conj (for [x @recently-modified-list] ^{:key (:id x)}
               [:option {:value (:id x)}
                (let [recent-element (:title x)]
                  (if (> (count recent-element) 100) (str (subs recent-element 0 100) "...") recent-element) )] )
             ^{:key -9999} [:option {:value "" :style {:display "none"}} "Recently modified"])])))


(defn EmbeddedMenuTabs [] ;; Title is ignored 
  (let [;; Initialize and populate the dropzones 
        _ (rf/dispatch [:dnd/initialize-tab-history-options {:cutoffTabElements defaultCutoffDropzoneElements
                                                             :cutoffHistoryElements defaultCutoffDropzoneElements
                                                             :collapsedStartupToggle (:startCollapsed (nth embeddedMenuConfiguration 0))
                                                             :searchTabsText ""
                                                             :searchHistoryText ""
                                                             :historyDays 1
                                                             :tabOrHistorySelected :tabselected
                                                             :tabselected []
                                                             :historyselected []}])
        changeColumns (r/atom :steady)
        selectTabHistory (rf/subscribe [:dnd/get-tabOrHistorySelected]) 
        searchTabsText (rf/subscribe [:dnd/get-searchTabsText])
        searchHistoryText (rf/subscribe [:dnd/get-searchHistoryText])
        plusButtonImage (r/atom "images/circle-plus.png")
        minusButtonImage (r/atom "images/circle-minus.png")
        historyDays (rf/subscribe [:dnd/get-historyDays]) ;; (r/atom 1)
        underline (r/atom false)
        s (r/atom {})]

    (fn []
      [:div {:id (str "menu-tab-history")
             ;; DANGER WITHOUT "when" to guard: :ref #(swap! s assoc :componentRef %) ;; will cause infinite updates!
             ;; :ref (fn [el] (when el (swap! s assoc :componentRef el)))
             ;; :ref #(swap! s assoc :componentRef %)
             :ref (fn [el] (when el (swap! s assoc :componentRef el))) 

             :style {:background-color (if themeColor "#222222" "black") :border "2px solid white" :width "90vw" :margin "auto" }}

       [:div {:style {:height "40px" :border-bottom "2px solid white" :cursor "default" :user-select "none" 
                      :background-color "#af0404" 
                      :display "grid" :grid-template-columns "2fr 3fr 3fr 1fr"}} 

        [:div {:style {:display "flex"}}
         [:div.EmbeddedMenuButton {:title "Fewer Columns"
                                   :style {:justify-self "end" :display "flex" :align-items "center" :justify-content "center"
                                           :padding-right "10px" :padding-left "10px"} :on-click #(reset! changeColumns :decrease)}
          [:img {:src "images/left-chevron.png" :style {:vertical-align "middle"} }]]
         
         [:div.EmbeddedMenuButton {:title "More Columns"
                                   :style {:justify-self "end" :display "flex" :align-items "center" :justify-content "center"
                                           :padding-right "10px" :padding-left "10px"} :on-click #(reset! changeColumns :increase)}
          [:img {:src "images/right-chevron.png"  }]]
         [recently-modified-dropdown]]

        [:div#tabhistory {:style {:display "flex" :justify-self "center" }}
         [:div#tabbtn {:on-click #(do (rf/dispatch [:dnd/set-tabOrHistorySelected :tabselected])
                                      (rf/dispatch [:dnd/reset-selected [] :tab-history :historyselected])
                                      (reset! underline false)
                                      (when (:componentRef @s)
                                        (setattrib (.querySelector (:componentRef @s) "#searchBox") "value" @searchTabsText)))
                       :style {:padding-right "50px" :padding-left "50px" :margin-right "1.4vw" :margin-top "2px"
                               :color (when @selectTabHistory (@selectTabHistory {:tabselected "black" :historyselected "white"}))
                               :background-color (when @selectTabHistory (@selectTabHistory {:tabselected "#ff9100" :historyselected "#840a01"}))
                               :border-width "2px" :border-color "black" :border-radius "15px 15px 0px 0px" 
                               :border-left-style "solid" :border-right-style  "solid" :border-top-style "solid" }}
          [:div {:style {:display "inline-block" :height "100%" :vertical-align "middle" }}] "Tabs"]

         [:div#historybtn {:on-click #(do (rf/dispatch [:dnd/set-tabOrHistorySelected :historyselected]) 
                                          (rf/dispatch [:dnd/reset-selected [] :tab-history :tabselected])
                                          (when (:componentRef @s)
                                            (setattrib (.querySelector (:componentRef @s) "#searchBox") "value" @searchHistoryText)))
                           :style {:padding-right "1.4vw" :padding-left "1.4vw" :margin-left "1.4vw" :margin-top "2px"
                                   :color (when @selectTabHistory (@selectTabHistory {:tabselected "white" :historyselected "black"}))
                                   :background-color (when @selectTabHistory (@selectTabHistory {:tabselected "#840a01" :historyselected "#ff9100"}))
                                   :border-width "2px"  :border-color "black" :border-radius "15px 15px 0px 0px" 
                                   :border-left-style "solid" :border-right-style  "solid" :border-top-style "solid"
                                   :display "grid" :grid-template-columns "min-content auto min-content"}}
          [:div.PlusMinusButton {:style {:justify-self "start" :display "flex" :align-items "center" :justify-content "center"
                                         :padding-right "10px" :padding-left "10px"}
                                 :title (when (= @selectTabHistory :historyselected) "Reduce 1 Day")
                                 :on-mouse-over #(when (= @selectTabHistory :historyselected)
                                                   (reset! minusButtonImage "images/circle-minus-hover.png"))
                                 :on-mouse-out #(when (= @selectTabHistory :historyselected)
                                                  (reset! minusButtonImage "images/circle-minus.png"))
                                 :on-click #(when (= @selectTabHistory :historyselected)
                                              (rf/dispatch-sync [:dnd/update-history-days (max 1 (dec @historyDays))])
                                              ;; filter-tabhistory-with-searchkeys will now take into account the new appdb history days
                                              (dnd/filter-tabhistory-with-searchkeys :history @searchHistoryText))}
           [:img {:src @minusButtonImage :style {:vertical-align "middle"} }]]
          
          
          [:span {:style {:justify-self "center" :white-space "nowrap"}
                  :title (when (= @selectTabHistory :historyselected) "Reset Days")
                  :on-mouse-over #(when (= @selectTabHistory :historyselected) (reset! underline true))
                  :on-mouse-out #(when (= @selectTabHistory :historyselected) (reset! underline false))
                  :on-click #(when (= @selectTabHistory :historyselected) (rf/dispatch-sync [:dnd/update-history-days 1])
                                   ;; filter-tabhistory-with-searchkeys will now take into account the new appdb history days
                                   (dnd/filter-tabhistory-with-searchkeys :history @searchHistoryText))}
           [:div {:style {:display "inline-block" :height "100%" :vertical-align "middle"}}]
           (if @underline [:u {:style {:color "darkblue"}} (str "History: " @historyDays " days")] (str "History: " @historyDays " days"))]

          [:div.PlusMinusButton {:style {:justify-self "end" :display "flex" :align-items "center" :justify-content "center"
                                         :padding-right "10px" :padding-left "10px"}
                                 :title (when (= @selectTabHistory :historyselected) "Add 1 Day")
                                 :on-mouse-over #(when (= @selectTabHistory :historyselected)
                                                   (reset! plusButtonImage "images/circle-plus-hover.png"))
                                 :on-mouse-out #(when (= @selectTabHistory :historyselected)
                                                  (reset! plusButtonImage "images/circle-plus.png"))
                                 :on-click #(when (= @selectTabHistory :historyselected)
                                              (rf/dispatch-sync [:dnd/update-history-days (inc @historyDays)])
                                              ;; filter-tabhistory-with-searchkeys will now take into account the new appdb history days
                                              (dnd/filter-tabhistory-with-searchkeys :history @searchHistoryText))}
           [:img {:src @plusButtonImage :style {:vertical-align "middle"} }]]]] 

        [:div#search {:on-mouse-up #(when (:componentRef @s) (let [searchInput (.querySelector (:componentRef @s) "#searchBox")]
                                                               (if (= @selectTabHistory :tabselected)
                                                                 (do (rf/dispatch-sync [:dnd/set-searchTabsText ""])
                                                                     (dnd/filter-tabhistory-with-searchkeys :tabs ""))
                                                                 (do (rf/dispatch-sync [:dnd/set-searchHistoryText ""])
                                                                     (dnd/filter-tabhistory-with-searchkeys :history "")))
                                                               (setattrib searchInput  "value" "")
                                                               (.focus searchInput)))
                      :title "Clear Search"
                      :style {:display "flex" :justify-self "center" }}  
         [:div {:style {:justify-self "start" :display "flex" :align-items "center" :justify-content "center"
                        :padding-right "10px" :padding-left "15px" }}
          [:img {:src "images/magnifying-glass.png" :style {:vertical-align "middle"} }]]
         
         [:div {:style {:justify-self "center" :display "flex" :align-items "center" :justify-content "center"
                        :padding-right "30px" }}
          [:input#searchBox {:type "search"
                             :title ""
                             :on-mouse-up #(.stopPropagation %) ;; if enabled click on inputbox does NOT clear
                             :on-change #(let [inputBoxValue (-> % .-target .-value)]
                                           (if (= @selectTabHistory :tabselected)
                                             (do (rf/dispatch-sync [:dnd/set-searchTabsText inputBoxValue])
                                                 (dnd/filter-tabhistory-with-searchkeys :tabs inputBoxValue))
                                             (do (rf/dispatch-sync [:dnd/set-searchHistoryText inputBoxValue])
                                                 (dnd/filter-tabhistory-with-searchkeys :history inputBoxValue)))
                                           (clear-all-selections-except))
                             
                             :style {:width "21vw" :background-color "wheat" :border-radius "5px"}  }]]]
        

        [:div {:style {:display "flex" :justify-content "flex-end"}}
         [:div.EmbeddedMenuButton {:title "Options"
                                   :style {:justify-self "end" :display "flex" :align-items "center" :justify-content "center"
                                           :padding-right "10px" :padding-left "10px"}
                                   :on-click (fn [] (.openOptionsPage (.-runtime js/chrome)))} 
          [:img {:src  "images/gear-option.png" }]] 
         [:div.EmbeddedMenuButton {:title "Help"
                                   :style {:justify-self "end" :display "flex" :align-items "center" :justify-content "center"
                                           :padding-right "10px" :padding-left "10px"}
                                   :on-click (fn [] (.setVisible dndc/helpDialog true))} 
          [:img {:src "images/information.png" }]]
         [:div.EmbeddedMenuButton {:title "Collapse"
                                   :style {:justify-self "end" :display "flex" :align-items "center" :justify-content "center"
                                           :padding-right "10px" :padding-left "10px"}
                                   :on-click (fn [] (rf/dispatch [:dnd/set-collapsedStartupToggle :tab-history false])
                                               (collapse-dropzone s @selectTabHistory))} 
          [:img {:src "images/minus.png" }]]]] 
       
       (when @selectTabHistory
         [drop-zone-tabs true changeColumns @selectTabHistory searchTabsText searchHistoryText])])))



(declare set-row-height-dropzone)
(declare collapse-dropzone)
(defn EmbeddedMenu [title dropzone-id folderId]
  (let [;; Initialize and populate the dropzones
        _ (rf/dispatch [:dnd/initialize-drop-zone 
                        dropzone-id
                        ;; options
                        {:folderId folderId
                         :pinned false
                         :menuOpen true
                         :collapsedStartupToggle (:startCollapsed (nth embeddedMenuConfiguration (if (= dropzone-id :dropzone-1) 2 3)))
                         :cutoffDropzoneElements defaultCutoffDropzoneElements
                         :selected []
                         :z-index 1}])
        
        _ (try (if (.hasOwnProperty js/chrome "bookmarks") (rf/dispatch [:chrome-synch-all-dropzones-to-folderId {:type :updateAll}])
                   (rf/dispatch [:dnd/synch-all-dropzones-to-folderId]))
               ;; tested error => Error Occured:  {:type :custom-arg-error, :message find-id: id was not found}
               (catch :default e (println "Error Occured: " e)))
        changeColumns (r/atom :steady)
        searchText (r/atom "")
        s (r/atom {})]

    (fn []
      [:div {:id (str "menu-" (name dropzone-id))
             ;; DANGER WITHOUT "when" to guard: :ref #(swap! s assoc :componentRef %) ;; will cause infinite updates!
             ;; :ref (fn [el] (when el (swap! s assoc :componentRef el)))
             ;; :ref #(swap! s assoc :componentRef %)
             :ref (fn [el] (when el (swap! s assoc :componentRef el))) 

             :style {:background-color (if themeColor "#222222" "black") :border "2px solid white" :width "90vw" :margin "auto" }}

       [:div {:style {:height "40px" :border-bottom "2px solid white" :cursor "default" :user-select "none" 
                      :background-color "#af0404" 
                      :display "grid" :grid-template-columns "2fr 3fr 3fr 1fr"}} 


        [:div {:style {:display "flex"  }}
         [:div.EmbeddedMenuButton {:title "Fewer Columns" :style {:display "flex" :align-items "center" :justify-content "center" 
                                           :padding-right "10px" :padding-left "10px"} :on-click #(reset! changeColumns :decrease)}
          [:img {:src "images/left-chevron.png" :style {:vertical-align "middle"} }]]

         [:div.EmbeddedMenuButton {:title "More Columns" :style {:display "flex" :align-items "center" :justify-content "center"
                                           :padding-right "10px" :padding-left "10px"} :on-click #(reset! changeColumns :increase)}
          [:img {:src "images/right-chevron.png"  }]]]

        
        [:div {:style {:justify-self "center" :align-self "center"}} title]

        [:div#search {:on-mouse-up #(when (:componentRef @s) (let [searchInput (.querySelector (:componentRef @s) "#searchBox")]
                                                               (dnd/recursive-dropzone-search dropzone-id "")
                                                               (setattrib searchInput  "value" "")
                                                               (reset! searchText "")
                                                               (.focus searchInput)
                                                               (rf/dispatch [:dnd/reset-cutoff-elements dropzone-id
                                                                             defaultCutoffDropzoneElements])
                                                               ;; selections are cleared because selected subfolder elements may no longer be visible
                                                               (clear-all-selections-except)))
                      :title "Clear Search"
                      :style {:display "flex" :justify-self "center" }}  
         [:div {:style {:justify-self "start" :display "flex" :align-items "center" :justify-content "center"
                        :padding-right "10px" :padding-left "15px" }}
          [:img {:src "images/magnifying-glass.png" :style {:vertical-align "middle"} }]]
         
         [:div {:style {:justify-self "center" :display "flex" :align-items "center" :justify-content "center"
                        :padding-right "30px" }}
          [:input#searchBox {:type "search"
                             :title ""
                             ;; if #(.stopPropagation %) is :on-mouse-up handler, clicking on inputbox does NOT clear
                             :on-mouse-up #(.stopPropagation %)
                             :on-change #(let [inputBoxValue (-> % .-target .-value)]
                                           (reset! searchText inputBoxValue)
                                           (dnd/recursive-dropzone-search dropzone-id inputBoxValue)
                                           (clear-all-selections-except))
                             
                             :style {:width "21vw" :background-color "wheat" :border-radius "5px"}  }]]]

        [:div.EmbeddedMenuButton {:title "Collapse"
                                  :style {:justify-self "end" :display "flex" :align-items "center" :justify-content "center"
                                          :padding-right "10px" :padding-left "10px"}
                                  :on-click (fn [] (rf/dispatch [:dnd/set-collapsedStartupToggle dropzone-id false])
                                              (collapse-dropzone s dropzone-id))}
         [:img {:src "images/minus.png" }]]]

       ;; Ensure :cutoffDropzoneElements defaultCutoffDropzoneElements exists before dropzone is mounted
       (when @(rf/subscribe [:dnd/dragdrop-options dropzone-id])
         [dndv/drop-zone dropzone-id true changeColumns nil nil nil searchText false])])))



(defn dblclick-border [s border]
  (fn [e]
    (when (:componentRef @s)
      (let [dropzone (.querySelector (:componentRef @s) ".drop-zone-floating")]
        (case border
          (lBorder rBorder) (gobj/set (gobj/get dropzone "style") "width" "auto")
          (tBorder bBorder) (gobj/set (gobj/get dropzone "style") "height" "auto")
          :else)))))

(def TITLETEXT_WIDTH_PERCENTAGE 0.3)

(defn dblclick-menutitlebar [s]
  (fn [e] 
    (when (:componentRef @s)
      (let [dropzone (.querySelector (:componentRef @s) ".drop-zone-floating")
            displayState (gobj/get (gobj/get dropzone "style") "display")
            savedTitleBarWidth (get-computed-style (.-parentNode dropzone) "width")]
        (if (= displayState "grid") 
          (do (gobj/set (gobj/get dropzone "style") "display" "none")
              (gobj/set (gobj/get (.-parentNode dropzone) "style") "width" savedTitleBarWidth))
	  ;; display is "none"
          (do (gobj/set (gobj/get dropzone "style") "display" "grid")
              ;; if you don't set the MenuTitleBar width back to auto, the title bar width will stay fixed while you resize
              ;; the dropzone width, which may then appear outside the menu box:
              (gobj/set (gobj/get (.-parentNode dropzone) "style") "width" "auto")
              ;; After dropzone display is set to none, it's width becomes auto, and is lost between double clicks
              ;; since I don't want to explicitly save it, I will just use savedTitleBarWidth instead. Menu width is auto
              ;; now so it should adjust if they are not equal.
              (gobj/set (gobj/get dropzone "style") "width" savedTitleBarWidth)))))))



(defn toggle-image [ratom-image dropzone-id]
  (if (= @ratom-image "images/unlock.png")
    (do (reset! ratom-image "images/lock.png" )
        (rf/dispatch [:dnd/set-pinned-state dropzone-id true]))
    (do (reset! ratom-image "images/unlock.png" )
        (rf/dispatch [:dnd/set-pinned-state dropzone-id false]))))

;; dropzone-id comes from folderid
;; this handles window overflow repositioning, if no overflow, then just uses positionState as set by folderbutton, and shows the menu
;defn set-menu-position-closure [dropzone-id positionState visibilityState -- true or false boolean value --]                                
(defn set-menu-position-closure [dropzone-id positionState dimensions visibilityState show-containing-folder-element-id]
  (fn [comp]
    (let [dropzone-reference (rdom/dom-node comp) ;; r/dom-node: react-class -> HTMLElement 
          menu-reference (.-parentNode dropzone-reference) ]
      ;;; if visible do nothing
      (when (= @visibilityState "hidden")
        (prn ["from set-menu-position-closure: [dropzone-id @positionState @visibilityState show-containing-folder-element-id] "
                  [dropzone-id @positionState @visibilityState show-containing-folder-element-id]])
        (let [menuBoundingRectangle (.getBoundingClientRect menu-reference) 
              countNodeList (.-length (.querySelectorAll dropzone-reference ".link-element, .folderbox-element, .blank-element"))]
          ;; if countNodeList is 0 do nothing ;; countNodeList is unused
          (let [my-panelReference (.getElementById js/document "my-panel") ;; my-panelReference is unused
                folderbutton (if show-containing-folder-element-id
                               (.getElementById js/document (str "dropped-element-" show-containing-folder-element-id))
                               (.getElementById js/document (str "dropped-element-" (dkey->fid dropzone-id))))]
            ;; If folderbutton is nil, this is because show-folder was not called and has not set show-containing-folder-element-id, when
            ;; showing a parent folder. And (str "dropped-element-" (dkey->fid dropzone-id)) is also nil because a folderbutton was not
            ;; clicked to trigger calling set-menu-position-closure. Instead show-folder-wrapper must have been called from
            ;; recently-modified-dropdown attempting to show a folder which is not currently visible as an html element. In this case ignore
            ;; x-overflow and just check for y-overflow (which is based on the menucomponent only not any bookmark element) and show the
            ;; folder with positionState set by show-folder-wrapper to a explicitly safe value.  ie. check for vertical overflow and (reset!
            ;; visibilityState "visible") below.
            ;; do not set x direction if dimensions are set by restore-view
            (when (and folderbutton (not dimensions))
              ;; if overflow in x direction subtract (+ menuWidth folderbuttonWidth) from "left" of menu
              (let [folderbuttonBoundingRectangle (.getBoundingClientRect folderbutton)
                    menuLeftViewport (.-left menuBoundingRectangle)
                    menuLeft (px-to-int (get-computed-style menu-reference "left"))
                    ;;(.-offsetWidth menu-reference) returns wrong values for large titlebars 
                    menuOffsetWidth (+ 8 (.-offsetWidth dropzone-reference)) ;; 8 is for 4px border on either side of dropzone
                    menuViewportDisplacement (- (.-left menuBoundingRectangle) (.-left folderbuttonBoundingRectangle))
                    viewportWidth (.-width (.-visualViewport js/window))]
                (when (< viewportWidth (+ menuLeftViewport menuOffsetWidth)) 
                  ;;(println "overflow-x has occured")
                  ;;(setstyle menu-reference "left" (str (- menuLeft menuOffsetWidth menuViewportDisplacement) "px"))
                  (reset! positionState [(first @positionState) (max 0 (- menuLeft menuOffsetWidth menuViewportDisplacement))]))))
            ;; do not set y direction if dimensions are set by restor -view
            (when (not dimensions)
             (let [menuTop (px-to-int (get-computed-style menu-reference "top"))
                   menuVPTop (.-top (.getBoundingClientRect menu-reference))
                   menuOffsetHeight (.-offsetHeight menu-reference)
                   viewportHeight (.-height (.-visualViewport js/window))
                   verticalOverflowAmount (- (.-bottom menuBoundingRectangle) viewportHeight)]
               (when (< viewportHeight (+ menuVPTop menuOffsetHeight))
                 ;;(println "overflow-y has occured")
                 ;; 20 is random number to check for why double overflow is occuring
                 ;;(setstyle menu-reference "top" (str (- menuTop verticalOverflowAmount 20)  "px"))
                 (reset! positionState [(- menuTop verticalOverflowAmount 20) (second @positionState)])))))
          ;; if dimensions were passed then set them. dimensions look like ["600px" "400px"]
          (when dimensions
           (setstyle dropzone-reference "width" (first dimensions))
           (setstyle dropzone-reference "height" (second dimensions)))
          ;; if scrollbars intermittently appear before visible is set there will be a placement error gap
          ;;(setstyle menu-reference "visibility" "visible")
          (reset! visibilityState "visible"))))))

#_(defn set-menu-position-closure--oldver-dec15-2022 [dropzone-id positionState visibilityState show-containing-folder-element-id]
  (fn [comp]
    (let [dropzone-reference (rdom/dom-node comp) ;; r/dom-node: react-class -> HTMLElement 
          menu-reference (.-parentNode dropzone-reference) ]
      ;;; if visible do nothing
      (when (= @visibilityState "hidden")
        (prn ["from set-menu-position-closure: [dropzone-id @positionState @visibilityState show-containing-folder-element-id] "
                  [dropzone-id @positionState @visibilityState show-containing-folder-element-id]])
        (let [menuBoundingRectangle (.getBoundingClientRect menu-reference) 
              countNodeList (.-length (.querySelectorAll dropzone-reference ".link-element, .folderbox-element, .blank-element"))]
          ;; if countNodeList is 0 do nothing ;; countNodeList is unused
          (let [my-panelReference (.getElementById js/document "my-panel") ;; my-panelReference is unused
                folderbutton (if show-containing-folder-element-id
                               (.getElementById js/document (str "dropped-element-" show-containing-folder-element-id))
                               (.getElementById js/document (str "dropped-element-" (dkey->fid dropzone-id))))]
            ;; If folderbutton is nil, this is because show-folder was not called and has not set show-containing-folder-element-id, when
            ;; showing a parent folder. And (str "dropped-element-" (dkey->fid dropzone-id)) is also nil because a folderbutton was not
            ;; clicked to trigger calling set-menu-position-closure. Instead show-folder-wrapper must have been called from
            ;; recently-modified-dropdown attempting to show a folder which is not currently visible as an html element. In this case ignore
            ;; x-overflow and just check for y-overflow (which is based on the menucomponent only not any bookmark element) and show the
            ;; folder with positionState set by show-folder-wrapper to a explicitly safe value.  ie. check for vertical overflow and (reset!
            ;; visibilityState "visible") below.
            (when folderbutton
              ;; if overflow in x direction subtract (+ menuWidth folderbuttonWidth) from "left" of menu
              (let [folderbuttonBoundingRectangle (.getBoundingClientRect folderbutton)
                    menuLeftViewport (.-left menuBoundingRectangle)
                    menuLeft (px-to-int (get-computed-style menu-reference "left"))
                    ;;(.-offsetWidth menu-reference) returns wrong values for large titlebars 
                    menuOffsetWidth (+ 8 (.-offsetWidth dropzone-reference)) ;; 8 is for 4px border on either side of dropzone
                    menuViewportDisplacement (- (.-left menuBoundingRectangle) (.-left folderbuttonBoundingRectangle))
                    viewportWidth (.-width (.-visualViewport js/window))]
                (when (< viewportWidth (+ menuLeftViewport menuOffsetWidth)) 
                  ;;(println "overflow-x has occured")
                  ;;(setstyle menu-reference "left" (str (- menuLeft menuOffsetWidth menuViewportDisplacement) "px"))
                  (reset! positionState [(first @positionState) (max 0 (- menuLeft menuOffsetWidth menuViewportDisplacement))]))))
            (let [menuTop (px-to-int (get-computed-style menu-reference "top"))
                  menuVPTop (.-top (.getBoundingClientRect menu-reference))
                  menuOffsetHeight (.-offsetHeight menu-reference)
                  viewportHeight (.-height (.-visualViewport js/window))
                  verticalOverflowAmount (- (.-bottom menuBoundingRectangle) viewportHeight)]
              (when (< viewportHeight (+ menuVPTop menuOffsetHeight))
                ;;(println "overflow-y has occured")
                ;; 20 is random number to check for why double overflow is occuring
                ;;(setstyle menu-reference "top" (str (- menuTop verticalOverflowAmount 20)  "px"))
                (reset! positionState [(- menuTop verticalOverflowAmount 20) (second @positionState)]))))
          ;; if scrollbars intermittently appear before visible is set there will be a placement error gap
          ;;(setstyle menu-reference "visibility" "visible") 
          (reset! visibilityState "visible"))))))

(defn changeColumns-floating [menuReference positionState direction]
  (let [panelReference (.getElementById js/document "my-panel")
        menuDomTop (js/parseFloat (get-computed-style menuReference "top"))
        menuDomLeft (js/parseFloat (get-computed-style menuReference "left"))
        dropzoneFloating (.querySelector menuReference ".drop-zone-floating")
        currentWidth (js/parseFloat (get-computed-style dropzoneFloating "width" ))
        ;; index 1 retrieves "dropped-element-2069", "guggle222, 2069", is 432 pixels, index 0 dummy element is 460px ;; 490->491
        columnWidth (js/parseFloat (get-computed-style (gobj/get (.-children dropzoneFloating) 1) "width"))
        ;; taken from (defn drop-zone ...):
        thirdOfScreenPadding (int (* (.-innerWidth js/window) 0.32))  ;; padding of 2% for scrollbar
        possibleColumnsFloating (js/Math.ceil (max (/ (- currentWidth 30) thirdOfScreenPadding) 1))
        ;; scrollbar width is set in css to: .drop-zone-floating::-webkit-scrollbar, 15px added here
        columnCoefficient (if (= direction :right) (inc (int (/ currentWidth columnWidth))) (max 1 (dec (int possibleColumnsFloating))))
        ;; columnCoefficient (if (= direction :right) (inc (int possibleColumnsFloating)) (max 1 (dec (int possibleColumnsFloating))))
        newWidth (+ 15 (* columnCoefficient columnWidth))]
    
    (setstyle dropzoneFloating "width" (str newWidth "px") )
    
    (let [overflow (- (.-scrollWidth panelReference) (.-clientWidth panelReference))]
      (when (pos? overflow)
        (reset! positionState [menuDomTop (- menuDomLeft overflow)])))

    (setstyle dropzoneFloating "height" "auto"  ))) 


;; [dndv/drop-zone dropzone-id false]
(defn set-position-and-render-dropzone [dropzone-id embedded? changeColumns positionState dimensions visibilityState searchText
                                        show-containing-folder-element-id]
  ;; the render function will be called with the same arguments as the outer function, you cannot permute or exclude arguments 
  (r/create-class {:reagent-render  (dndv/drop-zone dropzone-id embedded? changeColumns positionState dimensions visibilityState searchText
                                                    show-containing-folder-element-id) ;; ignored by dndv/drop-zone
                   :component-did-mount (set-menu-position-closure dropzone-id positionState dimensions
                                                                   visibilityState show-containing-folder-element-id)})) 

(defn Menu [title xyposition dimensions show-containing-folder-element-id dropzone-id]
  ;; with-let is shorthand for a let .. fn closure which gives each component it's own state. Also allows for an optional finally.
  (let [currIndex (rf/subscribe [:dnd/get-zindex dropzone-id])
        ratom-image (r/atom "images/unlock.png")
        changeColumns (r/atom :steady)
        visibilityState (r/atom "hidden")
        positionState (r/atom xyposition)
        dropped-elements (rf/subscribe [:dnd/dropped-elements dropzone-id])
        searchActivated (r/atom false)
        searchText (r/atom "")
        s (r/atom {})]   
    ;; html element id is "menu-dropzone-id"
    (fn []
      (when (seq @dropped-elements) 
        [:div.Menu {:id (str "menu-" (name dropzone-id)) :style {:visibility @visibilityState :position "absolute"
                                                                 :top (str (first @positionState) "px") :left (str (second @positionState) "px") 
                                                                 :background-color (if themeColor "#222222" "black")
                                                                 :z-index (or @currIndex 1)}
                    ;; DANGER WITHOUT "when" to guard: :ref #(swap! s assoc :componentRef %) ;; will cause infinite updates!
                    ;; :ref (fn [el] (when el (swap! s assoc :componentRef el)))
                    ;; :ref #(swap! s assoc :componentRef %)
                    :ref (fn [el] (when el (swap! s assoc :componentRef el)))}
         
         [:div.MenuTitleBar {:title title
                             :style {:height "40px" :border-bottom (if themeColor "2px solid #111111" "2px solid black")
                                     :cursor "move" :user-select "none" :position "relative"
                                     :display "grid" :grid-template-columns "min-content min-content auto min-content min-content min-content"} 
                             ;;:on-mouse-up (fn [e] (closeDragElement e))
                             :on-mouse-up #(reset! menuhasmoved? false)
                             ;; dropzone-id is a key not a string, adds document wide mousemove and mouseup 
                             :on-mouse-down (fn [e] (dragMouseDown e dropzone-id))
                             :on-double-click (dblclick-menutitlebar s)}

          [:div.MenuPinBtn
           {:on-mouse-up (fn [e] (do  (if @menuhasmoved? (reset! menuhasmoved? false) (toggle-image ratom-image dropzone-id))))
            :on-double-click (fn [e] (do  (.stopPropagation e) (toggle-image ratom-image dropzone-id)))
            :title "Pin Window"
            :style {:justify-self "start" :display "flex" :align-items "center" :justify-content "center"
                    :padding-right "10px" :padding-left "10px"}}
           [:img {:src @ratom-image :style {:padding "2px"}  }]]

          [:div.MenuArrowBtn {:title "Toggle Search"
                              :style {:justify-self "start" :display "flex" :align-items "center" :justify-content "center"
                                      :padding-right "10px" :padding-left "10px"}
                              :on-double-click #(.stopPropagation %)
                              :on-mouse-up #(cond @menuhasmoved? (reset! menuhasmoved? false)
                                                  @searchActivated (let [searchInput (.querySelector (:componentRef @s) "#searchBox")]
                                                                     (reset! searchActivated false)
                                                                     (dnd/recursive-dropzone-search dropzone-id "")
                                                                     (setattrib searchInput  "value" "")
                                                                     (reset! searchText "")
                                                                     (rf/dispatch [:dnd/reset-cutoff-elements dropzone-id
                                                                                   defaultCutoffDropzoneElements]) ;; global var
                                                                     (clear-all-selections-except))
                                                  :else
                                                  (do (reset! searchActivated true)
                                                      (js/setTimeout (fn [] (let [searchInput (.querySelector (:componentRef @s) "#searchBox")]
                                                                              (.focus searchInput))) 100)))}
           [:img {:src "images/magnifying-glass.png" :style {:vertical-align "middle"} }]]

          ;; <input type="search" id="searchBox" style="width: 300px; background-color: wheat; border-radius: 5px; place-self: center start;">

          (let [calculateWidth (when (:componentRef @s)
                                 (str (* TITLETEXT_WIDTH_PERCENTAGE
                                         (px-to-int
                                          (let [dropzone (.getComputedStyle
                                                          js/window
                                                          (.querySelector (:componentRef @s) ".drop-zone-floating") nil)]
                                            (if (= (.-display dropzone) "grid") (.-width dropzone)
                                                (get-computed-style (:componentRef @s) "width")))))
                                      "px"))]
            (if @searchActivated
              [:div#floatingsearch.MenuArrowBtn {:title "Clear Search"
                                                 :on-mouse-up #(if @menuhasmoved? (reset! menuhasmoved? false)
                                                                   (let [searchInput (.querySelector (:componentRef @s) "#searchBox")]
                                                                     (dnd/recursive-dropzone-search dropzone-id "")
                                                                     (setattrib searchInput  "value" "")
                                                                     (reset! searchText "")
                                                                     (.focus searchInput)
                                                                     (rf/dispatch [:dnd/reset-cutoff-elements dropzone-id
                                                                                   defaultCutoffDropzoneElements]) ;; global var
                                                                     (clear-all-selections-except)))
                                                 :style {:display "flex" :justify-self "stretch"}}
               [:input#searchBox {:type "search"
                                  :title ""
                                  ;; if #(.stopPropagation %) is :on-mouse-up handler, clicking on inputbox does NOT clear
                                  :on-mouse-down #(.stopPropagation %)
                                  :on-mouse-up #(.stopPropagation %)
                                  :on-double-click #(.stopPropagation %)
                                  :on-change #(let [inputBoxValue (-> % .-target .-value)]
                                                (reset! searchText inputBoxValue)
                                                (dnd/recursive-dropzone-search dropzone-id inputBoxValue)
                                                (clear-all-selections-except))
                                  :style {:align-self "center" :margin "auto"
                                          :max-width calculateWidth  :background-color "wheat" :border-radius "5px"}  }]]

              [:div.TitleBarText {:style {:justify-self "center" :align-self "center" 
                                          :max-width calculateWidth}} 
               title]))

          [:div.MenuArrowBtn {:title "Fewer Columns"
                              :style {:justify-self "end" :display "flex" :align-items "center" :justify-content "center"
                                      :padding-right "10px" :padding-left "10px"}
                              :on-double-click #(.stopPropagation %)
                              :on-mouse-up #(cond (some? (.querySelector (:componentRef @s) ".search-not-found")) :do-nothing
                                                  :else (changeColumns-floating (:componentRef @s) positionState :left))}
           [:img {:src "images/left-chevron.png"  }]]
          
          [:div.MenuArrowBtn {:title "More Columns"
                              :style {:justify-self "end" :display "flex" :align-items "center" :justify-content "center"
                                      :padding-right "10px" :padding-left "10px"}
                              :on-double-click #(.stopPropagation %)
                              :on-mouse-up #(cond (some? (.querySelector (:componentRef @s) ".search-not-found")) :do-nothing
                                                  :else (changeColumns-floating (:componentRef @s) positionState :right))}
           [:img {:src "images/right-chevron.png"  }]]
          
          [:div.MenuCloseBtn
           {:on-click (fn [e] (do (.stopPropagation e) (cleanup-child-dropzones dropzone-id :forceClose)
                                  (dndc/hide-menu dndc/link-context-menu dndc/link-move-submenu-container)
                                  (dndc/hide-menu dndc/folder-context-menu dndc/folder-move-submenu-container)))
            :title "Close"
            :style {:justify-self "end" :display "flex" :align-items "center" :justify-content "center"
                    :padding-right "10px" :padding-left "10px"}} 
           [:img {:src "images/close16.png" :style {:padding "5px"}  }]]] 
         
         [set-position-and-render-dropzone dropzone-id false changeColumns positionState dimensions visibilityState searchText
          show-containing-folder-element-id]
         
         [:div.resizer-l {:on-mouse-down (fn [e] (resizeMouseDown e dropzone-id 'lBorder)) :on-double-click (dblclick-border s 'lBorder)}]
         [:div.resizer-r {:on-mouse-down (fn [e] (resizeMouseDown e dropzone-id 'rBorder)) :on-double-click (dblclick-border s 'rBorder)}]
         [:div.resizer-b {:on-mouse-down (fn [e] (resizeMouseDown e dropzone-id 'bBorder)) :on-double-click (dblclick-border s 'bBorder)}]
         [:div.resizer-t {:on-mouse-down (fn [e] (resizeMouseDown e dropzone-id 'tBorder)) :on-double-click (dblclick-border s 'tBorder)}]]))))



;;  (rf/dispatch [:destroy-drop-zone dropzone-id])  ;; no :dnd/ needed
(rf/reg-event-db
 :destroy-drop-zone 
 (fn [db [_ dropzone-id]]
   ;;(println "calling (rf/reg-event-db :destroy-drop-zone [_ dropzone-id] dropzone-id is: " dropzone-id)
   (-> db 
       (update-in [:dnd/state :drop-zones] dissoc dropzone-id)
       (update-in [:dnd/state :drop-zone-options] dissoc dropzone-id))))



;; cleanup-child-dropzones, destroys dropzones that may have been created by subfolders if they have been opened
;; the argument dropzone-id itself is only destroyed from the database when the dropzone of a folder containing it is closed.
;; Unforced version only used in (defmethod dndv/dropped-widget :folderbox ... in :on-dragstart and :on-drag-end fired at
;; the source. Unforced version does not close dropzone-id Menu if it is pinned.
(defn cleanup-child-dropzones
  ([dropzone-id]
   (let [db             (rf/subscribe [:dnd/db])
         unpinnedDropzoneOptionKeys (keys (filter #(= (:pinned (val %)) false) (get-in @db [:dnd/state :drop-zone-options])))
         subfolderIdList (conj (map fid->dkey (get-all-subfolders (dkey->fid dropzone-id))) dropzone-id)
         dropzonesToBeDestroyed (clojure.set/intersection (set unpinnedDropzoneOptionKeys) (set subfolderIdList))
         dropzonesToBeDestroyedExcludingRoot  (clojure.set/difference dropzonesToBeDestroyed #{dropzone-id})]
     ;; destroy all unpinned dropzones except for the root
     (doseq [dz dropzonesToBeDestroyedExcludingRoot] (rf/dispatch [:destroy-drop-zone dz]))
     ;; if the root folder is unpinned destroy it's menu and all it's subfolder menus
     (doseq [dz dropzonesToBeDestroyed] (destroymenu dz))))
  ;; forceClose allows the x button to destroy a menu even if it is pinned, otherwise the above version excludes destroying
  ;; a pinned menu (representing the dropzone in the argument)
  ([dropzone-id forceClose]
   (let [db             (rf/subscribe [:dnd/db])
         unpinnedDropzoneOptionKeys (keys (filter #(= (:pinned (val %)) false) (get-in @db [:dnd/state :drop-zone-options])))
         ;; subfolders without root
         subfolderIdList (map fid->dkey (get-all-subfolders (dkey->fid dropzone-id)))
         ;; includes all unpinned subfolders with root added on
         dropzonesToBeDestroyed (conj (clojure.set/intersection (set unpinnedDropzoneOptionKeys) (set subfolderIdList)) dropzone-id)]
     ;; destroy all unpinned dropzones and their menus, and force destroy root regardless of it's pinned state
     (doseq [dz dropzonesToBeDestroyed] (rf/dispatch [:destroy-drop-zone dz]) (destroymenu dz))))) 
 
 
(defn appended-container [target dropzone-id]
  (let [id (str "menu-" (name dropzone-id))
        container (.getElementById js/document id)]

    (if container
      container
      ;;appends a div with (= id (str "menu-" (name dropzone-id))) to target
      (.appendChild target (doto (.createElement js/document "div")
                             (-> (.setAttribute "id" id)))))))

;; << folder functions >>


;; [FolderButton [500 500] :dropzone-26]  
(defn FolderButton [dropzoneElement dropzoneRef clicked] 
  (let [title (:title dropzoneElement)
        dropzone-id (keyword (str "dropzone-" (name (:id dropzoneElement) )))
        parentDropzoneKey (if-let  [searchDropzoneKey (:searchDropzone dropzoneElement)]
                            searchDropzoneKey
                            (fid->dkey (:parentId dropzoneElement)))
        folderId (:id dropzoneElement)
        menuOpen-state (rf/subscribe [:dnd/menuOpen-state dropzone-id])
        pinned-state (rf/subscribe [:dnd/pinned-state dropzone-id])
        ;; hold on to a dom reference to the button using reagent :ref attribute below
        s (r/atom {})

        folderbutton-mouseup-handler
        (fn [e]
          (let [isCtrlDown @(rf/subscribe [:dnd/get-keystate :ctrlIsDown])
                isShiftDown @(rf/subscribe [:dnd/get-keystate :shiftIsDown])
                currentSelectedElements @(rf/subscribe [:dnd/get-selected parentDropzoneKey])
                dropzoneElementId (:id dropzoneElement)]
            (cond (= (.-button e) 2) :do-nothing ;; mouse-up is caused by rightclick event do nothing
                  isCtrlDown
                  (do (clear-all-selections-except parentDropzoneKey)
                      (rf/dispatch [:dnd/reset-selected (vec (remove #{:anchor} currentSelectedElements))
                                    parentDropzoneKey])
                      (if (some #{folderId} currentSelectedElements) 
                        (rf/dispatch [:dnd/remove-selected parentDropzoneKey folderId])
                        (rf/dispatch [:dnd/append-selected parentDropzoneKey folderId])))
                  isShiftDown
                  (do (clear-all-selections-except parentDropzoneKey)
                      (let [allTabElementIds (map :id @(rf/subscribe [:dnd/dropped-elements parentDropzoneKey]))
                            
                            setAnchorTabElements (cond (empty? currentSelectedElements) [(:id dropzoneElement) :anchor]
                                                       (some #{:anchor} currentSelectedElements) currentSelectedElements
                                                       :else (conj  (vec currentSelectedElements) :anchor))
                            anchorElement (nth setAnchorTabElements (dec (.indexOf setAnchorTabElements :anchor)))
                            anchorIndex (.indexOf allTabElementIds anchorElement)
                            indexOfShiftClickedElement (.indexOf allTabElementIds dropzoneElementId)

                            newSelectedTabElements
                            (let [newSelectedTabElementsNoAnchor
                                  (for [index (range (min anchorIndex indexOfShiftClickedElement)
                                                     ;; inc because range open upper bound
                                                     (inc (max anchorIndex indexOfShiftClickedElement)))]
                                    (nth allTabElementIds index))
                                  [before after] (split-at (inc (.indexOf newSelectedTabElementsNoAnchor anchorElement))
                                                           newSelectedTabElementsNoAnchor)]
                              (vec (concat before [:anchor] after)))]
                        (rf/dispatch [:dnd/reset-selected newSelectedTabElements parentDropzoneKey])))
                  :else
                  ;; if open then cleanup-child-dropzones closes it and destroys children (unless pinned).
                  ;; nb: @menuOpen-state returns nil when a dropzone doesn't exist and generally (= (not nil) true)
                  (if (= @menuOpen-state true) (do (clear-all-selections-except)
                                                   (cleanup-child-dropzones dropzone-id))
                      ;; when not dragging and not open then create the menu: 
                      (when (and (not @(rf/subscribe [:dnd/get-drag-state])) (not @menuOpen-state)) 
                        (let [ ;; Initialize and populate the dropzones

                              _ (clear-all-selections-except) ;; clear selections
                              
                              xposition (- (:left (dnd/bounding-rect (:button @s))) 
                                           (:left (dnd/bounding-rect (.getElementById js/document "my-panel"))))                            
                              yposition (- (:top (dnd/bounding-rect (:button @s)))
                                           (:top (dnd/bounding-rect (.getElementById js/document "my-panel"))))

                              folderbutton-parent (.-parentNode (:button @s))
                              folderbutton-abs-right-pos (.-right (.getBoundingClientRect folderbutton-parent))
                              dropzoneReference (.-parentNode folderbutton-parent)
                              dropzoneReference-abs-right-pos (.-right (.getBoundingClientRect dropzoneReference))
                              
                              
                              folderbutton-width (.-offsetWidth (.-parentNode (:button @s)))
                              folderbutton-height (.-offsetHeight (:button @s))

                              ;;Note that absolute menu positioning is relative to the entire webpage because folderbutton is not
                              ;;really an html parent to menu 
                              ;; Menu appears to the right
                              menu-xyposition [yposition
                                               (if (< dropzoneReference-abs-right-pos folderbutton-abs-right-pos)
                                                 dropzoneReference-abs-right-pos (+ xposition folderbutton-width))]

                              _ (rf/dispatch [:dnd/initialize-drop-zone
                                              dropzone-id ;; this is the dropzone-id
                                              ;; options
                                              {:folderId folderId
                                               :pinned false
                                               :menuOpen false
                                               :collapsedStartupToggle false
                                               :cutoffDropzoneElements defaultCutoffDropzoneElements
                                               :selected []
                                               :z-index 1}])
                              _ (try (if (.hasOwnProperty js/chrome "bookmarks")
                                       ;; title menu-xyposition dropzone-id 
                                       (rf/dispatch [:chrome-synch-all-dropzones-to-folderId-with-menu menu-xyposition nil false dropzone-id])
                                       (rf/dispatch [:dnd/synch-all-dropzones-to-folderId-with-menu title menu-xyposition nil false dropzone-id]))
                                     ;; tested error => Error Occured:  {:type :custom-arg-error, :message find-id: id was not found}
                                     (catch :default e (println "Error Occured: " e)))]))))))

        folderbutton-contextmenu-handler 
        (fn [e]
          (.preventDefault e) ;; disables default right click menu
          (dndc/hide-menu dndc/link-context-menu dndc/link-move-submenu-container) ;; hide link menu
          (if-let [currentDz (:searchDropzone dropzoneElement)]
            (:searchDropzone dropzoneElement) (clear-all-selections-except (fid->dkey (:parentId dropzoneElement))))
          ;; set the new right clicked element and maintain the copy-selected clipboard contents until pasted or new copy or cut
          ;; because the new right clicked element will affect the count of dndc/fetch-links-of-all-selected
          ;;                                                         rightClicked     selected 
          (rf/dispatch-sync [:dnd/initialize-or-update-clipboard nil dropzoneElement  (:selected @(rf/subscribe [:dnd/get-clipboard]))])
          (let [countAllElementsSelected (count (fetch-all-selected))
                ;; different from countAllElementsSelected because subfolder links are count
                countAllLinksSelected (count (dndc/fetch-links-of-all-selected))
                clipboardContents @(rf/subscribe [:dnd/get-clipboard])]
            (if (:selected clipboardContents)
              (do (.setEnabled dndc/folder-paste-in-menuitem true) (.setEnabled dndc/folder-paste-above-menuitem true))
              (do (.setEnabled dndc/folder-paste-in-menuitem false) (.setEnabled dndc/folder-paste-above-menuitem false)))
            ;; Change delete caption if anything is selected, or just "delete" if only rightclicked and nothing selected
            (if (> countAllElementsSelected  0)
              (dndc/setCaptionAndAccelerator dndc/folder-delete-menuitem (str "Delete (" (str countAllElementsSelected) ") selected") "Del")
              (dndc/setCaptionAndAccelerator dndc/folder-delete-menuitem "Delete" "Del"))
            ;; you can assume what has been right clicked is a folder
            ;; countAllLinksSelected is links contained in selected elements 
            ;;   if nothing selected then use right clicked element (only 0 for empty folders)
            ;; countAllElementsSelected is only the count of highlighted elements
            ;;   for an empty folder "open in tab" should be greyed out
            (if (> countAllLinksSelected  0) 
              (do (.setEnabled dndc/folder-open-menuitem true)
                  (.setEnabled dndc/folder-move-submenu true)
                  (.setCaption dndc/folder-open-menuitem (str "Open all (" (str countAllLinksSelected) ") links in tabs"))
                  (.setCaption dndc/folder-move-submenu (if (> countAllElementsSelected 0)
                                                          (str "Move (" (str countAllElementsSelected) ") selected to ...")
                                                          "Move to ...")))
              ;; empty folder case: countAllLinksSelected is 0 ie. only an empty folder was selected or rightclicked
              (do (.setEnabled dndc/folder-move-submenu true)
                  ;; handles case where only empty folder is selected
                  ;; ie: countAllLinksSelected:  0 countAllElementsSelected: 1
                  (.setCaption dndc/folder-move-submenu (if (> countAllElementsSelected 0)
                                                          (str "Move (" (str countAllElementsSelected) ") selected to ...")
                                                          "Move to ..."))
                  (.setCaption dndc/folder-open-menuitem "Open in new tab")
                  (.setEnabled dndc/folder-open-menuitem false)))
            (dndc/place-context-menu dndc/folder-context-menu)))]
    
    (fn [dropzoneElement dropzoneRef clicked]
      (cond (= 0 (.-button @clicked)) (do (folderbutton-mouseup-handler @clicked)
                                          (reset! clicked false))
            (= 2 (.-button @clicked)) (do (folderbutton-contextmenu-handler @clicked)
                                          (reset! clicked false))
            :else (reset! clicked false))

      [:div 
       {;; DANGER WITHOUT when guard: :ref #(swap! s assoc :componentRef %) ;; will cause infinite updates!
        ;; :ref #(swap! s assoc :button %)
        :ref (fn [el] (when el (swap! s assoc :button el))) 
        
        :title title
        ;; width is auto and contained in :folderbox where it is set to thirdOfscreen, 2% less than thirdofscreenpadding
        :style {:display "flex" :align-items "center" :flex-grow "1" ;; flex-grow is an item, not container property
                :position "relative"  :user-select "none" :overflow "hidden" :white-space "nowrap" 
                :text-overflow "ellipsis" }}
       ;; if open then cleanup-child-dropzones closes it and destroys children (unless pinned).
       ;; :on-mouse-up and :on-context-menu are disabled because otherwise the handlers will be called twice,
       ;; once for :folderbox mouse-up and once for FolderButton 
       ;; :on-mouse-up folderbutton-mouseup-handler
       
       [:img.icon-folder {:src "images/folder16.png"}] [:span {:style {:overflow "hidden" :text-overflow "ellipsis"}} title]])))


(defn folderbox-overlay [top height currentDragState dropzoneElement s topOrBottom]
  [:div {:style {:position "absolute" :left "0px" :top top :height height :width "100%" :display (if @currentDragState "block" "none")}
         :on-drag-over #(do (.preventDefault %) ;; on-drop doesn't work without this
                            (if (= :top topOrBottom)
                              (.setAttribute (.-previousElementSibling (:componentRef @s)) "class" "dummy-element_drag_over")
                              (.setAttribute (.-nextElementSibling (:componentRef @s)) "class" "dummy-element_drag_over")))

         :on-drag-leave #(do (if (= :top topOrBottom)
                               (.setAttribute (.-previousElementSibling (:componentRef @s)) "class" "dummy-element_drag_leave")
                               (.setAttribute (.-nextElementSibling (:componentRef @s)) "class" "dummy-element_drag_leave")))

         :on-drop (edge-overlay-ondrop s dropzoneElement topOrBottom "folderbox-element")}])


(defn folderbox-overlay-middle [currentDragState dropzoneElement s]
  [:div {:style {:position "absolute" :left "0px" :top "25%" :height "50%" :width "100%"
                 :display (if @currentDragState "block" "none")}
         ;; on-drop doesn't work without this
         :on-drag-over #(do (.preventDefault %) (when @s (setattrib (:componentRef @s) "className" "folderbox-element_drag_over")))

         :on-drag-leave #(do (.preventDefault %) (when @s (setattrib (:componentRef @s) "className" "folderbox-element")))

         :on-drop (fn [event]
                    (do (.preventDefault event) ;; drag and drop works without this 
                        (when @s (setattrib (:componentRef @s) "className" "folderbox-element"))
                        (center-overlay-ondrop s event (:id dropzoneElement) 0 true)))}])

;; sometimes the appdb would not update with the new dragstate and so forcing the state to change until successful
(def timerId (r/atom 0))  

(defn secure-drag-state-false []
  (fn []    
    (let [currentDragState (rf/subscribe [:dnd/get-drag-state])]
      (if @currentDragState
        (rf/dispatch [:dnd/set-drag-state false])
        (doseq [x (range (inc @timerId))] 
          (js/clearInterval x))))))

(defmethod dndv/dropped-widget
  :folderbox
  [dropzoneElement embedded? embeddedWidgetWidth dropzoneRef lastInCol] 
  ;; {:keys [index type title associated-dropzone id dateAdded dateGroupModified parentId]} 
  ;;  children key excluded
  ;;subscription to the drag-status of dropzone element with id (:id dropzoneElement) and dropzone id: id
  (let [s (r/atom {})
        clicked (r/atom false)]
    (fn [dropzoneElement embedded? embeddedWidgetWidth dropzoneRef lastInCol]
      (let [currentDragState (rf/subscribe [:dnd/get-drag-state])
            dropzone-id (fid->dkey (:id dropzoneElement))
            parentDropzoneKey (if-let  [searchDropzoneKey (:searchDropzone dropzoneElement)]
                                searchDropzoneKey
                                (fid->dkey (:parentId dropzoneElement)))
            menuOpen-state (rf/subscribe [:dnd/menuOpen-state dropzone-id])
            pinned-state (rf/subscribe [:dnd/pinned-state dropzone-id])
            thirdOfScreen (int (* (.-innerWidth js/window) 0.3))

            fetchMenuFrame (when (:componentRef @dropzoneRef) (.closest (:componentRef @dropzoneRef) "div[id^='menu-dropzone']"))
            bottomOfMenuFrame (when fetchMenuFrame (.-bottom (.getBoundingClientRect fetchMenuFrame)))
            topOfFolderElement (when (:componentRef @s) (.-top (.getBoundingClientRect (:componentRef @s))))
            bottomBorderWidthOfMenuFrame (when fetchMenuFrame (px-to-int (get-computed-style fetchMenuFrame "border-bottom-width")))
            elementHeight (when  (:componentRef @s) (px-to-int (get-computed-style (:componentRef @s) "height"))) ;; integer

            ;; The reason why bottomofmenuframe is used instead of clientHeight, is because when scrolling is necessary, subtracting from
            ;; clientHeight will always be negative height, which is interpreted by the browser as 0, so dropping will no longer work.
            ;; With bottomofmenuframe, height is only negative when scrolled out of view.
            ;; eg newtitle222,41: (- 576 535 20 4) ;; 17px exactly
            calcHeight (max 0 (- bottomOfMenuFrame topOfFolderElement (js/Math.round (+ (* .75 (- elementHeight 4)) 2)) bottomBorderWidthOfMenuFrame))
            
            isCtrlDown @(rf/subscribe [:dnd/get-keystate :ctrlIsDown])
            isShiftDown @(rf/subscribe [:dnd/get-keystate :shiftIsDown])
            contextmenuVisible? @(rf/subscribe [:dnd/get-contextmenu-visible])
            getSelected @(rf/subscribe [:dnd/get-selected parentDropzoneKey])
            clipboardContents @(rf/subscribe [:dnd/get-clipboard])]
        [:div.folderbox-element
         {:id (str "dropped-element-" (name (:id dropzoneElement)))
          ;; DANGER WITHOUT when guard: :ref #(swap! s assoc :componentRef %) ;; will cause infinite updates!
          ;; :ref #(swap! s assoc :componentRef %)
          :ref (fn [el] (when el (swap! s assoc :componentRef el))) 

          :style {:box-sizing "border-box" :display "flex" :align-items "stretch"
                  :position "relative" :width (if embedded? embeddedWidgetWidth thirdOfScreen)
                  ;; without a 2px solid black border, the highlighted radial gradient :background-image upon selection is looks too big
                  ;; the border must be black because otherwise with a transparent border the highlighted radial gradient will show through
                  ;; :border (if (some #{(:id dropzoneElement)} @(rf/subscribe [:dnd/get-selected parentDropzoneKey]))
                  ;;           "2px solid black" "2px solid transparent")
                  :border (let [rightclicked? (= dropzoneElement (:rightClicked clipboardContents))
                                selected? (some #{(:id dropzoneElement)} getSelected)]
                            (cond (and contextmenuVisible? rightclicked?) "2px solid white"
                                  selected? (if themeColor "2px solid #222222" "2px solid black")
                                  :else "2px solid transparent"))
                  :background-image (if (some #{(:id dropzoneElement)} getSelected)
                                      "radial-gradient(circle at center, #AF0404 0%, gold 100%)" "none")} 
          :draggable true ; ;; required for divs because  only images and links are draggable by default
          ;; on-drop does not work without .preventDefault on-drag-over. See ondragover event.
          :class (cond @currentDragState "folderbox-element no-hover"
                       (or isCtrlDown isShiftDown) "folderbox-element-modifier-down"
                       :else "folderbox-element")

          :on-mouse-up #(reset! clicked %)
          :on-context-menu #(reset! clicked %)
          
          :on-drag-start #(do (rf/dispatch [:dnd/set-drag-state true])
                              ;; close the folder if it is being dragged 
                              ;; note that if dropzones don't exist @pinned-state and @menuOpen-state return nil and this won't execute
                              (when (= @menuOpen-state true) 
                                (cleanup-child-dropzones dropzone-id))
                              #_(.setData (.-dataTransfer %) "text/my-custom-data"
                                          (str (name (:type dropzoneElement)) "," (:id dropzoneElement) ","
                                               (str "dropzone-" (:parentId dropzoneElement))))
                              (.setData (.-dataTransfer %) "text/my-custom-data" (pr-str dropzoneElement))
                              (when (:componentRef @s) (setattrib (:componentRef @s) "className" "folderbox-element"))
                              (let [selectedIds @(rf/subscribe [:dnd/get-selected (fid->dkey (:parentId dropzoneElement))])]
                                ;; if the dragged element is not among the selectedIds, then unselect all immediately
                                ;; ie. (dispatch-sync version of clear-all-selections-except) so that center-overlay-ondrop
                                ;;     will not move the selected elements when :on-drop event occurs, only the unselected element
                                (when (not (nat-int? (.indexOf selectedIds (:id dropzoneElement))))
                                  (clear-all-selections-except))))
          
          :on-drag-end #(do (reset! timerId (js/setInterval (secure-drag-state-false) 200))
                            #_(println "folderbox :on-drag-end fired")
                            ;; note that if dropzones don't exist @pinned-state and @menuOpen-state return nil and this won't execute
                            (when (= @menuOpen-state true)
                              (cleanup-child-dropzones dropzone-id)))}

         ;; folderbutton widget
         [FolderButton dropzoneElement dropzoneRef clicked]

         ;; overlay 1
         [folderbox-overlay "calc(-25% - 4px)" "calc(50% + 4px)" currentDragState dropzoneElement s :top]

         ;; overlay 2
         [folderbox-overlay-middle currentDragState dropzoneElement s]
         
         ;; overlay 3
         [folderbox-overlay "75%" (if lastInCol (str calcHeight "px") "calc(50% + 4px)")
          currentDragState dropzoneElement s :bottom]]))))


;; << debugging, my-panel, and mounting >>

;; ---------------------------------------------------- TDD START ---------------------------------------------------
;; ----- to fetch tdd testing debug input box value use: (.-value (.getElementById js/document "debuginput")) --------
;; (def tdd_atom (r/atom "nothing to see here"))
;; (defn runme [] (reset! tdd_atom "cfe brk!"))



;; ----------------------------------------------------- TDD END ---------------------------------------------
#_(defn debug-panel
  "pretty prints data in a nice box on the screen."
  [s]
  (let [collapsed (r/atom true) treeDepth 3 totalLength 2500]
    (fn [s]
      [:div.debug-window-wrap {:style {:border "1px solid black"}}
       [:div {:on-click #(swap! collapsed not)
              :style {:cursor :pointer
                      :padding "10px"
                      :border "1px solid #ccc"}}
        [:div.clear
         [:div.pull-right [:b "Debug window "]]
         [:div (if @collapsed "> expand" "v collapse")]]]
       (when-not @collapsed
         (let [elidedBookmarkAtom (assoc-in s [:dnd/state :bookmark-atom] "removed-- see L1515 dndmenu.org (defn my-panel ...)")]
           [:pre (with-out-str  (binding [*print-level* 9 *print-length* 15 ] (clojure.pprint/pprint elidedBookmarkAtom )))  ]))])))

#_(defn debug-panel-sub-key
  "pretty prints data in a nice box on the screen."
  [s]
  (let [collapsed (r/atom true) ]
    (fn [s]
      ;; (reset! changes (conj @changes (assoc s :index 0))) ;; gathers any changes into a set, you should fix point also; bugged: :dateAdded,:id 
      [:div.debug-window-wrap {:style {:border "1px solid black"}}
       [:div {:on-click #(swap! collapsed not)
              :style {:cursor :pointer
                      :padding "10px"
                      :border "1px solid #ccc"}}
        [:div.clear
         [:div.pull-right [:b "Debug window TDD testing"]]
         [:div (if @collapsed "> expand" "v collapse")]]]
       (when-not @collapsed
         ;;[:pre (with-out-str  (binding [*print-level* 9 *print-length* 5 ] (clojure.pprint/pprint s)))  ]
         [:pre (with-out-str  (clojure.pprint/pprint s))]
         )])))

;; views: ({:view-title "basketball clojure", :open-folder-dimensions
;; ({:folderId "14", :top "20px", :left "37px", :width "879px", :height "137px"}
;;  {:folderId "17", :top "229px", :left "36px", :width "879px", :height "169px"})} nil nil nil nil nil)
(defn ViewBar []
  (let [_ (rf/dispatch [:dnd/initialize-view-titles])
        indexed-views (rf/subscribe [:dnd/get-view-titles])]
   (fn []
     (when @indexed-views
      [:div.ViewBar 
       (for [[view-number view-title] @indexed-views]
         ^{:key (random-uuid)}
         [:<>
          [:div.ViewBarButtonContainer 
           [:div.ViewBarButton.Left {:title "save view" :on-click #(dndc/resetSaveViewDialog view-number)} ]
           [:div.ViewBarButton.Right {:title "restore view"
                                      :on-click #(try (dndc/restore-view view-number)
                                                      (println ["restoring view: " view-title view-number])
                                                      (catch :default e (run-fade-alert (str "this view is empty"))))}]
           [:img {:src "images/diskette.png" :style {:vertical-align "middle" :padding-left "4px" :padding-right "4px"} }]
           [:span.ViewButtonTitle (if view-title view-title (str "VIEW-" view-number))]
           [:img {:src "images/share.png" :style {:vertical-align "middle" :padding-left "4px" :padding-right "4px"} }]]
          [:div {:style {:flex .25}} ]])]))))


(defn my-panel
  []
  (let [ ;;this state is necesary to determine if we need to show the drag-box
        db             (rf/subscribe [:dnd/db])]

    (fn []
      ;;Note if you set the position of this div as relative it will always be above any static element, and therefore
      ;;any static draggable element when completely dragged underneath will be lost.
      [:div  {:id "my-panel"
              :on-context-menu (fn [e] (.preventDefault e)) ;; disable chrome's context menu
              :on-click (fn [e]
                          (when (= "my-panel" (.-id (.-target e)))
                            (clear-all-selections-except)))
              :style {:position "relative" }}

       [ViewBar]
       [:div  {:style { :margin-bottom "10px" }}]
     
       [EmbeddedMenuTabs]
       [:div  {:style { :margin-bottom "20px" }}]
       
       (when (:show (nth embeddedMenuConfiguration 2))
         [EmbeddedMenu "Bookmarks Bar" :dropzone-1 "1" ])
       [:div  {:style { :margin-bottom "20px" }}]
       
       (when (:show (nth embeddedMenuConfiguration 3))
         [EmbeddedMenu "Other Bookmarks" :dropzone-2 "2"])
       
       
       ;; [:button#tddtesting {:type "button" :on-click runme}  [:span "TDD testing"]]

       ;; ;; to fetch this input value use: ;; (.-value (.getElementById js/document "debuginput"))
       ;; [:input#debuginput {:type "text" :defaultValue "9999999"}]

       ;; ;; [debug-panel-sub-key @(rf/subscribe [:dnd/mouse-position])]
       ;; ;; evaluates to: [:pre (with-out-str  (clojure.pprint/pprint s))  ] 

       ;; [debug-panel-sub-key [" @(rf/subscribe [:dnd/get-clipboard]): "
       ;;                       @(rf/subscribe [:dnd/get-clipboard])
       ;;                       " @(rf/subscribe [:dnd/get-contextmenu-visible]): "
       ;;                       @(rf/subscribe [:dnd/get-contextmenu-visible])]] 
       ;; [debug-panel @db]

       [:div.clear]

       [:div#fade-alert-container]])))



(defn mount-dndmenu [] 
  (if-let [node (.getElementById js/document "dndmenu")] ;; if truthy (not false or nil)
    (rdom/render [my-panel] (js/document.getElementById "dndmenu")))
  (.render dndc/link-context-menu (.getElementById js/document "my-panel"))
  (.render dndc/link-move-submenu-container (.getElementById js/document "my-panel"))
  (.render dndc/folder-context-menu (.getElementById js/document "my-panel"))
  (.render dndc/folder-move-submenu-container (.getElementById js/document "my-panel"))
  (rf/dispatch [:dnd/show-folder-from-query-parm]))







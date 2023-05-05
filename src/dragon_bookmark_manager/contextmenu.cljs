
(ns dragon-bookmark-manager.contextmenu
  (:require 
   [reagent.core :as r]
   [re-frame.core :as rf]
   [clojure.zip :as zip]
   [goog.ui.Menu]
   [goog.ui.SubMenu]
   [goog.ui.MenuItem]
   [goog.ui.MenuSeparator]
   [goog.ui.Dialog]
   [goog.html.SafeHtml]
   [goog.events]
   [goog.object]
   [goog.ui.Component.EventType]
   [dragon-bookmark-manager.events :as dnde]
   [dragon-bookmark-manager.utilities :refer [fid->dkey dkey->fid setattrib setstyle run-fade-alert find-id map-vec-zipper chrome-update-bookmark
                                              chrome-getSubTree-bookmark chrome-move-bookmark-selected-wrapper stub-move-bookmark get-all-subfolders
                                              get-property onevent-dispatch-refresh-fnhandle clear-all-selections-except clear-all-selections-except-async
                                              destroymenu getstyle fetch-all-selected defaultCutoffDropzoneElements get-computed-style
                                              px-to-int find-id-no-throw]])
  (:require-macros [dragon-bookmark-manager.macros :as macros]))


;; << utility functions >>
;; (defn selectall-rightclicked-dropzone [])
;; (defn hide-menu [menu & [submenu-container]])
;; (defn place-context-menu [menu])
;; (defn find-title-from-id [elementId])
;; (defn folder-already-shown-error-msg [elementId])
;; (defn show-folder [parentDropzone rightClickedElement])
;; (defn show-grand-parent [elementId rightClickedElement])
;; (defn show-containing-folder [])
;; (defn fetch-all-selected [])
;; (defn delete-selected [])
;; (defn cutToClipboard [])
;; (defn move-to-top-or-bottom [& {:keys [topOrBottom newFolder] :or {topOrBottom :top newFolder nil}}])
;; (defn process-bookmarkElementVector [bookmarkElementVector newParentId newIndex createElementCounter countSelectedElementList])
;; (defn process-bookmarkElement [bookmarkElement newParentId newIndex createElementCounter countSelectedElementList])
;; (rf/reg-event-fx :dnd/pasteFromClipboard )
;; (defn open-in-tabs [])


;; Utility Functions for Context Menu  --------------------------------------------------------------------------------------------------

(declare resetRenamePageDialog)
(defn rename-mouseover [] 
  (let [mousePosition @(rf/subscribe [:dnd/mouse-position])
        elementsAtPointArray (js->clj (.elementsFromPoint js/document (:x mousePosition) (:y mousePosition)))
        ;; .-className is a string variable representing the class or space-separated classes of the current element
        ;; [.-id .-className] fetches: for floating: ["menu-dropzone-3471" "Menu"] for embedded: ["menu-dropzone-1" ""]
        ;; for tabhistory: ["menu-tab-history" ""]
        ;; matchResults [(.-id (first matchResults)) (.-className (first matchResults))]

        ;; check for contextmenu collision
        matchContextmenu (filter #(.matches % "div[class^='goog-menu']") elementsAtPointArray)
        
        ;; get dropzone
        matchDropzone (filter #(.matches % "div[id^='menu-dropzone'],div[id='menu-tab-history']") elementsAtPointArray)
        menuDropzoneIds (map #(get-property % "id") matchDropzone)
        matchingDropzone  (keyword (if-not (nil? (first menuDropzoneIds)) (subs (first menuDropzoneIds) 5) "nothingfound"))

        ;; get first dropped-element html #id in elementsatpointarray, or nil if nothing found. Of the form "dropped-element-3475".
        matchHtmlElement (first (filter #(and (.matches % "div[id^='dropped-element']") ) elementsAtPointArray))
        ;; "dropped-element-dummyid3476" -> "dummyid3476", "dropped-element-3475" -> "3475",
        ;; "dropped-element-9000000040" -> "9000000040"
        matchElement (if-not  (nil? matchHtmlElement) (subs (.-id matchHtmlElement) 16) "nothingfound")]
    (cond
      (seq matchContextmenu) :do-nothing ;; do nothing if hovering contextmenu
      ;; mouse is not over a dropzone:
      (= matchingDropzone :nothingfound) :do-nothing ;; (prn "no dropzone found: matchingDropzone: " matchingDropzone)
      ;; element of class "dummy-element" is being hovered at highes z-index:
      (and (some? matchHtmlElement) (= (.-className matchHtmlElement) "dummy-element")) :do-nothing ;; (prn "dummy element found: matchElement: " matchElement)
      (= matchingDropzone :tab-history) :do-nothing
      #_(if (= :tabselected @(rf/subscribe [:dnd/get-tabOrHistorySelected]))
          (let [getTabElement (first (filter #(= (:id %) matchElement) @(rf/subscribe [:dnd/get-tabs]))) ]
            (prn ["tabselected matchElement do nothing! can't rename!: " matchElement " getTabElement: " getTabElement]))
          (let [getHistoryElement (first (filter #(= (:id %) matchElement) @(rf/subscribe [:dnd/get-history]))) ]
            (prn ["tabselected matchElement do nothing! can't rename!: " matchElement " getHistoryElement: " getHistoryElement])))
      :else
      ;; over a titlebar matchingDropzone dropzone is found, and matchHtmlelement may find an dropped-element beneath it with a correct .-id
      ;; but getDroppedElement is nil because the filter fails to match the element with the dropzone.
      ;; Therefore when getDroppedelement is nil here do nothing
      (let [getDroppedElement (first (filter #(= (:id %) matchElement) @(rf/subscribe [:dnd/dropped-elements matchingDropzone])))]
        (when getDroppedElement (resetRenamePageDialog getDroppedElement))
        ;;(prn ["dropzone element matchElement: " matchElement " getDroppedElement: " getDroppedElement])
        ))))

(defn selectall-rightclicked-dropzone []
  (let [rightClickedElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard]))
        selectDropzone (case (:type rightClickedElement) ;; selectDropzone is: :tabselected, :historyselected, or :dropzone-???
                         :tablink :tabselected
                         :historylink :historyselected
                         (:link :folderbox :dummy) ;; case syntax for :link, :folderbox, or :dummy (last in column)
                         (if-let [searchDropzone (:searchDropzone rightClickedElement)] searchDropzone (fid->dkey (:parentId rightClickedElement)))
                         nil)
        selectedElementIds (case (:type rightClickedElement) ;; selectDropzone is: :tabselected, :historyselected, or :dropzone-???
                             :tablink (map :id @(rf/subscribe [:dnd/get-tabs]))
                             :historylink (map :id @(rf/subscribe [:dnd/get-history]))
                             (:link :folderbox :dummy) ;; case syntax for :link, :folderbox, or :dummy (last in column)
                             (map :id @(rf/subscribe [:dnd/dropped-elements selectDropzone]))
                             nil)] 
    (case (:type rightClickedElement) ;; selectDropzone is: :tabselected, :historyselected, or :dropzone-???
      (:tablink :historylink) (rf/dispatch [:dnd/reset-selected selectedElementIds :tab-history selectDropzone])
      (:link :folderbox :dummy) (rf/dispatch [:dnd/reset-selected selectedElementIds selectDropzone])
      nil)))

;; hide the main menu, and optionaly a submenu
(defn hide-menu [menu & [submenu-container]]
  (setstyle (.getElement menu) "display" "block")
  (setstyle (.getElement menu) "visibility" "hidden")
  (when submenu-container (.setVisible submenu-container false))
  (rf/dispatch [:dnd/set-contextmenu-visible false]))

(defn place-context-menu [menu]
  (let [menuElement (.getElement menu)
        mousePosition @(rf/subscribe [:dnd/mouse-position]) ;;{:x more +ve is right, :y more +ve is down}
        xMPos (:x mousePosition)
        yMPos (:y mousePosition)
        ;;(.-clientWidth (.getElementById js/document "my-panel")) ;; excludes scrollbar
        viewportWidth  (.-clientWidth (.-documentElement js/document)) 
        ;;(.-clientHeight (.getElementById js/document "my-panel")) ;; excludes scrollbar
        viewportHeight (.-clientHeight (.-documentElement js/document)) 

        _ (setstyle (.getElement menu) "display" "block")
        _ (setstyle (.getElement menu) "visibility" "hidden")
        
        menuWidth (.-width (.getBoundingClientRect menuElement))
        menuHeight (.-height (.getBoundingClientRect menuElement))]

    (.setPosition menu (- (:x mousePosition) (max 0 (- (+ xMPos menuWidth) viewportWidth)))
                  (- (:y mousePosition) (max 0 (- (+ yMPos menuHeight) viewportHeight))))

    (setstyle (.getElement menu) "display" "block")
    (setstyle (.getElement menu) "visibility" "visible")
    (rf/dispatch [:dnd/set-contextmenu-visible true])))

;; only used for offline bookmark-atom version, online version of show-containing-folder fetches title when getsubtree is called,
;;      or else searches appdb for existing dropzones
;; returns nil if not found, or if dropzones don't exist
(defn find-title-from-id [elementId]
  (let [bookmarks-bar (first (:children (first (get-in @(rf/subscribe [:dnd/db]) [:dnd/state :bookmark-atom]))))
        other-bookmarks (second (:children (first (get-in @(rf/subscribe [:dnd/db]) [:dnd/state :bookmark-atom]))))]
      (cond (= elementId "1") "Bookmarks Bar"
            (= elementId "2") "Other Bookmarks"
            :else
            (first (filter some? (for [subtree [bookmarks-bar other-bookmarks]]
                                   (when-let [find-result (try (find-id (map-vec-zipper subtree) elementId)
                                                               (catch :default e nil))]
                                     (:title (zip/node find-result)))))))))

(defn folder-already-shown-error-msg [elementId]
  (if (.hasOwnProperty js/chrome "bookmarks")
    (let [callbackFunction
          (fn [x]
            (let [parentTitle (if-let [title-found (:title (first (js->clj x :keywordize-keys true)))]
                                (if (> (count title-found) 26) (str (subs title-found 0 26) "...") title-found) ;; truncate string if too long
                                "")]

              (run-fade-alert (str "Folder \"" parentTitle "\" is already shown."))))]  ;; "Folder "" is already shown." if id is not found
      ;; (.. js/chrome -bookmarks (get "1834" (fn [x] (println (js->clj x :keywordize-keys true)) )))
      ;;[{:dateAdded 1596503414220, :id 1834, :index 0, :parentId 1833, :title ntsc to component video processing}]
      ;; nil and in red: "Unchecked runtime.lastError: Can't find bookmark for id." if id not found
      (.. js/chrome -bookmarks (get elementId callbackFunction)))
    
    ;; else offline:
    (let [bookmarks-bar (first (:children (first (get-in @(rf/subscribe [:dnd/db]) [:dnd/state :bookmark-atom]))))
          other-bookmarks (second (:children (first (get-in @(rf/subscribe [:dnd/db]) [:dnd/state :bookmark-atom]))))
          title-found
          (cond (= elementId "1") "Bookmarks Bar"
                (= elementId "2") "Other Bookmarks"
                :else
                (first (filter some? (for [subtree [bookmarks-bar other-bookmarks]]
                                       (when-let [find-result (try (find-id (map-vec-zipper subtree) elementId)
                                                                   (catch :default e nil))]
                                         (:title (zip/node find-result)))))))
          parentTitle (if (> (count title-found) 26) (str (subs title-found 0 26) "...") title-found)]
      (run-fade-alert (str "Folder \"" parentTitle "\" is already shown.")))))

;; for show-folder [elementId rightClickedElement menuPos] rightClickedElement or menuPos can be nil
;; see show-folder-wrapper below for example nil used for rightClickedElement but menuPos not nil
(defn show-folder [parentDropzone rightClickedElement menuPos] ;; menuPos is of the form [100 100]
  (rf/dispatch [:dnd/initialize-drop-zone
                parentDropzone ;; this is the dropzone-id key
                ;; options
                {:folderId (dkey->fid parentDropzone) 
                 :pinned false
                 :menuOpen false
                 :collapsedStartupToggle false
                 :cutoffDropzoneElements defaultCutoffDropzoneElements
                 :selected []
                 :z-index 1}])

  (let [elRef (.getElementById js/document (str "dropped-element-" (:id rightClickedElement)))
        xposition (when elRef (- (:left (dnde/bounding-rect elRef)) 
                                 (:left (dnde/bounding-rect (.getElementById js/document "my-panel")))))
        yposition  (when elRef (- (:top (dnde/bounding-rect elRef))
                                  (:top (dnde/bounding-rect (.getElementById js/document "my-panel")))))

        width (when elRef (.-width (.getBoundingClientRect elRef)))

        menu-xyposition (cond elRef [yposition (+ xposition width)]
                              menuPos menuPos
                              :else [100 100])]

    (prn ["parentDropzone" parentDropzone "rightClickedElement" rightClickedElement "elRef: " elRef])
    (try (if (.hasOwnProperty js/chrome "bookmarks")
           ;; online title is derived when getsubtree is called for online version

           ;;--- (From: dndmenu.cljs) ---
           ;; --- :chrome-synch-all-dropzones-to-folderId-with-menu (fn [{db :db} [_ menu-xyposition show-containing-folder-element-id dropzone-id]
           (rf/dispatch [:chrome-synch-all-dropzones-to-folderId-with-menu menu-xyposition nil (:id rightClickedElement) parentDropzone])
           ;; --- (From: dndmenu.cljs) ---
           ;;  :dnd/synch-all-dropzones-to-folderId-with-menu [_ title menu-xyposition show-containing-folder-element-id dropzone-id]
           (rf/dispatch [:dnd/synch-all-dropzones-to-folderId-with-menu (find-title-from-id (dkey->fid parentDropzone))  
                         menu-xyposition nil (:id rightClickedElement) parentDropzone]))
         ;; tested error => Error Occured:  {:type :custom-arg-error, :message find-id: id was not found}
         (catch :default e (println "Error Occured: " e)))))

;; this is for recently-modified-dropdown component: shows folder or error, at x,y 1/14, 1/3 location on screen
(defn show-folder-wrapper [folderId]
  (let [menuOpenState (rf/subscribe [:dnd/menuOpen-state (fid->dkey folderId)])
        dropzone-options @(rf/subscribe [:dnd/dropzone-options])
        list-of-dropzones (keys dropzone-options)
        screenWidth (.-clientWidth (.getElementById js/document "my-panel"))
        ypos (/ screenWidth 14)
        xpos (/ screenWidth 3)]
    (if (and (nat-int? (.indexOf list-of-dropzones (fid->dkey folderId))) @menuOpenState)
      (folder-already-shown-error-msg folderId)      
      (show-folder (fid->dkey folderId) nil [ypos xpos]))))

(declare restore-view)
;; the paramId is always a folder id or the parent id of a link, or nil
(rf/reg-event-fx
 :dnd/show-folder-from-query-parm
 (fn [_ _]
   (let [paramId (.get (js/URLSearchParams. (.-search (js/URL. (.toString js/window.location)))) "id")
         paramView (.get (js/URLSearchParams. (.-search (js/URL. (.toString js/window.location)))) "view")]
     (cond paramId (show-folder-wrapper paramId)
           paramView (try (restore-view (js/parseInt paramView))
                          (catch :default e (run-fade-alert (str "this view is empty"))))
           :else :do-nothing)
     {:fx []})))
 
;; offline is unhandled currently
;; for show-folder [elementId rightClickedElement menuPos] rightClickedElement or menuPos can be nil
(defn show-parent [elementId]
  (let [showFolderOrErrorFunction
        (fn [parentId] (let [menuOpenState (rf/subscribe [:dnd/menuOpen-state (fid->dkey parentId)])
                             dropzone-options @(rf/subscribe [:dnd/dropzone-options])
                             list-of-dropzones (keys dropzone-options)]
                         (if (and (nat-int? (.indexOf list-of-dropzones (fid->dkey parentId))) @menuOpenState)
                           (folder-already-shown-error-msg parentId)
                           ;; since rightClickedElement and menuPos omited, menu will show at [100 100]
                           (show-folder (fid->dkey parentId) nil nil))))
        
        callbackFunction
        (fn [x]
          (let [requestedElement (first (js->clj x :keywordize-keys true))
                requestedElementParentId (:parentId requestedElement)]
            (showFolderOrErrorFunction requestedElementParentId)))]
    
    (.. js/chrome -bookmarks (get elementId callbackFunction))))

;; check if grandparent exists in appdb and is already displayed then show error, else display grandparent dropzone of rightclicked
(defn show-grand-parent [elementId rightClickedElement] 
  (if (.hasOwnProperty js/chrome "bookmarks")
    (let [showFolderOrErrorFunction
          (fn [grandparentId] (let [menuOpenState (rf/subscribe [:dnd/menuOpen-state (fid->dkey grandparentId)])
                                    dropzone-options @(rf/subscribe [:dnd/dropzone-options])
                                    list-of-dropzones (keys dropzone-options)]
                                (if (and (nat-int? (.indexOf list-of-dropzones (fid->dkey grandparentId))) @menuOpenState)
                                  (folder-already-shown-error-msg grandparentId)      
                                  (show-folder (fid->dkey grandparentId) rightClickedElement nil))))
          
          callbackFunction
          (fn [x]
            (let [requestedElement (first (js->clj x :keywordize-keys true))
                  requestedElementParentId (:parentId requestedElement)]
              (cond (or (= requestedElementParentId "1") (= requestedElementParentId "2"))
                    (showFolderOrErrorFunction requestedElementParentId) 
                    :else
                    (.. js/chrome -bookmarks
                        (get requestedElementParentId
                             (fn [x] (let [requestedElementParent (first (js->clj x :keywordize-keys true))
                                           requestedElementGrandparentId (:parentId requestedElementParent)]
                                       
                                       (showFolderOrErrorFunction requestedElementGrandparentId)

                                       (prn ["requestedElement: " requestedElement
                                             "requestedElementParentId: " requestedElementParentId
                                             "requestedElementParent: " requestedElementParent
                                             "requestedElementGrandparentId: " requestedElementGrandparentId]))))))))]
      (cond (or (= elementId "1") (= elementId "2"))
            (showFolderOrErrorFunction elementId)
            :else
            (.. js/chrome -bookmarks (get elementId callbackFunction))))
    ;;else offline
    ;;offline version searches appdb bookmark-atom instead of appdb :drop-zones so is not affected by search results
    (let [bookmarks-bar (first (:children (first (get-in @(rf/subscribe [:dnd/db]) [:dnd/state :bookmark-atom]))))
          other-bookmarks (second (:children (first (get-in @(rf/subscribe [:dnd/db]) [:dnd/state :bookmark-atom]))))
          getParent (fn [e] (cond (= e "1") "1"
                                  (= e "2") "2"
                                  :else
                                  (first (filter some? (for [subtree [bookmarks-bar other-bookmarks]]
                                                         (when-let [find-result (try (find-id (map-vec-zipper subtree) e)
                                                                                     (catch :default e nil))]
                                                           (:parentId (zip/node find-result))))))))
          grandparentId (getParent (getParent elementId))
          menuOpenState (rf/subscribe [:dnd/menuOpen-state (fid->dkey grandparentId)])
          dropzone-options @(rf/subscribe [:dnd/dropzone-options])
          list-of-dropzones (keys dropzone-options)
          _ (println ["list-of-dropzones" list-of-dropzones
                      "(fid->dkey grandparentId)" (fid->dkey grandparentId)
                      "(and (nat-int? (.indexOf list-of-dropzones (fid->dkey grandparentId))) @menuOpenState)"
                      (and (nat-int? (.indexOf list-of-dropzones (fid->dkey grandparentId))) @menuOpenState)
                      "@menuOpenState" @menuOpenState])
          ]

      (if (and (nat-int? (.indexOf list-of-dropzones (fid->dkey grandparentId))) @menuOpenState)
        (folder-already-shown-error-msg grandparentId)      
        (show-folder (fid->dkey grandparentId) rightClickedElement nil)))))

(defn show-containing-folder []
  (let [rightClickedElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard]))
        parentDropzone (fid->dkey (:parentId rightClickedElement))
        menuOpenState (rf/subscribe [:dnd/menuOpen-state parentDropzone])
        parentTitle (if-let [title-found (find-title-from-id (:parentId rightClickedElement))]
                      (if (> (count title-found) 26) (str (subs title-found 0 26) "...") title-found) ;; truncate string if too long
                      "")
        dropzone-options @(rf/subscribe [:dnd/dropzone-options])
        list-of-dropzones (keys dropzone-options)]

    (cond
      ;; if rightclicked is a search dropzone, then if it exists in appdb and is already displayed show error, else display parent dropzone of rtclkd
      (some? (:searchDropzone rightClickedElement))
      (if (and (nat-int? (.indexOf list-of-dropzones parentDropzone)) @menuOpenState)
        (folder-already-shown-error-msg (:parentId rightClickedElement))
        (show-folder parentDropzone rightClickedElement nil))
      :else
      ;; if a regular dropzone (must be, since first cond has failed) , we know immediate parent already visible, therefore
      ;;    check if grandparent exists in appdb and is already displayed then show error, else display grandparent dropzone of rightclicked
      (show-grand-parent (:id rightClickedElement) rightClickedElement))))



;; if fetch-all-selected returns tabs or history elements, instead of dropzone elements, this still works correctly
(defn fetch-links-of-all-selected []
 (let [rightClickedDropzoneElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard]))
       allSelected (fetch-all-selected) 
       selectedElementList  (cond (and (empty? allSelected) (nil? rightClickedDropzoneElement)) nil ;; fubar
                                  (empty? allSelected) (vector rightClickedDropzoneElement) ;; if nothing selected use rightclicked
                                  ;; use selected:
                                  :else allSelected)
       urlList (for [x selectedElementList] (if (nil? (:children x)) (:url x)
                                                (let [folderChildren (:children x)
                                                      folderChildrenLinksOnly (remove #(some? (:children %) ) folderChildren)]
                                                  (map :url folderChildrenLinksOnly))))]
  
   (flatten urlList)))

;; removeTree is used to delete folders, and removeTree causes refresh to occur only once per selected folder
(defn delete-selected []
  (let [allSelectedElements (fetch-all-selected) ;; list of elements not ids
        _ (clear-all-selections-except-async) ;; deleting the elements does not clear the selections in the appdb, you must do it manually
        rightClickedDropzoneElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard])) 
        ;; eg. #{:dropzone-1 :dropzone-2 :dropzone-5148 :dropzone-2202}
        currDropzones (set (keys @(rf/subscribe [:dnd/dropzone-options])))

        instantiatedSelectedFolders ;; eg. [:dropzone-2068] or []
        ;; keep only folders, take ids, convert to keys, and keep only dropzones with a dropzone-options key
        (filterv some? (map (comp currDropzones fid->dkey :id) (filter (comp not :url) allSelectedElements))) ;; eg. [:dropzone-2068]
        
        ;; eg. nil or :dropzone-2202
        ;; we don't filter for folders because urls don't show up in currDropzones
        instantiatedRightClicked (currDropzones (fid->dkey (:id rightClickedDropzoneElement)))

        closeDestroySubfolderMenusFn (fn [dz]
                                       (let [instantiatedSubfolderIdList
                                             (filterv currDropzones  (conj (map fid->dkey (get-all-subfolders (dkey->fid dz))) dz))]
                                         (doseq [dz instantiatedSubfolderIdList]
                                           (rf/dispatch [:destroy-drop-zone dz])
                                           (destroymenu dz))))]
    
    (if (empty? allSelectedElements)
      (when (:id rightClickedDropzoneElement) ;; delete rightclicked bookmark
        (when instantiatedRightClicked ;; close menu and destroy dropzone in appdb if folder is rightclicked
          (closeDestroySubfolderMenusFn instantiatedRightClicked))
        ;; if bookmarkIdArray is empty delete-drop-zone-elements will not call chrome.bookmarks.remove
        (rf/dispatch [:dnd/delete-drop-zone-elements (vector rightClickedDropzoneElement)]))
      (do
        (when (seq instantiatedSelectedFolders)
          (doseq [destroyFolder instantiatedSelectedFolders]
            (closeDestroySubfolderMenusFn destroyFolder)))
        (rf/dispatch [:dnd/delete-drop-zone-elements allSelectedElements])))))


;; identical to shortcut-copyToClipboard except [:dnd/cutToClipboard] dispatched instead of [:dnd/copyToClipboard]
(declare link-context-menu link-move-submenu-container folder-context-menu folder-move-submenu-container)
(rf/reg-event-fx
 :dnd/shortcut-confirmDelete
 ;; operation is :cut :copy or :delete
 (fn [_ _]
   ;; if contextmenu is displayed:  close context menu then del on existing last rightclicked If rightclicked is tab or history then abort.
   (if (some identity (map #(= (getstyle % "visibility" ) "visible") (array-seq (.querySelectorAll js/document "[class~=goog-menu]"))))
     (let [rightClickedElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard]))]
       (if (#{:tablink :historylink} (:type rightClickedElement)) 
         (do (run-fade-alert "you cannot delete tabs or history") {:fx []})
         (do (hide-menu link-context-menu link-move-submenu-container)
             (hide-menu folder-context-menu folder-move-submenu-container)
             {:fx [[:dispatch [:dnd/confirmDelete]]]})))
     ;; if contextmenu is not displayed: clear rightclicked to nil, and execute del on selected. If any selected are tab or history then abort.
     (let [allSelected (fetch-all-selected)]
       (if (some #(#{:tablink :historylink} (:type %)) allSelected)
         (do (run-fade-alert "you cannot delete tabs or history") {:fx []}) ;; abort
         ;; delete selected
         {:fx [[:dispatch [:dnd/initialize-or-update-clipboard nil nil (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
               [:dispatch [:dnd/confirmDelete]]]})))))

(rf/reg-event-fx
 :dnd/shortcut-esc
 ;; operation is :cut :copy or :delete
 (fn [_ _]
   (hide-menu link-context-menu link-move-submenu-container)
   (hide-menu folder-context-menu folder-move-submenu-container)
   (clear-all-selections-except-async)))

(rf/reg-event-fx
 :dnd/mouseover-cutToClipboard
 (fn [_ _]
   (let [mousePosition @(rf/subscribe [:dnd/mouse-position])
         elementsAtPointArray (js->clj (.elementsFromPoint js/document (:x mousePosition) (:y mousePosition)))
         ;; .-className is a string variable representing the class or space-separated classes of the current element
         ;; [.-id .-className] fetches: for floating: ["menu-dropzone-3471" "Menu"] for embedded: ["menu-dropzone-1" ""]
         ;; for tabhistory: ["menu-tab-history" ""]
         ;; matchResults [(.-id (first matchResults)) (.-className (first matchResults))]

         ;; check for contextmenu collision
         matchContextmenu (filter #(.matches % "div[class^='goog-menu']") elementsAtPointArray)

         
         ;; get dropzone
         matchDropzone (filter #(.matches % "div[id^='menu-dropzone'],div[id='menu-tab-history']") elementsAtPointArray)
         menuDropzoneIds (map #(get-property % "id") matchDropzone)
         matchingDropzone  (keyword (if-not (nil? (first menuDropzoneIds)) (subs (first menuDropzoneIds) 5) "nothingfound"))

         ;; get first dropped-element html #id in elementsatpointarray, or nil if nothing found. Of the form "dropped-element-3475".
         matchHtmlElement (first (filter #(and (.matches % "div[id^='dropped-element']") ) elementsAtPointArray))
         ;; "dropped-element-dummyid3476" -> "dummyid3476", "dropped-element-3475" -> "3475",
         ;; "dropped-element-9000000040" -> "9000000040"
         matchElement (if-not  (nil? matchHtmlElement) (subs (.-id matchHtmlElement) 16) "nothingfound")
         allSelected (fetch-all-selected)]
     (cond
       ;; if :tablink or :historylink is selected abort and display an error
       (some #(#{:tablink :historylink} (:type %)) allSelected)
       (do (run-fade-alert "you cannot cut tabs or history") {:fx []})
       ;; if contextmenu is displayed:  close context menu then cut using last rightclicked element
       (some identity (map #(= (getstyle % "visibility" ) "visible") (array-seq (.querySelectorAll js/document "[class~=goog-menu]"))))
       (do (hide-menu link-context-menu link-move-submenu-container)
           (hide-menu folder-context-menu folder-move-submenu-container)
           (let [rightClickedElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard]))]
             (if (#{:tablink :historylink} (:type rightClickedElement))
               (do (run-fade-alert "you cannot cut tabs or history") {:fx []})
               {:fx [[:dispatch [:dnd/cutToClipboard]]]})))
       ;; if contextmenu is not displayed and hovering nothing: clear rightclicked to nil, and execute copy on fetch-all-selected if any
       (= matchingDropzone :nothingfound)
       {:fx [[:dispatch [:dnd/initialize-or-update-clipboard nil nil (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
             [:dispatch [:dnd/cutToClipboard]]]}
       ;; if contextmenu is not displayed and element of class "dummy-element" is being hovered at highest z-index:
       ;; clear rightclicked to nil, and execute copy on fetch-all-selected if any
       (and (some? matchHtmlElement) (= (.-className matchHtmlElement) "dummy-element"))
       {:fx [[:dispatch [:dnd/initialize-or-update-clipboard nil nil (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
             [:dispatch [:dnd/cutToClipboard]]]}
       ;; if any :tablink or :history link is selected, the cut operation is aborted above. In case anything else is selected it is cut here.
       (= matchingDropzone :tab-history) 
       {:fx [[:dispatch [:dnd/initialize-or-update-clipboard nil nil (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
             [:dispatch [:dnd/cutToClipboard]]]}
       :else
       ;; over a titlebar matchingDropzone dropzone is found, and matchHtmlelement may find an dropped-element beneath it with a correct .-id
       ;; but getDroppedElement is nil because the filter fails to match the element with the dropzone.
       ;; Therefore when getDroppedelement is nil here only cut selected to clipboard
       (let [getDroppedElement (first (filter #(= (:id %) matchElement) @(rf/subscribe [:dnd/dropped-elements matchingDropzone])))]
         (if getDroppedElement
           {:fx [[:dispatch [:dnd/initialize-or-update-clipboard nil getDroppedElement (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
                 [:dispatch [:dnd/cutToClipboard]]]}
           {:fx [[:dispatch [:dnd/initialize-or-update-clipboard nil nil (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
                 [:dispatch [:dnd/cutToClipboard]]]}))))))

;; identical to delete-selected except for run-fade-alert and :dnd/initialize-or-update-clipboard 
(rf/reg-event-fx
 :dnd/cutToClipboard
 (fn [_ _]
   (let [allSelectedElements (fetch-all-selected) ;; list of elements not ids
         _ (clear-all-selections-except-async) ;; deleting the elements does not clear the selections in the appdb, you must do it manually
         rightClickedDropzoneElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard])) 
         ;; eg. #{:dropzone-1 :dropzone-2 :dropzone-5148 :dropzone-2202}
         currDropzones (set (keys @(rf/subscribe [:dnd/dropzone-options])))

         instantiatedSelectedFolders ;; eg. [:dropzone-2068] or []
         ;; keep only folders, take ids, convert to keys, and keep only dropzones with a dropzone-options key
         (filterv some? (map (comp currDropzones fid->dkey :id) (filter (comp not :url) allSelectedElements))) ;; eg. [:dropzone-2068]
         
         ;; eg. nil or :dropzone-2202
         ;; we don't filter for folders because urls don't show up in currDropzones
         instantiatedRightClicked (currDropzones (fid->dkey (:id rightClickedDropzoneElement)))

         closeDestroySubfolderMenusFn (fn [dz]
                                        (let [instantiatedSubfolderIdList
                                              (filterv currDropzones  (conj (map fid->dkey (get-all-subfolders (dkey->fid dz))) dz))]
                                          (doseq [dz instantiatedSubfolderIdList]
                                            (rf/dispatch [:destroy-drop-zone dz])
                                            (destroymenu dz))))]
     
     (if (empty? allSelectedElements)
       (when (:id rightClickedDropzoneElement) ;; delete rightclicked bookmark
         (when instantiatedRightClicked ;; close menu and destroy dropzone in appdb if folder is rightclicked
           (closeDestroySubfolderMenusFn instantiatedRightClicked))
         (run-fade-alert "1 cut")
         (rf/dispatch [:dnd/initialize-or-update-clipboard {:rightClicked rightClickedDropzoneElement :selected (vector rightClickedDropzoneElement)}])
         ;; if bookmarkIdArray is empty delete-drop-zone-elements will not call chrome.bookmarks.remove
         (rf/dispatch [:dnd/delete-drop-zone-elements (vector rightClickedDropzoneElement)]))
       (do
         (when (seq instantiatedSelectedFolders)
           (doseq [destroyFolder instantiatedSelectedFolders]
             (closeDestroySubfolderMenusFn destroyFolder)))
         (run-fade-alert (str (count allSelectedElements) " cut"))
         (rf/dispatch [:dnd/initialize-or-update-clipboard {:rightClicked rightClickedDropzoneElement :selected (vec allSelectedElements)}])
         (rf/dispatch [:dnd/delete-drop-zone-elements allSelectedElements]))))))


(rf/reg-event-fx
 :dnd/mouseover-copyToClipboard
 (fn [_ _]
   (let [mousePosition @(rf/subscribe [:dnd/mouse-position])
         elementsAtPointArray (js->clj (.elementsFromPoint js/document (:x mousePosition) (:y mousePosition)))
         ;; .-className is a string variable representing the class or space-separated classes of the current element
         ;; [.-id .-className] fetches: for floating: ["menu-dropzone-3471" "Menu"] for embedded: ["menu-dropzone-1" ""]
         ;; for tabhistory: ["menu-tab-history" ""]
         ;; matchResults [(.-id (first matchResults)) (.-className (first matchResults))]

         ;; check for contextmenu collision
        matchContextmenu (filter #(.matches % "div[class^='goog-menu']") elementsAtPointArray)

         
         ;; get dropzone
         matchDropzone (filter #(.matches % "div[id^='menu-dropzone'],div[id='menu-tab-history']") elementsAtPointArray)
         menuDropzoneIds (map #(get-property % "id") matchDropzone)
         matchingDropzone  (keyword (if-not (nil? (first menuDropzoneIds)) (subs (first menuDropzoneIds) 5) "nothingfound"))

         ;; get first dropped-element html #id in elementsatpointarray, or nil if nothing found. Of the form "dropped-element-3475".
         matchHtmlElement (first (filter #(and (.matches % "div[id^='dropped-element']") ) elementsAtPointArray))
         ;; "dropped-element-dummyid3476" -> "dummyid3476", "dropped-element-3475" -> "3475",
         ;; "dropped-element-9000000040" -> "9000000040"
         matchElement (if-not  (nil? matchHtmlElement) (subs (.-id matchHtmlElement) 16) "nothingfound")]
     (cond
       ;; if contextmenu is displayed:  close context menu then copy last rightclicked element
       (some identity (map #(= (getstyle % "visibility" ) "visible") (array-seq (.querySelectorAll js/document "[class~=goog-menu]"))))
       (do (hide-menu link-context-menu link-move-submenu-container)
           (hide-menu folder-context-menu folder-move-submenu-container)
           {:fx [[:dispatch [:dnd/copyToClipboard]]]})
       ;; if contextmenu is not displayed and hovering nothing: clear rightclicked to nil, and execute copy on fetch-all-selected if any
       (= matchingDropzone :nothingfound)
       {:fx [[:dispatch [:dnd/initialize-or-update-clipboard nil nil (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
             [:dispatch [:dnd/copyToClipboard]]]} ;; :do-nothing ;; (prn "no dropzone found: matchingDropzone: " matchingDropzone)
       ;; if contextmenu is not displayed and element of class "dummy-element" is being hovered at highest z-index:
       ;; clear rightclicked to nil, and execute copy on fetch-all-selected if any
       (and (some? matchHtmlElement) (= (.-className matchHtmlElement) "dummy-element")) ;; :do-nothing ;; (prn "dummy element found: matchElement: " matchElement)
       {:fx [[:dispatch [:dnd/initialize-or-update-clipboard nil nil (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
             [:dispatch [:dnd/copyToClipboard]]]}
       (= matchingDropzone :tab-history) 
       (if (= :tabselected @(rf/subscribe [:dnd/get-tabOrHistorySelected]))
         (let [getTabElement (first (filter #(= (:id %) matchElement) @(rf/subscribe [:dnd/get-tabs]))) ]
           {:fx [[:dispatch [:dnd/initialize-or-update-clipboard nil getTabElement (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
                 [:dispatch [:dnd/copyToClipboard]]]})
         (let [getHistoryElement (first (filter #(= (:id %) matchElement) @(rf/subscribe [:dnd/get-history]))) ]
           {:fx [[:dispatch [:dnd/initialize-or-update-clipboard nil getHistoryElement (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
                 [:dispatch [:dnd/copyToClipboard]]]}))
       :else
       ;; over a titlebar matchingDropzone dropzone is found, and matchHtmlelement may find an dropped-element beneath it with a correct .-id
       ;; but getDroppedElement is nil because the filter fails to match the element with the dropzone.
       ;; Therefore when getDroppedelement is nil here clear rightclicked to nil, and execute copy on fetch-all-selected if any
       (let [getDroppedElement (first (filter #(= (:id %) matchElement) @(rf/subscribe [:dnd/dropped-elements matchingDropzone])))]
         (if getDroppedElement
           {:fx [[:dispatch [:dnd/initialize-or-update-clipboard nil getDroppedElement (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
                 [:dispatch [:dnd/copyToClipboard]]]}
           {:fx [[:dispatch [:dnd/initialize-or-update-clipboard nil nil (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
                 [:dispatch [:dnd/copyToClipboard]]]}))))))


(rf/reg-event-fx
 :dnd/copyToClipboard
 (fn [_ _]
   (let [allSelectedElements (fetch-all-selected)
         rightClickedElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard]))]
     (if (empty? allSelectedElements)
       ;; note that (vector rightClickedElement) puts the map in a vector
       ;; and (vec allSelectedElements) converts a list of maps to a vector of maps
       (when rightClickedElement
         (run-fade-alert "1 copied")
         (rf/dispatch [:dnd/initialize-or-update-clipboard {:rightClicked rightClickedElement :selected (vector rightClickedElement)}]))
       (do (run-fade-alert (str (count allSelectedElements) " copied"))
           (rf/dispatch [:dnd/initialize-or-update-clipboard {:rightClicked rightClickedElement :selected (vec allSelectedElements)}]))))))

;; newFolder arg is nil or title of folder
(defn move-to-top-or-bottom [& {:keys [topOrBottom newFolder] :or {topOrBottom :top newFolder nil}}]
  (let [allSelectedElements (fetch-all-selected)
        rightClickedDropzoneElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard]))
        ;; rightClickedParentId: try :searchDropzone first, else fall back to :parentId ;; convert :searchDropzoneId :dropzone-2068 -> "2068"
        rightClickedParentId (if-let [rightClickedParentId (:searchDropzone rightClickedDropzoneElement)]
                               (dkey->fid rightClickedParentId)
                               (:parentId rightClickedDropzoneElement))
        searchDropzoneId (if-let [searchDropzoneKey (some :searchDropzone allSelectedElements)] (dkey->fid searchDropzoneKey) nil) ;; string
        apparentParentFolderId (some :parentId allSelectedElements) ;; string like "2068" not dropzone key like :dropzone-2068
        ;; parentFolderId:
        ;; if not a searchdropzone use the parentId of selected elements, if allselectedelements is nil use :parentId of rightclickeddropzoneelement
        ;; string like "2068" not dropzone key like :dropzone-2068
        parentFolderId (or searchDropzoneId apparentParentFolderId rightClickedParentId)
        parentFolderDropzoneElements @(rf/subscribe [:dnd/dropped-elements (fid->dkey parentFolderId)])
        ;; parentFolderDropzoneIdOrder is used to maintain selection order when moving results of a searchDropzoneId
        parentFolderDropzoneIdOrder (map :id parentFolderDropzoneElements)
        source-element-id-array (if (empty? allSelectedElements) (vector (:id rightClickedDropzoneElement))
                                    (sort-by #(.indexOf parentFolderDropzoneIdOrder %) (map :id allSelectedElements)))]
    
    (cond (and (empty? allSelectedElements) (nil? rightClickedDropzoneElement)) (run-fade-alert "Move failed: nothing is selected.")
          (.hasOwnProperty js/chrome "bookmarks")
          (do                       
            (if newFolder
              (.. js/chrome -bookmarks
                  (create (clj->js {"parentId" parentFolderId "index" 0 "title" newFolder})
                          (fn [x]
                            (let [newFolderElement (js->clj x :keywordize-keys true)
                                  newFolderId (:id newFolderElement)]
                              (do
                                (.removeListener js/chrome.bookmarks.onMoved onevent-dispatch-refresh-fnhandle)
                                ;; [source-element-id-array targetParentDropzoneId dropped-position updateDzs]
                                (chrome-move-bookmark-selected-wrapper (reverse source-element-id-array) (fid->dkey newFolderId) 0
                                                                       {:type :updateSome :updateDzs (vector (fid->dkey parentFolderId))}))))))
              ;; else not newFolder , just move to top or bottom
              (chrome-getSubTree-bookmark
               parentFolderId
               (fn [chromeSubtree]
                 ;; take first because chrome returns a vector of a single map
                 (let [subtree (first (js->clj chromeSubtree :keywordize-keys true))
                       bottomIndex (count (:children subtree))]
                   (do (.removeListener js/chrome.bookmarks.onMoved onevent-dispatch-refresh-fnhandle)
                       (chrome-move-bookmark-selected-wrapper (if (= topOrBottom :top) (reverse source-element-id-array) source-element-id-array) 
                                                              (fid->dkey parentFolderId) (if (= topOrBottom :top) 0 bottomIndex)
                                                              {:type :updateSome :updateDzs (vector (fid->dkey parentFolderId))})))))))
          :else
          (do ;; source dropzone is used in recursive-drop-offline-new to call get-dz-element to check for recursive move errors, or
            ;; in :my-drop-dispatch-offline-new to calculate the target index. In this case it is unecessary because target index is 0, and recursive
            ;; drop error is impossible since move is, within same, or to parent folder. Manually do it with stub-move-bookmark
            ;; recursive-drop-dispatch-multiple -> recursive-drop-offline-new -> get-dz-element, :my-drop-dispatch-offline-new -> get-dz-element
            (let [newFolderId (str (+ 99999 (rand-int 100000)))
                  targetParentId (if newFolder newFolderId parentFolderId) ;; make the move target the new folder if it is requested
                  bkmrks (rf/subscribe [:dnd/bookmark-atom])
                  sort-selected (if (empty? allSelectedElements) (vector (:id rightClickedDropzoneElement))
                                    (sort-by #(.indexOf parentFolderDropzoneIdOrder %) (map :id allSelectedElements)))
                  source-element-id-array (if (= topOrBottom :top) (reverse sort-selected) sort-selected)]

              (when newFolder
                (rf/dispatch-sync [:dnd/create-bookmark newFolderId
                                   parentFolderId ;; use or: searchDropzoneId, seleced parent dropzone id, rightclicked parent id
                                   0
                                   newFolder])) ;; newFolder arg is nil or title of folder
              (loop [chopArray source-element-id-array newdb @bkmrks]
                (if (empty? chopArray) 
                  (do (rf/dispatch [:dnd/initialize-bookmark-atom newdb])
                      (rf/dispatch [:dnd/synch-all-dropzones-to-folderId]))
                  (recur (rest chopArray)
                         (let [bottomIndex 
                               (max 0 (dec (count (:children
                                                   (zip/node (find-id (map-vec-zipper @(rf/subscribe [:dnd/bookmark-atom])) parentFolderId))))))]
                           (stub-move-bookmark
                            newdb (first chopArray) :parentId targetParentId :index (if (= topOrBottom :top) 0 bottomIndex)))))))))))


(declare process-bookmarkElement)
(defn process-bookmarkElementVector [bookmarkElementVector newParentId newIndex createElementCounter countSelectedElementList]
  (when (seq bookmarkElementVector)
    (process-bookmarkElement (first bookmarkElementVector) newParentId newIndex createElementCounter countSelectedElementList)
    (process-bookmarkElementVector (rest bookmarkElementVector) newParentId newIndex createElementCounter countSelectedElementList)))

(defn process-bookmarkElement [bookmarkElement newParentId newIndex createElementCounter countSelectedElementList]
  (if (.hasOwnProperty js/chrome "bookmarks")
    (do
      ;; before bookmark created remove onCreated listener
      (if (.hasListener js/chrome.bookmarks.onCreated onevent-dispatch-refresh-fnhandle)
        (.removeListener js/chrome.bookmarks.onCreated onevent-dispatch-refresh-fnhandle))
      (.. js/chrome -bookmarks
          (create (clj->js {"parentId" newParentId "index" newIndex "title" (:title bookmarkElement) "url" (:url bookmarkElement)})
                  (fn [x]
                    (if (:url bookmarkElement)
                      (do (swap! createElementCounter inc)
                          ;; (prn ["title" (:title bookmarkElement) "url" (:url bookmarkElement) "createElementCounter: " @createElementCounter])
                          ;; after bookmark created add back onCreated listener
                          (if (= false (.hasListener js/chrome.bookmarks.onCreated onevent-dispatch-refresh-fnhandle))
                            (.addListener js/chrome.bookmarks.onCreated onevent-dispatch-refresh-fnhandle))
                          ;; if complete refresh all
                          (when (= @createElementCounter countSelectedElementList)
                            (rf/dispatch [:chrome-synch-all-dropzones-to-folderId {:type :updateAll}]) ))
                      (let [newFolderElement (js->clj x :keywordize-keys true)
                            newFolderId (:id newFolderElement)]
                        (swap! createElementCounter inc)
                        ;; (prn ["title" (:title bookmarkElement) "url" (:url bookmarkElement) "createElementCounter: " @createElementCounter])
                        ;; after bookmark created add back onCreated listener
                        (if (= false (.hasListener js/chrome.bookmarks.onCreated onevent-dispatch-refresh-fnhandle))
                          (.addListener js/chrome.bookmarks.onCreated onevent-dispatch-refresh-fnhandle))
                        ;; if complete refresh all
                        (when (= @createElementCounter countSelectedElementList)
                          (rf/dispatch [:chrome-synch-all-dropzones-to-folderId {:type :updateAll}]))
                        ;; create children: danger use newFolderId not newParentId here
                        (process-bookmarkElementVector (reverse (:children bookmarkElement)) newFolderId 0 createElementCounter
                                                       countSelectedElementList)))))))
    ;; else offline
    (let [newId (str (+ 99999 (rand-int 100000)))]
      (if (:url bookmarkElement)
        ;;                [:dnd/create-bookmark   id      parentId    index      title                    url]
        (rf/dispatch [:dnd/create-bookmark        newId   newParentId newIndex (:title bookmarkElement) (:url bookmarkElement)])
        ;; else it is a folder
        (do
          ;; create subfolder and process children
          ;;           [:dnd/create-bookmark  id    parentId    index       title                    url]
          (rf/dispatch [:dnd/create-bookmark  newId newParentId newIndex  (:title bookmarkElement)   nil])
          ;; create children: ;; danger use newId not newParentId here
          (process-bookmarkElementVector (reverse (:children bookmarkElement)) newId 0 createElementCounter countSelectedElementList)))))) 

(rf/reg-event-fx
 :dnd/pasteFromClipboard
 (fn [_ [_ location]]
   (let [clipboardContents @(rf/subscribe [:dnd/get-clipboard])
         rightClickedElement (:rightClicked clipboardContents)
         selectedElementList (reverse (:selected clipboardContents))
         countSelectedElementList (reduce + (map #(loop [loc (map-vec-zipper %) count 0]
                                                    (if (not (zip/end? loc))
                                                      (recur (zip/next loc) (inc count))
                                                      count))
                                                 selectedElementList))
         createElementCounter (r/atom 0)]
     (when (> (count selectedElementList) 0) (run-fade-alert (str (count selectedElementList) " pasted")))
     (process-bookmarkElementVector selectedElementList ((if (= location :above) :parentId :id) rightClickedElement)
                                    (if (= location :above) (:index rightClickedElement) 0)
                                    createElementCounter countSelectedElementList))))

(declare link-context-menu link-move-submenu-container folder-context-menu folder-move-submenu-container)
(rf/reg-event-fx
 :dnd/mouseover-pasteFromClipboard
 (fn [_ _]
   (let [mousePosition @(rf/subscribe [:dnd/mouse-position])
         elementsAtPointArray (js->clj (.elementsFromPoint js/document (:x mousePosition) (:y mousePosition)))
         ;; .-className is a string variable representing the class or space-separated classes of the current element
         ;; [.-id .-className] fetches: for floating: ["menu-dropzone-3471" "Menu"] for embedded: ["menu-dropzone-1" ""]
         ;; for tabhistory: ["menu-tab-history" ""]
         ;; matchResults [(.-id (first matchResults)) (.-className (first matchResults))]

         ;; check for contextmenu collision
         matchContextmenu (filter #(.matches % "div[class^='goog-menu']") elementsAtPointArray)

         
         ;; get dropzone
         matchDropzone (filter #(.matches % "div[id^='menu-dropzone'],div[id='menu-tab-history']") elementsAtPointArray)
         menuDropzoneIds (map #(get-property % "id") matchDropzone)
         matchingDropzone  (keyword (if-not (nil? (first menuDropzoneIds)) (subs (first menuDropzoneIds) 5) "nothingfound"))

         ;; get first dropped-element html #id in elementsatpointarray, or nil if nothing found. Of the form "dropped-element-3475".
         matchHtmlElement (first (filter #(.matches % "div[id^='dropped-element']")  elementsAtPointArray))
         ;; "dropped-element-dummyid3476" -> "dummyid3476", "dropped-element-3475" -> "3475",
         ;; "dropped-element-9000000040" -> "9000000040"
         matchElement (if-not  (nil? matchHtmlElement) (subs (.-id matchHtmlElement) 16) "nothingfound")
         allSelected (fetch-all-selected)]
     (cond ;; many cases included in case I want to change specific behaviour in the future
       ;; if contextmenu is displayed:  close context menu and paste using last rightclicked element unless tab or history has been rightclicked
       (some identity (map #(= (getstyle % "visibility" ) "visible") (array-seq (.querySelectorAll js/document "[class~=goog-menu]"))))
       (do (hide-menu link-context-menu link-move-submenu-container)
           (hide-menu folder-context-menu folder-move-submenu-container)
           (let [rightClickedElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard]))]
               (if (#{:tablink :historylink} (:type rightClickedElement))
              (do (run-fade-alert "you cannot paste to tabs or history") {:fx []})
              ;; if rightclicked is a url, or a blank element, paste above, else it's a folder and paste inside
              ;; views.cljs defmethod dropped-widget :blank :on-context-menu ... {:type :blank, :id "blank-element1", :parentId "4418"}
              (if (or (:url rightClickedElement) (= :blank (:type rightClickedElement)))
                {:fx [[:dispatch [:dnd/pasteFromClipboard :above]]]}
                {:fx [[:dispatch [:dnd/pasteFromClipboard :in]]]}))))
       ;; if contextmenu is not displayed and hovering nothing: do nothing
       (= matchingDropzone :nothingfound) {:fx []}

       ;; if contextmenu is not displayed and element of class "dummy-element" is being hovered at highest z-index: 
       ;; if data-lastincol is "false" do nothing, else paste
       ;; if data-lastincol is not "false" then :dummy widget encodes the element with (pr-str de) into data-lastincol to be decoded
       ;; below with cljs.reader/read-string
       ;; see: (defmethod dropped-widget :dummy ...)  :on-context-menu, initialize-or-update-clipboard ...
       ;; lastincol dummy widget looks like: {:id "dummyid3472" :type :dummy :index 7 :parentId "3471" :last-in-col true}


       (and (some? matchHtmlElement) (= (.-className matchHtmlElement) "dummy-element"))
       (let [data-lastincol (.getAttribute matchHtmlElement "data-lastincol")]
         (if (= data-lastincol "false") {:fx []}
             {:fx [[:dispatch [:dnd/initialize-or-update-clipboard    nil (cljs.reader/read-string data-lastincol)
                               (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
                   [:dispatch [:dnd/pasteFromClipboard :above]]]}))
       
       
       
       ;; if any :tablink or :history link is being hovered do nothing
       (= matchingDropzone :tab-history) (do (run-fade-alert "you cannot paste to tabs or history") {:fx []})

       ;; ctrl-v while hovering a :blank element
       (let [firstPair (take 2 elementsAtPointArray)]
         (case (count firstPair)
           2 (when
                 (or
                  ;; first OR condition matches case hovering directly over blank element: 
                  ;; [.-id .-className]: (["blank-element-blank-element1" "blank-element"] ["drop-zone-dropzone-3479" "drop-zone-floating"] ...)
                  (= (.-className (first firstPair)) "blank-element")
                  ;; second OR condition matches case hovering directly over drop marker in the blank element:
                  ;; [.-id .-className]: (["" "blank-drop-marker"] ["blank-element-blank-element1" "blank-element"] ... )
                  (and (= (.-className (first firstPair)) "blank-drop-marker")
                       (= (.-className (second firstPair)) "blank-element")))
               "2 elements found")
           1 (when (= (.-className (first firstPair)) "blank-element") "one element found")
           nil))
       {:fx [[:dispatch [:dnd/initialize-or-update-clipboard    nil
                         {:type :blank :id "blank-element1" :index 0 :parentId (dkey->fid matchingDropzone)}
                         (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
             [:dispatch [:dnd/pasteFromClipboard :above]]]}
       :else
       ;; When mouse is over a titlebar matchingDropzone dropzone is found, and matchHtmlelement may find an dropped-element beneath it (<z-index) 
       ;; with a correct .-id, but getDroppedElement is nil because the filter fails to match the element with the dropzone.
       ;; Therefore when getDroppedelement is nil here do nothing, because it means the matchHtmlelement doesn't correspond to the correct
       ;; matchingDropzone. (ie. because it is actually in another different unrelated dropzone beneath it z-index-wise).**
       (let [getDroppedElement (first (filter #(= (:id %) matchElement) @(rf/subscribe [:dnd/dropped-elements matchingDropzone])))]
         (cond (= (:type getDroppedElement) :link)
               {:fx [[:dispatch [:dnd/initialize-or-update-clipboard nil getDroppedElement (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
                     [:dispatch [:dnd/pasteFromClipboard :above]]]}
               (= (:type getDroppedElement) :folderbox)
               {:fx [[:dispatch [:dnd/initialize-or-update-clipboard nil getDroppedElement (:selected @(rf/subscribe [:dnd/get-clipboard]))]]
                     [:dispatch [:dnd/pasteFromClipboard :in]]]}
               ;; do nothing if hovering titlebar as discussed above **
               (nil? getDroppedElement) {:fx []}))))))

(defn open-in-tabs []
  (let [allSelected (fetch-links-of-all-selected)
        rightClickedURL (:url (:rightClicked @(rf/subscribe [:dnd/get-clipboard])))]
    (if (= 0 (count allSelected))
      (if (.hasOwnProperty js/chrome "bookmarks")
        (.create js/chrome.tabs #js {"active" false "url" rightClickedURL})
        (.open js/window rightClickedURL "_blank"))
      (if (.hasOwnProperty js/chrome "bookmarks")
        (doseq [url allSelected]
          (.create js/chrome.tabs #js {"active" false "url" url}))
        (doseq [url allSelected]
          (.open js/window url "_blank"))))))


;; << link context menu >>

(defn generate-menuitem-accel [title accelerator id]
  (let [shortcutkeyDiv (.createElement js/document "div")
        _  (.appendChild shortcutkeyDiv (.createTextNode js/document accelerator))
        _  (.add (.-classList shortcutkeyDiv ) "goog-menuitem-accel")
        menuitem-caption (.createTextNode js/document title)
        node-array (clj->js [shortcutkeyDiv menuitem-caption])]
    (doto (new goog.ui.MenuItem node-array) (.setId id))))

(defn setCaptionAndAccelerator [menuitem title accelerator]
  (let [shortcutkeyDiv (.createElement js/document "div")
        _  (.appendChild shortcutkeyDiv (.createTextNode js/document accelerator))
        _  (.add (.-classList shortcutkeyDiv ) "goog-menuitem-accel")
        menuitem-caption (.createTextNode js/document title)
        node-array (clj->js [shortcutkeyDiv menuitem-caption])]
    (.setContent menuitem node-array)))



;; link context menu --------------------------------------------------------------------------------------------------------------------------
  
(def link-context-menu (doto (new goog.ui.Menu) (.setId "link-context-menu")))

(def link-newlink-menuitem (doto (new goog.ui.MenuItem "New page...") (.setId "link-newlink-menuitem")))

(def link-newfolder-menuitem (doto (new goog.ui.MenuItem "New folder...") (.setId "link-newfolder-menuitem")))

;; link-insertdivider-menuitem: dynamically count links to open? including subfolders? ;; warn if too many, "Open All" if folder
(def link-insertdivider-menuitem (doto (new goog.ui.MenuItem "Insert divider...") (.setId "link-insertdivider-menuitem")))


(def link-sep1 (new goog.ui.MenuSeparator))

(def link-rename-menuitem (generate-menuitem-accel "Rename..." "F2" "link-rename-menuitem"))

(def link-sep2 (new goog.ui.MenuSeparator))

(def link-open-menuitem  (doto (new goog.ui.MenuItem "Open in new tab") (.setId "link-open-menuitem"))) 
(def link-show-menuitem  (doto (new goog.ui.MenuItem "Show parent folder") (.setId "link-show-menuitem")))


;; ------------------------------------------START SUBMENU

(def link-move-submenu (doto (new goog.ui.SubMenu "Move to ...") (.setId "link-move-submenu")))
(def link-move-submenu-top (doto (new goog.ui.MenuItem "Top of this folder") (.setId "link-move-submenu-top")))
(def link-move-submenu-bottom (doto (new goog.ui.MenuItem "Bottom of this folder") (.setId "link-move-submenu-bottom")))
(def link-move-submenu-new (doto (new goog.ui.MenuItem "New folder ...") (.setId "link-move-submenu-new")))

(def link-move-submenu-container (doto (new goog.ui.Menu) (.setId "link-move-submenu-container")))
;; the items are added to a seperate menu container which is then set to a submenu, so that the menu container can stop propagation
;; otherwise, clicking a submenu item would propagate up to the global click handler which would close the parent menu and leave submenu container
;; visible. Note that the submenu is an element of the main menu, the submenu items are by default added to an anonymous container which is
;; by default added at the end of body, but which I render explicitly in dndmenu.cljs (defn mount-dndmenu).
(.addChild link-move-submenu-container link-move-submenu-top true)
(.addChild link-move-submenu-container link-move-submenu-bottom true)
(.addChild link-move-submenu-container link-move-submenu-new true)

(.setMenu link-move-submenu link-move-submenu-container)

;; ------------------------------------------END SUBMENU


(def link-sep3 (new goog.ui.MenuSeparator))


(def link-selectall-menuitem (generate-menuitem-accel "Select all" "Ctrl+A" "link-selectall-menuitem"))


(def link-sep4 (new goog.ui.MenuSeparator))

(def link-cut-menuitem (generate-menuitem-accel "Cut" "Ctrl+X" "link-cut-menuitem"))
(def link-copy-menuitem (generate-menuitem-accel "Copy" "Ctrl+C" "link-copy-menuitem"))
(def link-paste-menuitem (generate-menuitem-accel "Paste" "Ctrl+V" "link-paste-menuitem"))

(def link-sep5 (new goog.ui.MenuSeparator))

(def link-delete-menuitem (generate-menuitem-accel "Delete" "Del" "link-delete-menuitem"))


(doto link-context-menu
  (.addChild link-newlink-menuitem true)
  (.addChild link-newfolder-menuitem true)
  (.addChild link-insertdivider-menuitem true)
  (.addChild link-sep1 true)
  (.addChild link-rename-menuitem true)
  (.addChild link-sep2 true)
  (.addChild link-open-menuitem true)
  (.addChild link-show-menuitem true)
  (.addChild link-move-submenu true)
  (.addChild link-sep3 true)
  (.addChild link-selectall-menuitem true)
  (.addChild link-sep4 true)
  (.addChild link-cut-menuitem true)
  (.addChild link-copy-menuitem true)
  (.addChild link-paste-menuitem true)
  (.addChild link-sep5 true)
  (.addChild link-delete-menuitem true)
  (.setPosition 0 0)
  (.setVisible false))

;; without this left clicking the menu will bubble up to the document level and hide the menu after registering the action (maybe useful?)
(.addEventListener (.getElement link-context-menu) "click" #(.stopPropagation %))
;; If you don't stop click propagation on the submenu, the click will propagate to the document and the global handler will close the main menu
;; Also by default parentElement of the submenu is unnamed and placed at the bottom of <body>. Now I render it explicitly in dndmenu.cljs
;; (defn mount-dndmenu) at the bottom of my-panel just like the main context menu.
(.addEventListener (.getElement link-move-submenu-container) "click" #(.stopPropagation %))




;; << folder context menu >>
;; folder context menu --------------------------------------------------------------------------------------------------------------------------
  
(def folder-context-menu (doto (new goog.ui.Menu) (.setId "folder-context-menu")))

;; create a new link at the top of folder
(def folder-newlink-menuitem (doto (new goog.ui.MenuItem "New page...") (.setId "folder-newlink-menuitem")))

;; create a new folder at the top of folder
(def folder-newfolder-menuitem (doto (new goog.ui.MenuItem "New folder...") (.setId "folder-newfolder-menuitem")))

(def folder-insertdivider-menuitem (doto (new goog.ui.MenuItem "Insert divider...") (.setId "folder-insertdivider-menuitem")))

(def folder-sep1 (new goog.ui.MenuSeparator))

(def folder-rename-menuitem (generate-menuitem-accel "Rename..." "F2" "folder-rename-menuitem")) ;; Rename if folder


(def folder-sep2 (new goog.ui.MenuSeparator))


(def folder-open-menuitem  (doto (new goog.ui.MenuItem "Open in new tab") (.setId "folder-open-menuitem"))) 
(def folder-show-menuitem  (doto (new goog.ui.MenuItem "Show parent folder") (.setId "folder-show-menuitem")))


;; ------------------------------------------START SUBMENU

(def folder-move-submenu (doto (new goog.ui.SubMenu "Move to ...") (.setId "folder-move-submenu")))
(def folder-move-submenu-top (doto (new goog.ui.MenuItem "Top of this folder") (.setId "folder-move-submenu-top")))
(def folder-move-submenu-bottom (doto (new goog.ui.MenuItem "Bottom of this folder") (.setId "folder-move-submenu-bottom")))
(def folder-move-submenu-new (doto (new goog.ui.MenuItem "New folder ...") (.setId "folder-move-submenu-new")))

(def folder-move-submenu-container (doto (new goog.ui.Menu) (.setId "folder-move-submenu-container")))
;; the items are added to a seperate menu container which is then set to a submenu, so that the menu container can stop propagation
;; otherwise, clicking a submenu item would propagate up to the global click handler which would close the parent menu and leave submenu container
;; visible. Note that the submenu is an element of the main menu, the submenu items are by default added to an anonymous container which is
;; by default added at the end of body, but which I render explicitly in dndmenu.cljs (defn mount-dndmenu).
(.addChild folder-move-submenu-container folder-move-submenu-top true)
(.addChild folder-move-submenu-container folder-move-submenu-bottom true)
(.addChild folder-move-submenu-container folder-move-submenu-new true)

(.setMenu folder-move-submenu folder-move-submenu-container)

;; ------------------------------------------END SUBMENU


(def folder-sep3 (new goog.ui.MenuSeparator))

(def folder-selectall-menuitem (generate-menuitem-accel "Select all" "Ctrl-A" "folder-selectall-menuitem"))

(def folder-sep4 (new goog.ui.MenuSeparator))
(def folder-cut-menuitem (generate-menuitem-accel "Cut" "Ctrl-X" "folder-cut-menuitem"))
(def folder-copy-menuitem (generate-menuitem-accel "Copy" "Ctrl-C" "folder-copy-menuitem"))
(def folder-paste-above-menuitem  (doto (new goog.ui.MenuItem "Paste (above)") (.setId "folder-paste-above-menuitem")))
(def folder-paste-in-menuitem (generate-menuitem-accel "Paste" "Ctrl-V" "folder-paste-in-menuitem"))
(def folder-sep5 (new goog.ui.MenuSeparator))

(def folder-delete-menuitem (generate-menuitem-accel "Delete" "Del" "folder-delete-menuitem"))

(doto folder-context-menu
  (.addChild folder-newlink-menuitem true)
  (.addChild folder-newfolder-menuitem true)
  (.addChild folder-insertdivider-menuitem true)
  (.addChild folder-sep1 true)
  (.addChild folder-rename-menuitem true)
  (.addChild folder-sep2 true)
  (.addChild folder-open-menuitem true)
  (.addChild folder-show-menuitem true)
  (.addChild folder-move-submenu true)
  (.addChild folder-sep3 true)
  (.addChild folder-selectall-menuitem true)
  (.addChild folder-sep4 true)
  (.addChild folder-cut-menuitem true)
  (.addChild folder-copy-menuitem true)
  (.addChild folder-paste-above-menuitem true)
  (.addChild folder-paste-in-menuitem true)
  (.addChild folder-sep5 true)
  (.addChild folder-delete-menuitem true)
  (.setPosition 0 0)
  (.setVisible false))

;; without this left clicking the menu will bubble up to the document level and hide the menu after registering the action (maybe useful?)
(.addEventListener (.getElement folder-context-menu) "click" #(.stopPropagation %))
;; If you don't stop click propagation on the submenu, the click will propagate to the document and the global handler will close the main menu
;; Also by default parentElement of the submenu is unnamed and placed at the bottom of <body>. Now I render it explicitly in dndmenu.cljs
;; (defn mount-dndmenu) at the bottom of my-panel just like the main context menu.
(.addEventListener (.getElement folder-move-submenu-container) "click" #(.stopPropagation %))


;; << confirmation dialog >>
;; confirmDialog -----------------------------------------------------------------------------------------------------------

 (def confirmDialog (new goog.ui.Dialog nil true))

;; (.setTitle confirmDialog "Delete Multiple Items")
(.remove (.getTitleElement confirmDialog))
(setstyle (.getContentElement confirmDialog) "padding-bottom" "0px")

(def totalDeleteElements (r/atom 0))

;; copiedOrRightClicked is :selected or :rightClicked
(defn count-rightclicked-or-selected [copiedOrRightClicked] 
  (let [selectedElementList (if (= copiedOrRightClicked :selected)
                              (vec (fetch-all-selected))
                              (remove nil? (vector (:rightClicked @(rf/subscribe [:dnd/get-clipboard])))))]
    (reduce + (map #(loop [loc (map-vec-zipper %) count 0]
                      (if (not (zip/end? loc))
                        (recur (zip/next loc) (inc count))
                        count))
                   selectedElementList))))

;; preload image
(doto (js/Image.) (setattrib "src" "images/warning128.png"))


(defn resetConfirmDialog [count]
  (reset! totalDeleteElements count)
  (.setSafeHtmlContent confirmDialog
                       (let [thirdOfScreen (int (* (.-innerWidth js/window) 0.3))]
                         (goog.html.SafeHtml.create "div" #js {"style" #js {"width" (str thirdOfScreen "px") "display" "flex"
                                                                            "justify-content" "flex-start" "align-items" "center"
                                                                            "gap" (str (int (/ thirdOfScreen 15)) "px")}}
                                                    (goog.html.SafeHtml.concat
                                                     (goog.html.SafeHtml.create "img" #js {"src" "images/warning128.png"})
                                                     (goog.html.SafeHtml.create "div" #js {"style" #js {"font-size" "1.5vw"}}
                                                                                (str "Are you sure you want to delete " @totalDeleteElements
                                                                                     " items?") )))))
  (.setVisible confirmDialog true))

(rf/reg-event-fx
 :dnd/confirmDelete
 (fn [_ _]
   (let [allSelectedElements (fetch-all-selected)
         rightClickedDropzoneElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard]))]
     (if (empty? allSelectedElements)
       ;; if nothing is selected, check if rightclicked element passes the threshold
       (let [countRightClicked (count-rightclicked-or-selected :rightClicked)]
         (if (> countRightClicked 10)
           (resetConfirmDialog countRightClicked)
           (delete-selected)))
       ;; if something is selected, check if selected elements pass the threshold
       (let [countSelected (count-rightclicked-or-selected :selected)]
         (if (> countSelected 10)
           (resetConfirmDialog countSelected)
           (delete-selected)))))))



(.setButtonSet confirmDialog
               (doto (new goog.ui.Dialog.ButtonSet)
                 (.addButton #js {"caption" "Cancel" "key" "cancel"} false true)
                 (.addButton #js {"caption" "Delete" "key" "delete"} true false)))

(.setHasTitleCloseButton confirmDialog false)

;; ["from confirmDialog: clickedButton: " "cancel"]
;; ["from confirmDialog: clickedButton: " "delete"]

(goog.events.listen confirmDialog goog.ui.Dialog.EventType.SELECT
                    (fn [e] (let [clickedButton (.-key e)]
                              (when (= clickedButton "delete") (delete-selected)))))


;; << new folder dialog >>
;; newFolderDialog -----------------------------------------------------------------------------------------------------------
 
;;goog.ui.Dialog constructor: arg1: class name prefix defaults to modal-dialog, arg2: use iframe instead of z-index true or false 
(def newFolderDialog (new goog.ui.Dialog nil true)) 
(.setTitle newFolderDialog "New Folder")


(.setSafeHtmlContent
 newFolderDialog
 (let [thirdOfScreen (int (* (.-innerWidth js/window) 0.3))]
   (goog.html.SafeHtml.concat
    (goog.html.SafeHtml.create "label" #js {"for" "newfolder" "style" #js {"font-size" ".625rem" "font-weight" "500"}} "Name")
    (goog.html.SafeHtml.create "div" #js {"class" "flexbox"}
                               (goog.html.SafeHtml.create "div" #js {"class" "underline"}
                                                          (goog.html.SafeHtml.create
                                                           "input" #js {"id" "newfolder" "class" "inputDialog" "type" "text"
                                                                        "style" #js {"width" (str thirdOfScreen "px")}} nil))))))


(.setButtonSet newFolderDialog (doto (new goog.ui.Dialog.ButtonSet)
                         (.addButton #js {"caption" "Cancel" "key" "cancel"} false true)
                         (.addButton #js {"caption" "Save" "key" "save"} true false)))

(.setHasTitleCloseButton newFolderDialog false)

(defn add-underline [e] (.add (.-classList (.-parentElement (.-currentTarget e))) "runanimation"))
(defn remove-underline [e] (.remove  (.-classList (.-parentElement (.-currentTarget e))) "runanimation"))

;; (defn add-underline [e] (.add (.-classList (.querySelector (.getElement newFolderDialog) "div.underline")) "runanimation"))
;; (defn remove-underline [e] (.remove (.-classList (.querySelector (.getElement newFolderDialog) "div.underline")) "runanimation"))

(goog.events.listen newFolderDialog goog.ui.Dialog.EventType.SELECT
                    (fn [e] (let [inputValue (.-value (.querySelector (.getElement newFolderDialog) "input.inputDialog"))
                                  clickedButton (.-key e)
                                  rightClickedDropzoneElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard]))]
                              (when (= clickedButton "save")
                                ;; :dnd/create-bookmark [_ id parentId index title url]
                                (rf/dispatch [:dnd/create-bookmark (str (+ 99999 (rand-int 100000)))
                                              (:parentId rightClickedDropzoneElement)
                                              (:index rightClickedDropzoneElement)
                                              inputValue]))
                              (setattrib (.querySelector (.getElement newFolderDialog) "input.inputDialog")  "value" ""))))


(goog.events.listen newFolderDialog goog.ui.Dialog.EventType.AFTER_SHOW
                    (fn [e] 
                      (.addEventListener (.querySelector (.getElement newFolderDialog) "input.inputDialog") "focus" add-underline)
                      (.addEventListener (.querySelector (.getElement newFolderDialog) "input.inputDialog") "blur" remove-underline)
                      (.focus (.querySelector (.getElement newFolderDialog) "input.inputDialog"))))

(goog.events.listen newFolderDialog goog.ui.Dialog.EventType.HIDE
                    (fn [e]
                      (.removeEventListener (.querySelector (.getElement newFolderDialog) "input.inputDialog") "focus" add-underline)
                      (.removeEventListener (.querySelector (.getElement newFolderDialog) "input.inputDialog") "blur" remove-underline)))


;; << new page dialog >>
;; newPageDialog -----------------------------------------------------------------------------------------------------------

;;goog.ui.Dialog constructor: arg1: class name prefix defaults to modal-dialog, arg2: use iframe instead of z-index true or false 
(def newPageDialog (new goog.ui.Dialog nil true)) 
(.setTitle newPageDialog "New Page")

(.setSafeHtmlContent
 newPageDialog
 (let [thirdOfScreen (int (* (.-innerWidth js/window) 0.3))]
   (goog.html.SafeHtml.concat
    (goog.html.SafeHtml.create "label" #js {"for" "getName" "style" #js {"font-size" ".625rem" "font-weight" "500"}} "Name")
    (goog.html.SafeHtml.create "div" #js {"class" "flexbox" "style" #js {"margin-bottom" "10px"}}
                               (goog.html.SafeHtml.create "div" #js {"class" "underline"}
                                                          (goog.html.SafeHtml.create
                                                           "input" #js {"class" "inputDialog" "id" "getName" "type" "text"
                                                                        "style" #js {"width" (str thirdOfScreen "px")}} nil)))
    (goog.html.SafeHtml.create "label" #js {"for" "getURL" "style" #js {"font-size" ".625rem" "font-weight" "500"}}
                               "URL (eg. https://www.google.com)")
    (goog.html.SafeHtml.create "div" #js {"class" "flexbox"}
                               (goog.html.SafeHtml.create "div" #js {"class" "underline"}
                                                          (goog.html.SafeHtml.create
                                                           "input" #js {"class" "inputDialog" "id" "getURL" "type" "url" "value" "https://"
                                                                        "style" #js {"width" (str thirdOfScreen "px")}} nil))))))


(.setButtonSet newPageDialog (doto (new goog.ui.Dialog.ButtonSet)
                         (.addButton #js {"caption" "Cancel" "key" "cancel"} false true)
                         (.addButton #js {"caption" "Save" "key" "save"} true false)))

(.setHasTitleCloseButton newPageDialog false)


(goog.events.listen newPageDialog goog.ui.Dialog.EventType.SELECT
                    (fn [e] (let [inputNameValue (.-value (.querySelector (.getElement newPageDialog) "#getName"))
                                  inputURLValue  (.-value (.querySelector (.getElement newPageDialog) "#getURL"))
                                  clickedButton (.-key e)
                                  rightClickedDropzoneElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard]))]
                              (when (= clickedButton "save")
                                (if (clojure.string/blank? inputURLValue)
                                  (run-fade-alert "New Page failed, invalid url.")
                                  ;; :dnd/create-bookmark [_ id parentId index title url]
                                  (rf/dispatch [:dnd/create-bookmark (str (+ 99999 (rand-int 100000)))
                                                (:parentId rightClickedDropzoneElement)
                                                (:index rightClickedDropzoneElement)
                                                inputNameValue
                                                (if (nil? (re-find #"://" inputURLValue)) (str "https://" inputURLValue) inputURLValue)])))
                              ;; clear fields for next time newPagedialog is invoked
                              (setattrib (.querySelector (.getElement newPageDialog) "#getName")  "value" "")
                              (setattrib (.querySelector (.getElement newPageDialog) "#getURL")  "value" "")))) 


(goog.events.listen newPageDialog goog.ui.Dialog.EventType.AFTER_SHOW
                    (fn [e] 
                      (.addEventListener (.querySelector (.getElement newPageDialog) "#getName") "focus" add-underline)
                      (.addEventListener (.querySelector (.getElement newPageDialog) "#getName") "blur" remove-underline)
                      (.addEventListener (.querySelector (.getElement newPageDialog) "#getURL") "focus" add-underline)
                      (.addEventListener (.querySelector (.getElement newPageDialog) "#getURL") "blur" remove-underline)
                      (.focus (.querySelector (.getElement newPageDialog) "input.inputDialog"))))

(goog.events.listen newPageDialog goog.ui.Dialog.EventType.HIDE
                    (fn [e]
                      (.removeEventListener (.querySelector (.getElement newPageDialog) "#getName") "focus" add-underline)
                      (.removeEventListener (.querySelector (.getElement newPageDialog) "#getName") "blur" remove-underline)
                      (.removeEventListener (.querySelector (.getElement newPageDialog) "#getURL") "focus" add-underline)
                      (.removeEventListener (.querySelector (.getElement newPageDialog) "#getURL") "blur" remove-underline)))


;; << insert divider dialog >>
;; insertDividerDialog -------------------------------------------------------------------------------------------------------------

;;goog.ui.Dialog constructor: arg1: class name prefix defaults to modal-dialog, arg2: use iframe instead of z-index true or false 
(def insertDividerDialog (new goog.ui.Dialog nil true)) 
(.setTitle insertDividerDialog "Insert Divider")

(.setSafeHtmlContent
 insertDividerDialog
 (let [thirdOfScreen (int (* (.-innerWidth js/window) 0.3))]
   (goog.html.SafeHtml.concat
    (goog.html.SafeHtml.create "label" #js {"for" "getDivider" "style" #js {"font-size" ".625rem" "font-weight" "500"}}
                               "Divider Title (blank for straight line)")
    (goog.html.SafeHtml.create "div" #js {"class" "flexbox"}
                               (goog.html.SafeHtml.create "div" #js {"class" "underline"}
                                                          (goog.html.SafeHtml.create
                                                           "input" #js {"class" "inputDialog" "id" "getDivider" "type" "text"
                                                                        "style" #js {"width" (str thirdOfScreen "px")}} nil))))))


(.setButtonSet insertDividerDialog (doto (new goog.ui.Dialog.ButtonSet)
                         (.addButton #js {"caption" "Cancel" "key" "cancel"} false true)
                         (.addButton #js {"caption" "Save" "key" "save"} true false)))

(.setHasTitleCloseButton insertDividerDialog false)

(defn make-divider [x]
 (let [mystring x
       inputLength (count mystring)
       branchLength (/ (- 34 inputLength) 2)
       Lbranch (apply str (repeat (js/Math.floor branchLength) \u2500))
       Rbranch (apply str (repeat (js/Math.ceil branchLength) \u2500))
       ]
   (str Lbranch mystring Rbranch)))

(goog.events.listen insertDividerDialog goog.ui.Dialog.EventType.SELECT
                    (fn [e] (let [inputDividerValue (.-value (.querySelector (.getElement insertDividerDialog) "#getDivider"))
                                  clickedButton (.-key e)
                                  rightClickedDropzoneElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard]))]
                              (when (= clickedButton "save")
                                ;; :dnd/create-bookmark [_ id parentId index title url]
                                (rf/dispatch [:dnd/create-bookmark (str (+ 99999 (rand-int 100000)))
                                              (:parentId rightClickedDropzoneElement)
                                              (:index rightClickedDropzoneElement)
                                              (make-divider inputDividerValue)
                                              (str "chrome://bookmarks/?id=" (:parentId rightClickedDropzoneElement))]))
                              (setattrib (.querySelector (.getElement insertDividerDialog) "#getDivider")  "value" "")))) 


(goog.events.listen insertDividerDialog goog.ui.Dialog.EventType.AFTER_SHOW
                    (fn [e] 
                      (.addEventListener (.querySelector (.getElement insertDividerDialog) "#getDivider") "focus" add-underline)
                      (.addEventListener (.querySelector (.getElement insertDividerDialog) "#getDivider") "blur" remove-underline)
                      (.focus (.querySelector (.getElement insertDividerDialog) "input.inputDialog"))))

(goog.events.listen insertDividerDialog goog.ui.Dialog.EventType.HIDE
                    (fn [e]
                      (.removeEventListener (.querySelector (.getElement insertDividerDialog) "#getDivider") "focus" add-underline)
                      (.removeEventListener (.querySelector (.getElement insertDividerDialog) "#getDivider") "blur" remove-underline)))

;; << rename page dialog >>
;; renamePageDialog -----------------------------------------------------------------------------------------------------------

;;goog.ui.Dialog constructor: arg1: class name prefix defaults to modal-dialog, arg2: use iframe instead of z-index true or false 
(def renamePageDialog (new goog.ui.Dialog nil true)) 


;; the reason why resetRenamePageDialog and resetRenamePageDialogAFTERSHOW have an argument "mouseoverElement"
;; is because dispatch-sync is not allowed inside another event, as when dispatching the f2 keypress event.
;; Therefore an argument "mouseoverElement" is necesary instead of simply setting the :rightClicked key in the
;; clipboard appdb map.
;; (setstyle (.getContentElement renamePageDialog) "padding-bottom" "0px") ;;tested works!
(declare resetRenamePageDialogAFTERSHOW)
(declare resetRenamePageDialogSELECT)
(defn resetRenamePageDialog [mouseoverElement]
  (let [rightClickedOrMouseoverElement (if mouseoverElement mouseoverElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard])))]
    (resetRenamePageDialogAFTERSHOW rightClickedOrMouseoverElement)
    (resetRenamePageDialogSELECT rightClickedOrMouseoverElement)
    (if (= (:type rightClickedOrMouseoverElement) :link)
      (do (.setTitle renamePageDialog "Rename Bookmark")
          (.setSafeHtmlContent
           renamePageDialog
           (let [thirdOfScreen (int (* (.-innerWidth js/window) 0.3))]
             (goog.html.SafeHtml.concat
              (goog.html.SafeHtml.create "label" #js {"for" "getName" "style" #js {"font-size" ".625rem" "font-weight" "500"}} "Name")
              (goog.html.SafeHtml.create "div" #js {"class" "flexbox" "style" #js {"margin-bottom" "10px"}}
                                         (goog.html.SafeHtml.create "div" #js {"class" "underline"}
                                                                    (goog.html.SafeHtml.create
                                                                     "input" #js {"class" "inputDialog" "id" "getName" "type" "text"
                                                                                  "style" #js {"width" (str thirdOfScreen "px")}} nil)))
              (goog.html.SafeHtml.create "label" #js {"for" "getURL" "style" #js {"font-size" ".625rem" "font-weight" "500"}} "URL")
              (goog.html.SafeHtml.create "div" #js {"class" "flexbox"}
                                         (goog.html.SafeHtml.create "div" #js {"class" "underline"}
                                                                    (goog.html.SafeHtml.create
                                                                     "input" #js {"class" "inputDialog" "id" "getURL" "type" "url"
                                                                                  "style" #js {"width" (str thirdOfScreen "px")}} nil)))))))
      (do (.setTitle renamePageDialog "Rename Folder")
          (.setSafeHtmlContent
           renamePageDialog
           (let [thirdOfScreen (int (* (.-innerWidth js/window) 0.3))]
             (goog.html.SafeHtml.concat
              (goog.html.SafeHtml.create "label" #js {"for" "getName" "style" #js {"font-size" ".625rem" "font-weight" "500"}} "Name")
              (goog.html.SafeHtml.create "div" #js {"class" "flexbox" "style" #js {"margin-bottom" "10px"}}
                                         (goog.html.SafeHtml.create "div" #js {"class" "underline"}
                                                                    (goog.html.SafeHtml.create
                                                                     "input" #js {"class" "inputDialog" "id" "getName" "type" "text"
                                                                                  "style" #js {"width" (str thirdOfScreen "px")}} nil))))))))
    (.setVisible renamePageDialog true)))




(.setButtonSet renamePageDialog (doto (new goog.ui.Dialog.ButtonSet)
                         (.addButton #js {"caption" "Cancel" "key" "cancel"} false true)
                         (.addButton #js {"caption" "Save" "key" "save"} true false)))

(.setHasTitleCloseButton renamePageDialog false)


(defn resetRenamePageDialogSELECT [rightClickedOrMouseoverElement]
  (goog.events.listenOnce renamePageDialog goog.ui.Dialog.EventType.SELECT
                          (fn [e] (let [inputNameValue (.-value (.querySelector (.getElement renamePageDialog) "#getName"))
                                        inputURLValue  (when (= (:type rightClickedOrMouseoverElement) :link)
                                                         (.-value (.querySelector (.getElement renamePageDialog) "#getURL")))
                                        clickedButton (.-key e)
                                        ]
                                    (when (= clickedButton "save")
                                      (let [id (:id rightClickedOrMouseoverElement)
                                            title (.-value (.querySelector (.getElement renamePageDialog) "#getName"))
                                            url (when (= (:type rightClickedOrMouseoverElement) :link)
                                                  (.-value (.querySelector (.getElement renamePageDialog) "#getURL")))]
                                        (if (.hasOwnProperty js/chrome "bookmarks")
                                          (chrome-update-bookmark id :title title :url url)
                                          (rf/dispatch [:dnd/update-bookmark id title url]))))
                                    ;; clear fields for next time newPagedialog is invoked
                                    (setattrib (.querySelector (.getElement renamePageDialog) "#getName")  "value" "")
                                    (when (= (:type rightClickedOrMouseoverElement) :link)
                                      (setattrib (.querySelector (.getElement renamePageDialog) "#getURL")  "value" "")))))) 



(defn resetRenamePageDialogAFTERSHOW [rightClickedOrMouseoverElement]
  (goog.events.listenOnce renamePageDialog goog.ui.Dialog.EventType.AFTER_SHOW
                          (fn [e]
                            (.addEventListener (.querySelector (.getElement renamePageDialog) "#getName") "focus" add-underline)
                            (.addEventListener (.querySelector (.getElement renamePageDialog) "#getName") "blur" remove-underline)
                            (when (= (:type rightClickedOrMouseoverElement) :link)
                              (.addEventListener (.querySelector (.getElement renamePageDialog) "#getURL") "focus" add-underline)
                              (.addEventListener (.querySelector (.getElement renamePageDialog) "#getURL") "blur" remove-underline))
                            ;; (defn setattrib [e x y] (gobj/set e x y))
                            (setattrib (.querySelector (.getElement renamePageDialog) "#getName") "value" (:title rightClickedOrMouseoverElement))
                            (when (= (:type rightClickedOrMouseoverElement) :link)
                              (setattrib (.querySelector (.getElement renamePageDialog) "#getURL") "value" (:url rightClickedOrMouseoverElement)))
                            (.focus (.querySelector (.getElement renamePageDialog) "input.inputDialog")))))

(goog.events.listen renamePageDialog goog.ui.Dialog.EventType.HIDE
                    (fn [e]
                      (let [rightClickedDropzoneElement (:rightClicked @(rf/subscribe [:dnd/get-clipboard]))]
                        (.removeEventListener (.querySelector (.getElement renamePageDialog) "#getName") "focus" add-underline)
                        (.removeEventListener (.querySelector (.getElement renamePageDialog) "#getName") "blur" remove-underline)
                        (when (= (:type rightClickedDropzoneElement) :link)
                          (.removeEventListener (.querySelector (.getElement renamePageDialog) "#getURL") "focus" add-underline)
                          (.removeEventListener (.querySelector (.getElement renamePageDialog) "#getURL") "blur" remove-underline)))))


;; << move to new page dialog >>
;; moveToNewPageDialog -----------------------------------------------------------------------------------------------------------

;;goog.ui.Dialog constructor: arg1: class name prefix defaults to modal-dialog, arg2: use iframe instead of z-index true or false 
(def moveToNewPageDialog (new goog.ui.Dialog nil true)) 
(.setTitle moveToNewPageDialog "Move to New Folder")

(.setSafeHtmlContent
 moveToNewPageDialog
 (let [thirdOfScreen (int (* (.-innerWidth js/window) 0.3))]
   (goog.html.SafeHtml.concat
    (goog.html.SafeHtml.create "label" #js {"for" "moveToNewPage" "style" #js {"font-size" ".625rem" "font-weight" "500"}} "New Folder (at the top of this folder)")
    (goog.html.SafeHtml.create "div" #js {"class" "flexbox"}
                               (goog.html.SafeHtml.create "div" #js {"class" "underline"}
                                                          (goog.html.SafeHtml.create
                                                           "input" #js {"class" "inputDialog" "id" "moveToNewPage" "type" "text"
                                                                        "style" #js {"width" (str thirdOfScreen "px")}} nil))))))


(.setButtonSet moveToNewPageDialog (doto (new goog.ui.Dialog.ButtonSet)
                         (.addButton #js {"caption" "Cancel" "key" "cancel"} false true)
                         (.addButton #js {"caption" "Save" "key" "save"} true false)))

(.setHasTitleCloseButton moveToNewPageDialog false)

(goog.events.listen moveToNewPageDialog goog.ui.Dialog.EventType.SELECT
                    (fn [e] (let [inputValue (.-value (.querySelector (.getElement moveToNewPageDialog) "#moveToNewPage"))
                                  clickedButton (.-key e)]
                              (when (= clickedButton "save")
                                (move-to-top-or-bottom :topOrBottom :top :newFolder inputValue))
                              (setattrib (.querySelector (.getElement moveToNewPageDialog) "#moveToNewPage")  "value" ""))))



(goog.events.listen moveToNewPageDialog goog.ui.Dialog.EventType.AFTER_SHOW
                    (fn [e] 
                      (.addEventListener (.querySelector (.getElement moveToNewPageDialog) "#moveToNewPage") "focus" add-underline)
                      (.addEventListener (.querySelector (.getElement moveToNewPageDialog) "#moveToNewPage") "blur" remove-underline)
                      (.focus (.querySelector (.getElement moveToNewPageDialog) "input.inputDialog"))))

(goog.events.listen moveToNewPageDialog goog.ui.Dialog.EventType.HIDE
                    (fn [e]
                      (.removeEventListener (.querySelector (.getElement moveToNewPageDialog) "#moveToNewPage") "focus" add-underline)
                      (.removeEventListener (.querySelector (.getElement moveToNewPageDialog) "#moveToNewPage") "blur" remove-underline)))


;; << help dialog >>
;; Help dialog here
(def helpDialog (new goog.ui.Dialog nil true)) 
(.setTitle helpDialog "Help")
(.setButtonSet helpDialog (.addButton (new goog.ui.Dialog.ButtonSet) #js {"caption" "Ok" "key" "ok"} true false))

(.setSafeHtmlContent
 helpDialog
 (goog.html.SafeHtml.create
  "div" #js { "style" #js {"overflow-y" "auto" "max-height" "50vh"}}
  (goog.html.SafeHtml.create "div" #js {"class" "helpDialogGrid"}
                             (goog.html.SafeHtml.concat
                              (goog.html.SafeHtml.create "div" #js { "style" #js  {"text-shadow" "2px 2px 5px black"}} "Popup Menu and Views")
                              (goog.html.SafeHtml.create "div" #js {} "Click 'Bookmark Manager' to open the bookmark manager. Any menu item under 'Recently Modified' will run the bookmark manager, with that folder already open. A 'View' is a layout of folder windows which you can save and restore. To save a view; click the left hand side of a view button. To restore a view; click the right hand side of a view button. Any item under 'Views' of the popup menu will run the bookmark manager and restore that view of folders.")

                              (goog.html.SafeHtml.create "div" #js {} )
                              (goog.html.SafeHtml.create "div" #js {} (goog.html.SafeHtml.create "img" #js {"src" "images/help/popup.png"} ))

                              (goog.html.SafeHtml.create "div" #js {}
                                                         (goog.html.SafeHtml.concat
                                                          (goog.html.SafeHtml.create "p" #js { "style" #js  {"margin-top" "0"
                                                                                                             "margin-bottom" "1vh"
                                                                                                             "text-shadow" "2px 2px 5px black"}}
                                                                                     "Select, Copy, Paste and Delete")

                                                          (goog.html.SafeHtml.create "p" #js { "style" #js  {"margin-top" "0"
                                                                                                             "margin-bottom" "0"}}
                                                                                     (goog.html.SafeHtml.concat

                                                                                      (goog.html.SafeHtml.create "span"
                                                                                                                 #js {"style" #js
                                                                                                                      {"font-weight" "bolder"}}
                                                                                                                 "Ctrl|Shift: ")

                                                                                      (goog.html.SafeHtml.create "span" #js {}
                                                                                                                 "Select one/many")))

                                                          (goog.html.SafeHtml.create "p" #js { "style" #js  {"margin-top" "0"
                                                                                                             "margin-bottom" "0"}}
                                                                                     (goog.html.SafeHtml.concat

                                                                                      (goog.html.SafeHtml.create "span"
                                                                                                                 #js {"style" #js
                                                                                                                      {"font-weight" "bolder"}}
                                                                                                                 "Ctrl+A: ")

                                                                                      (goog.html.SafeHtml.create "span" #js {}
                                                                                                                 "Select All")))

                                                          (goog.html.SafeHtml.create "p" #js { "style" #js  {"margin-top" "0"
                                                                                                             "margin-bottom" "0"}}
                                                                                     (goog.html.SafeHtml.concat

                                                                                      (goog.html.SafeHtml.create "span"
                                                                                                                 #js {"style" #js
                                                                                                                      {"font-weight" "bolder"}}
                                                                                                                 "Ctrl+X|C|V: ")

                                                                                      (goog.html.SafeHtml.create "span" #js {}
                                                                                                                 "Cut/Copy/Paste")))

                                                          (goog.html.SafeHtml.create "p" #js { "style" #js  {"margin-top" "0"
                                                                                                             "margin-bottom" "0"}}
                                                                                     (goog.html.SafeHtml.concat

                                                                                      (goog.html.SafeHtml.create "span"
                                                                                                                 #js {"style" #js
                                                                                                                      {"font-weight" "bolder"}}
                                                                                                                 "DEL: ")

                                                                                      (goog.html.SafeHtml.create "span" #js {}
                                                                                                                 "Delete Selected")))

                                                          (goog.html.SafeHtml.create "p" #js { "style" #js  {"margin-top" "0"
                                                                                                             "margin-bottom" "0"}}
                                                                                     (goog.html.SafeHtml.concat

                                                                                      (goog.html.SafeHtml.create "span"
                                                                                                                 #js {"style" #js
                                                                                                                      {"font-weight" "bolder"}}
                                                                                                                 "ESC: ")

                                                                                      (goog.html.SafeHtml.create "span" #js {}
                                                                                                                 "Clear Search Box")))))
                              
                              (goog.html.SafeHtml.create "div" #js {} "Use CTRL or SHIFT click to select tabs, history, bookmarks or folders. Use Ctrl-A to select all, Ctrl-C to copy, Ctrl-X to cut, and Ctrl-V to paste above the link currently highlighted by the mouse, or into the folder currently highlighted by the mouse. To paste above a folder, instead of within it, right click the folder and select 'Paste (above)' from the popup context menu. Press DEL to delete selected folders or links. Deleting greater then 10 items requires confirmation. There is no undo -- undo has not been implemented yet.")
                              ;; [:div {:style {:display "flex"}}
                              ;;  [:div.EmbeddedMenuButton {:style {:justify-self "end" :display "flex" :align-items "center"
                              ;;                                    :justify-content "center"
                              ;;                                    :padding-right "10px" :padding-left "10px"}
                              ;;                            :on-click #(reset! changeColumns :decrease)}
                              ;;   [:img {:src "images/left-chevron.png" :style {:vertical-align "middle"} }]]
                              
                              ;;  [:div.EmbeddedMenuButton {:style {:justify-self "end" :display "flex" :align-items "center"
                              ;;   :justify-content "center"
                              ;;                                    :padding-right "10px" :padding-left "10px"}
                              ;;                            :on-click #(reset! changeColumns :increase)}
                              ;;   [:img {:src "images/right-chevron.png"  }]]
                              ;;  [recently-modified-dropdown]]
                              
                              (goog.html.SafeHtml.create "div" #js {} )
                              (goog.html.SafeHtml.create "div" #js {}
                                                         (goog.html.SafeHtml.create "img" #js {"src" "images/help/select-copy-paste.png"} ))


                              (goog.html.SafeHtml.create "div" #js { "style" #js  {"text-shadow" "2px 2px 5px black"}} "Pin Child Folder")
                              (goog.html.SafeHtml.create "div" #js {} "Left clicking the close button of a folder will only close that folder. Right clicking the close button of a parent folder to close all of it's children. Click the pin icon in the top right corner of a child folder, to prevent a child folder from being closed by it's parent.")

                              (goog.html.SafeHtml.create "div" #js {} )
                              (goog.html.SafeHtml.create "div" #js {}
                                                         (goog.html.SafeHtml.create "img" #js {"src" "images/help/lock-child.png"} ))

                              (goog.html.SafeHtml.create "div"  #js { "style" #js  {"text-shadow" "2px 2px 5px black"}} "Rollup TitleBar")
                              (goog.html.SafeHtml.create "div" #js {} "If a floating window gets in the way, double click it's title bar to toggle hiding it's contents.")

                              (goog.html.SafeHtml.create "div" #js {} )
                              (goog.html.SafeHtml.create "div" #js {}
                                                         (goog.html.SafeHtml.create "img" #js {"src" "images/help/rollup.png"} ))


                              (goog.html.SafeHtml.create "div" #js { "style" #js  {"text-shadow" "2px 2px 5px black"}} "Bookmark Manager Override")
                              (goog.html.SafeHtml.create "div" #js {} "Because this extension overrides the chrome bookmark manager, when you right click any bookmark or folder in chrome's native 'Bookmarks Bar' or 'Other Bookmarks' dropdown menu, and select 'Bookmark manager', this extension will open with that item's containing folder already shown. Similarly left clicking any divider which you have inserted via the popup context menu 'Insert divider...' option will do the same thing. Use the context menu 'Show parent folder' option to then show it's parent folder if necessary.")

                              (goog.html.SafeHtml.create "div" #js {} )
                              (goog.html.SafeHtml.create "div" #js {}
                                                         (goog.html.SafeHtml.create "img" #js {"src" "images/help/bookmark-manager-override.png"} ))
                              
                              (goog.html.SafeHtml.create "div" #js { "style" #js  {"text-shadow" "2px 2px 5px black"}} "Show Parent Folder")
                              (goog.html.SafeHtml.create "div" #js {} "To find the parent folder of any search result, link, or folder, click the up arrow button in the top left corner of the menu, or right click and select 'Show Parent Folder' from the popup context menu. Repeat to find all parent folders.")

                              (goog.html.SafeHtml.create "div" #js {} )
                              (goog.html.SafeHtml.create "div" #js {}
                                                         (goog.html.SafeHtml.create "img" #js {"src" "images/help/show-parent-folder.png"} ))


                              

                              (goog.html.SafeHtml.create "div" #js { "style" #js  {"text-shadow" "2px 2px 5px black"}} "History Search")
                              (goog.html.SafeHtml.create "div" #js {} "Click the 'History: n days' text written on the history tab of the tab/history frame, to reset the number of days to search or display back to 1 day. By default, history is searched 1 day in the past -- use the (+) or (-) buttons to update the search results to look more days in the past.")

                              (goog.html.SafeHtml.create "div" #js {} )
                              (goog.html.SafeHtml.create "div" #js {}
                                                         (goog.html.SafeHtml.create "img" #js {"src" "images/help/history.png"} ))

                              
                              (goog.html.SafeHtml.create "div" #js { "style" #js  {"text-shadow" "2px 2px 5px black"}} "Search")
                              (goog.html.SafeHtml.create "div" #js {} " To find the parent folder of a search result right click the item and select 'Show Parent Folder' from the popup context menu. Redo this for items in the parent folder to find further parent folders. Any search box can be cleared by pressing ESC or clicking the highlighted portion surrounding the search box. Search terms are combined with boolean 'AND'. All substrings of titles and urls are searched. Tabs, history or any folder can be searched by typing in the top right search box or clicking the magnifying glass icon in a floating window to reveal the search box in the titlebar.")

                              (goog.html.SafeHtml.create "div" #js {} )
                              (goog.html.SafeHtml.create "div" #js {}
                                                         (goog.html.SafeHtml.create "img" #js {"src" "images/help/searchbox.png"} ))

                              (goog.html.SafeHtml.create "div" #js { "style" #js  {"text-shadow" "2px 2px 5px black"}} "Columns, Rows and Snapping")
                              (goog.html.SafeHtml.create "div" #js {} "Clicking the [<] [>] buttons attempts to increase the number of columns by decreasing the number of rows, up to a limit of at most 8 columns. The minimum and maximum number of rows and default columns can be set in the extension's options. Double click the border of a floating window to snap resize the window and show all of it's contents along that axis.")

                              (goog.html.SafeHtml.create "div" #js {} )
                              (goog.html.SafeHtml.create "div" #js {}
                                                         (goog.html.SafeHtml.create "img" #js {"src" "images/help/snap-window.png"} ))


                              (goog.html.SafeHtml.create "div" #js { "style" #js  {"text-shadow" "2px 2px 5px black"}} "Recently Modified")
                              (goog.html.SafeHtml.create "div" #js {} "The 'Recently Modified' drop-down list shows a list of most recently modified folders. Deleting, renaming any element or folder, or moving any element out of a folder will not show up in 'Recently Modified'. Only creating a new element or folder, moving an element into a folder, or moving an element within a folder count as modifying the folder. This limitation relies upon the dateGroupModified property of BookmarkTreeNode in the chrome bookmark api. ")

                              (goog.html.SafeHtml.create "div" #js {} )
                              (goog.html.SafeHtml.create "div" #js {}
                                                         (goog.html.SafeHtml.create "img" #js {"src" "images/help/recently-modified.png"} ))))))







;; << dispatch folder menu events >>
;; Dispatch folder-context-menu events -----------------------------------------------------------------------------------------------------------


(defn folderDispatchMenuItemfn [e]
  ;; (println ["dispatchMenuItemfn: (.toString (.-type e)): " (.toString (.-type e)) "(.getId e): " (.getId e.target)])
  (cond (and (= (.toString (.-type e)) "action") (= (.getId e.target) "folder-newlink-menuitem"))
        (do (hide-menu folder-context-menu folder-move-submenu-container) (.setVisible newPageDialog true))
        
        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "folder-newfolder-menuitem"))
        (do (hide-menu folder-context-menu folder-move-submenu-container) (.setVisible newFolderDialog true))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "folder-insertdivider-menuitem"))
        (do (hide-menu folder-context-menu folder-move-submenu-container) (.setVisible insertDividerDialog true))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "folder-rename-menuitem"))
        (do (hide-menu folder-context-menu folder-move-submenu-container) (resetRenamePageDialog nil))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "folder-open-menuitem"))
        (do (hide-menu folder-context-menu folder-move-submenu-container) (open-in-tabs))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "folder-show-menuitem"))
        (do (hide-menu folder-context-menu folder-move-submenu-container) (show-containing-folder))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "folder-move-submenu-top"))
        (do (hide-menu folder-context-menu folder-move-submenu-container) (move-to-top-or-bottom :topOrBottom :top))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "folder-move-submenu-bottom"))
        (do (hide-menu folder-context-menu folder-move-submenu-container) (move-to-top-or-bottom :topOrBottom :bottom))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "folder-move-submenu-new"))
        (do (hide-menu folder-context-menu folder-move-submenu-container) (.setVisible moveToNewPageDialog true))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "folder-selectall-menuitem"))
        (do (hide-menu folder-context-menu folder-move-submenu-container) (selectall-rightclicked-dropzone))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "folder-cut-menuitem"))
        (do (hide-menu folder-context-menu folder-move-submenu-container) (rf/dispatch [:dnd/cutToClipboard]))
        
        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "folder-copy-menuitem"))
        (do (hide-menu folder-context-menu folder-move-submenu-container) (rf/dispatch [:dnd/copyToClipboard]))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "folder-paste-above-menuitem"))
        (do (hide-menu folder-context-menu folder-move-submenu-container) (rf/dispatch [:dnd/pasteFromClipboard :above]))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "folder-paste-in-menuitem"))
        (do (hide-menu folder-context-menu folder-move-submenu-container) (rf/dispatch [:dnd/pasteFromClipboard :in]))
        
        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "folder-delete-menuitem"))
        (do (hide-menu folder-context-menu folder-move-submenu-container) (rf/dispatch [:dnd/confirmDelete]))

        ))



(def FOLDEREVENTS (goog.object.getValues goog.ui.Component.EventType))
;; #js [beforeshow show hide disable enable highlight unhighlight activate deactivate select unselect check uncheck focus
;;      blur open close enter leave action change]

(goog.events.listen folder-context-menu FOLDEREVENTS folderDispatchMenuItemfn) 





;; << dispatch link menu events >>
;; Dispatch link-context-menu events -----------------------------------------------------------------------------------------------------------

(defn linkDispatchMenuItemfn [e]
  ;;(println ["dispatchMenuItemfn: (.toString (.-type e)): " (.toString (.-type e)) "(.getId e): " (.getId e.target)])
  (cond (and (= (.toString (.-type e)) "action") (= (.getId e.target) "link-newlink-menuitem"))
        (do (hide-menu link-context-menu link-move-submenu-container) (.setVisible newPageDialog true))
        
        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "link-newfolder-menuitem"))
        (do (hide-menu link-context-menu link-move-submenu-container) (.setVisible newFolderDialog true))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "link-insertdivider-menuitem"))
        (do (hide-menu link-context-menu link-move-submenu-container) (.setVisible insertDividerDialog true))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "link-rename-menuitem"))
        (do (hide-menu link-context-menu link-move-submenu-container) (resetRenamePageDialog nil))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "link-open-menuitem"))
        (do (hide-menu link-context-menu link-move-submenu-container) (open-in-tabs))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "link-show-menuitem"))
        (do (hide-menu link-context-menu link-move-submenu-container) (show-containing-folder))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "link-move-submenu-top"))
        (do (hide-menu link-context-menu link-move-submenu-container) (move-to-top-or-bottom :topOrBottom :top))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "link-move-submenu-bottom"))
        (do (hide-menu link-context-menu link-move-submenu-container) (move-to-top-or-bottom :topOrBottom :bottom))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "link-move-submenu-new"))
        (do (hide-menu link-context-menu link-move-submenu-container) (.setVisible moveToNewPageDialog true))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "link-selectall-menuitem"))
        (do (hide-menu link-context-menu link-move-submenu-container) (selectall-rightclicked-dropzone))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "link-cut-menuitem"))
        (do (hide-menu link-context-menu link-move-submenu-container) (rf/dispatch [:dnd/cutToClipboard]))
        
        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "link-copy-menuitem"))
        (do (hide-menu link-context-menu link-move-submenu-container) (rf/dispatch [:dnd/copyToClipboard]))

        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "link-paste-menuitem"))
        (do (hide-menu link-context-menu link-move-submenu-container) (rf/dispatch [:dnd/pasteFromClipboard :above]))
        
        (and (= (.toString (.-type e)) "action") (= (.getId e.target) "link-delete-menuitem"))
        (do (hide-menu link-context-menu link-move-submenu-container) (rf/dispatch [:dnd/confirmDelete]))

        ))



(def LINKEVENTS (goog.object.getValues goog.ui.Component.EventType))
;; #js [beforeshow show hide disable enable highlight unhighlight activate deactivate select unselect check uncheck focus
;;      blur open close enter leave action change]

(goog.events.listen link-context-menu LINKEVENTS linkDispatchMenuItemfn) 


;; << saveViewDialog dialog >>
;; saveViewDialog -----------------------------------------------------------------------------------------------------------
;; -*** restore-view: localstorage json serialization views, view-number integer -> show windows with correct position and sizes

;; folder-position-list:
;; [["clojure", 14 [100 100] ["500px" "400px"]] ["entAUD", 32 [100 200] ["600px" "500px"]] ["music" 42  [100 300] ["700px" "600px"]]]
;; open-folder-dimensions:
;; ({:folderId "99990032", :top "117px", :left "974px", :width "460px", :height "297px"}
;;  {:folderId "14", :top "119px", :left "5px", :width "460px", :height "115px"}
;;  {:folderId "32", :top "117px", :left "974px", :width "460px", :height "297px"}
;;  {:folderId "42", :top "28px", :left "493px", :width "460px", :height "572.797px"})

(defn remove-invalid-folders [open-folder-dimensions]
  (let [db (rf/subscribe [:dnd/db])
        bookmark-atom (get-in @db [:dnd/state :bookmark-atom])]
    (filter #(find-id-no-throw (map-vec-zipper bookmark-atom) (:folderId %)) open-folder-dimensions)))

(defn remove-open-folders [open-folder-dimensions]
  (let [current-dropzone-options @(rf/subscribe [:dnd/dropzone-options])
        open-menu-dropzones (keys (into {} (filter #(= (:menuOpen (second %)) true) current-dropzone-options)))
        open-menu-ids  (set (map dkey->fid open-menu-dropzones))]
    (remove (comp open-menu-ids :folderId) open-folder-dimensions)))

(defn filter-open-or-invalid-folders [open-folder-dimensions]
  (if (.hasOwnProperty js/chrome "bookmarks") (remove-open-folders open-folder-dimensions)
      (remove-open-folders (remove-invalid-folders open-folder-dimensions))))

(defn restore-view [view-number]
  ;; localstorage view-1: {:view-title "VIEW 1", :open-folder-dimensions
  ;; '({:folderId "32", :top "119px", :left "5px", :width "460px", :height "115px"}
  ;;   {:folderId "17", :top "117px", :left "974px", :width "460px", :height "297px"}
  ;;   {:folderId "22", :top "28px", :left "493px", :width "460px", :height "572.797px"})}
  ;; if view-? is nil in localstorage or is a map with no :open-folder-dimensions throw error:
  {:pre [(seq (:open-folder-dimensions (cljs.reader/read-string (js/localStorage.getItem (str "view-" view-number)))))]} 
  ;; for each folder that in folder-posiion-list that exists
  ;; cond folder-exists-and-open? ignore it, cond :else (folder is closed and exists or does not exist) overwrite it:
  ;; initialize the dropzone
  ;; populate the dropzone contents and set menu-xyposition with:
  ;; (rf/dispatch [:chrome-synch-all-dropzones-to-folderId-with-menu menu-xyposition (:id rightClickedElement) parentDropzone])
  ;; or 
  ;; (rf/dispatch [:dnd/synch-all-dropzones-to-folderId-with-menu (find-title-from-id (dkey->fid parentDropzone)) menu-xyposition (:id rightClickedElement) parentDropzone])

  (let [view (cljs.reader/read-string (js/localStorage.getItem (str "view-" view-number)))
        open-folder-dimensions (:open-folder-dimensions view)]
    (doseq [{folderId :folderId top :top left :left width :width height :height}
            (filter-open-or-invalid-folders open-folder-dimensions)]
     (let [folder-title (str folderId)
           folder-dropzone-key (fid->dkey folderId)]
       (rf/dispatch [:dnd/initialize-drop-zone
                     folder-dropzone-key ;; this is the dropzone-id
                     ;; options
                     {:folderId folderId
                      :pinned false
                      :menuOpen false
                      :collapsedStartupToggle false
                      :cutoffDropzoneElements defaultCutoffDropzoneElements
                      :selected []
                      :z-index 1}])

       ;; position is: [yaxis xaxis]
       (try (if (.hasOwnProperty js/chrome "bookmarks")
              (rf/dispatch [:chrome-synch-all-dropzones-to-folderId-with-menu [(js/parseInt top) (js/parseInt left)] [width height] false folder-dropzone-key])
              (rf/dispatch [:dnd/synch-all-dropzones-to-folderId-with-menu folder-title [(js/parseInt top) (js/parseInt left)] [width height] false folder-dropzone-key]))
            ;; tested error => Error Occured:  {:type :custom-arg-error, :message find-id: id was not found}
            (catch :default e (println "Error Occured: " e)))))))

;; -*** save-view: {:view-title string, ({:folder id :top px :left px :width px :height px} ...) -> localstorage json serialization views

(defn get-all-open-folders []
  (let [current-dropzone-options @(rf/subscribe [:dnd/dropzone-options])
        open-menu-dropzones (keys (into {} (filter #(= (:menuOpen (second %)) true) current-dropzone-options)))]
    open-menu-dropzones))

(defn dropzone->folder-data [dropzone-id]
  (let [dropzoneElement (.querySelector js/document (str "#drop-zone-" (name dropzone-id)))
        menuElement (.-parentNode dropzoneElement)
        top (get-computed-style menuElement "top") 
        left (get-computed-style menuElement "left")

        width (get-computed-style dropzoneElement "width" )
        height (get-computed-style dropzoneElement "height" )

        
        ;;getstyle (fn [e x] (gobj/get (gobj/get e "style") x)) 
        minWidth (get-computed-style dropzoneElement "min-width")
        minHeight (get-computed-style dropzoneElement "min-height")]

    ;;[top left width height minWidth minHeight]
    ;; ({:folder id :top px :left px :width px :height px} ...)
    {:folderId (dkey->fid dropzone-id) :top top :left left
     :width (if (> (px-to-int minWidth) (px-to-int width)) minWidth width)
     :height (if (> (px-to-int minHeight) (px-to-int height)) minHeight height)}))

;; save-view
;; ps: retrieve view-number clicked, all currently open folders:  id, size, positions, dimensions and save them to localstorage
;; view-number, integer
;; all-open-folders: (:dropzone-1 :dropzone-2 :dropzone-17)
;; open-folder-dimensions: all-open-folders -> ({:folder id :top px :left px :width px :height px} ...)
;;(save-view 1) ;;=> ;;{:view-title "VIEW 1", :open-folder-dimensions ({:folderId "14", :top "221px", :left "252px", :width "460px", :height "233px"})}
(defn save-view [view-number view-title]
  (let [all-open-folders (get-all-open-folders) ;; (see restore-view)
        ;; open-folder-dimensions: ({:folderId "14", :top "221px", :left "252px", :width "460px", :height "233px"} ...)
        open-folder-dimensions (map dropzone->folder-data (remove #{:dropzone-1 :dropzone-2} all-open-folders))]
    (.setItem js/localStorage (str "view-" view-number) {:view-title view-title :open-folder-dimensions open-folder-dimensions})))

;; saveViewDialog ---------------------------------------------

(def saveViewDialog (new goog.ui.Dialog nil true))
(def selectedViewNumber (r/atom 0))

(.setTitle saveViewDialog "Save View")


;; fetch localstorage view-(view-number) and extract view-title or default set it to "VIEW (view-number)"
;; set default input value to this view-title
;; when save button is clicked call resetSaveViewDialog, and (.setVisible saveViewDialog true)
(defn resetSaveViewDialog [view-number]
  (reset! selectedViewNumber view-number)
  (.setSafeHtmlContent
   saveViewDialog
   (let [thirdOfScreen (int (* (.-innerWidth js/window) 0.3))
         view (cljs.reader/read-string (js/localStorage.getItem (str "view-" view-number)))
         view-title (if (nil? (:view-title view)) (str "VIEW-" view-number) (:view-title view))]
     (goog.html.SafeHtml.concat
      (goog.html.SafeHtml.create "label" #js {"for" "newfolder" "style" #js {"font-size" ".625rem" "font-weight" "500"}} "Name")
      (goog.html.SafeHtml.create "div" #js {"class" "flexbox"}
                                 (goog.html.SafeHtml.create "div" #js {"class" "underline"}
                                                            (goog.html.SafeHtml.create
                                                             "input" #js {"id" "newfolder" "class" "inputDialog" "type" "text" "value" view-title
                                                                          "style" #js {"width" (str thirdOfScreen "px")}} nil))))))
  (.setVisible saveViewDialog true))

(.setButtonSet saveViewDialog (doto (new goog.ui.Dialog.ButtonSet)
                         (.addButton #js {"caption" "Cancel" "key" "cancel"} false true)
                         (.addButton #js {"caption" "Save" "key" "save"} true false)))

(.setHasTitleCloseButton saveViewDialog false)


(goog.events.listen saveViewDialog goog.ui.Dialog.EventType.SELECT
                    (fn [e] (let [inputValue (.-value (.querySelector (.getElement saveViewDialog) "input.inputDialog"))
                                  clickedButton (.-key e)]
                              (when (= clickedButton "save")
                                (save-view @selectedViewNumber inputValue)
                                (rf/dispatch [:dnd/update-view-title @selectedViewNumber inputValue])
                                (run-fade-alert "view saved")
                                (println ["inputValue: " inputValue "@selectedViewNumber: " @selectedViewNumber]))
                              (setattrib (.querySelector (.getElement saveViewDialog) "input.inputDialog")  "value" ""))))


(goog.events.listen saveViewDialog goog.ui.Dialog.EventType.AFTER_SHOW
                    (fn [e] 
                      (.addEventListener (.querySelector (.getElement saveViewDialog) "input.inputDialog") "focus" add-underline)
                      (.addEventListener (.querySelector (.getElement saveViewDialog) "input.inputDialog") "blur" remove-underline)
                      (.focus (.querySelector (.getElement saveViewDialog) "input.inputDialog"))))

(goog.events.listen saveViewDialog goog.ui.Dialog.EventType.HIDE
                    (fn [e]
                      (.removeEventListener (.querySelector (.getElement saveViewDialog) "input.inputDialog") "focus" add-underline)
                      (.removeEventListener (.querySelector (.getElement saveViewDialog) "input.inputDialog") "blur" remove-underline)))




(ns dragon-bookmark-manager.views
  (:require 
   [re-frame.core :as rf]
   [reagent.core :as r]
   [clojure.string]
   [clojure.pprint]
   [dragon-bookmark-manager.utilities :refer [fid->dkey dkey->fid getstyle subfolder? zip-walk-closure get-all-subfolders  
                                              chrome-getSubTree-bookmark recursive-drop-dispatch-multiple
                                              get-dz-element zip-walk map-vec-zipper px-to-int setstyle setattrib get-property
                                              get-computed-style edge-overlay-ondrop center-overlay-ondrop link-overlay 
                                              delta-out delta-out2 lastZIndex gen-next-zindex clear-all-selections-except
                                              fetch-all-selected embeddedMenuConfiguration themeColor]]
   [dragon-bookmark-manager.events :as dnd]
   [dragon-bookmark-manager.contextmenu :as dndc]))

;;this defines the dispatching function on the arguments for dropped-widget
;;{:keys [type id]} binds variables type and id to values of {:type typevalue :id idvalue}
;; so type = typevalue and id = idvalue but only typevalue is returned by fn, and therefore dispatched on.
;; NOTE: ((fn [a b _ _] [a b]) 1 2 3 4 5 6) ;; => [1 2] anonymous functions drop args with no error
;; you can call dropped widget with any number of arguments and it still dispatch on :type
(defmulti dropped-widget
    (fn [{:keys [type id]} _ _] type))
  
 

(defmethod dropped-widget
  :search-not-found
  [de _ _ _ _]
  (fn [de]
    (println ":search-not-found is running!")
    [:div.search-not-found
     {;;:on-click #(.click (:searchBoxRef de))
      :on-click #(.dispatchEvent (:searchBoxRef de) (js/Event. "mouseup" #js {"bubbles" true}))
      ;; #(do ;; recursive-dropzone-search must be directly called by bubbling this click event because unlike tabs/history
      ;;      ;; the dropzone is updated directly by an event because it has to be wrapped in a chrome getSubtree asynch callback
      ;;              (.click (:searchBoxRef de))
      ;;              ;; originally meant to clear tabs/history without click event, works because the elements are subscribed to a filter subscription
      ;;              (reset! (:searchText de) "")
      ;;              (setattrib (:searchBoxRef de) "value" ""))
      :style {:display "flex" :justify-content "center" :align-items "center" :word-break "break-all"
              :font-size "2vw" :padding-top "1vw" :padding-bottom "1vw"} 

      :id (str "search-not-found-" (name (:id de)))}
     [:div (str "Search: " (:searchText de) " not found" )]]))

(defmethod dropped-widget
  :blank
  [de _ _ _ _]
  (let [thirdOfScreen (int (* (.-innerWidth js/window) 0.3)) ;; for 1440px monitor about 432px
        bgcolor (r/atom "transparent")
        s (r/atom {})
        clipboard (rf/subscribe [:dnd/get-clipboard])
        contextmenuVisible? (rf/subscribe [:dnd/get-contextmenu-visible])]
    (fn [de] ;; de {:type :blank :id "blank-element" :parentId "1234"}      
      [:div.blank-element
       { ;; DANGER WITHOUT "when" to guard: :ref #(swap! s assoc :componentRef %) ;; will cause infinite updates!
        ;; :ref (fn [el] (when el (swap! s assoc :componentRef el)))
        ;; :ref #(swap! s assoc :componentRef %)
        :ref (fn [el] (when el (swap! s assoc :componentRef el)))
        
        :style {:position "absolute" :top "-4px" :left "0px" :width "100%" :height "100%" :cursor "pointer"  } ;; take up the entire dropzone
        :id (str "blank-element-" (name (:id de)))
        
        :on-drag-end #(do (reset! bgcolor "transparent"))
        
        :on-drop (fn [event]
                   (do (.preventDefault event) ;; drag and drop works without this
                       (reset! bgcolor "transparent")
                       ;;(reset! linkElementClass "link-element")
                       (center-overlay-ondrop s event (:parentId de) 0 false)))

        :on-mouse-over #(reset! bgcolor "linear-gradient(to bottom, white 2px, rgba(0,0,0,0) 4px, rgba(0,0,0,0) 100%)")
        :on-mouse-out #(reset! bgcolor "transparent")

        
        :on-drag-over #(do (.preventDefault %) ;; it works without this
                           (reset! bgcolor "orange")) ;; display the dropmarker
        
        :on-drag-leave #(do (.preventDefault %)
                            (reset! bgcolor "transparent"))

        :on-context-menu (fn [e]
                           (.preventDefault e) ;; disables default right click menu
                           (reset! bgcolor "linear-gradient(to bottom, white 2px, rgba(0,0,0,0) 4px, rgba(0,0,0,0) 100%)")
                           ;; close folder menu, there is no need to hide the link menu because it will placed below
                           (dndc/hide-menu dndc/folder-context-menu dndc/folder-move-submenu-container) 
                           ;; folderbutton approach:
                           ;; set the new right clicked element and maintain the selected clipboard contents until pasted or new copy or cut
                           ;; because the new right clicked element will affect the count of dndc/fetch-links-of-all-selected
                           ;;                                                            rightClicked     selected 
                           ;; (rf/dispatch-sync [:dnd/initialize-or-update-clipboard nil dropzoneElement  (:selected @(rf/subscribe [:dnd/get-clipboard]))])
                           ;; link approach
                           ;; set the new right clicked element and maintain the selected clipboard contents until pasted or new copy or cut
                           ;;                                                            rightClicked          selected 
                           ;; (rf/dispatch-sync [:dnd/initialize-or-update-clipboard nil dropzoneElement      (:selected @(rf/subscribe [:dnd/get-clipboard]))])
                           ;; newPageDialog, newFolderDialog, insertDividerDialog, and pasteFromClipboard all only use :index and :parentId from rightclicked clipboard element
                           ;; ["from :blank widget: de:" {:type :blank, :id "blank-element1", :parentId "4418"}] ;; missing :index 
                           ;; set the blank element as the rightClicked element for use in dialogs, but set it's index to 0:
                           (rf/dispatch-sync [:dnd/initialize-or-update-clipboard    nil (assoc de :index 0)  (:selected @(rf/subscribe [:dnd/get-clipboard]))])
                           (.setEnabled dndc/link-newlink-menuitem true)
                           (.setEnabled dndc/link-newfolder-menuitem true)
                           (.setEnabled dndc/link-insertdivider-menuitem true)
                           (.setEnabled dndc/link-rename-menuitem false)
                           (.setCaption dndc/link-open-menuitem "Open in new tab")
                           (.setEnabled dndc/link-open-menuitem false)
                           (.setEnabled dndc/link-show-menuitem false)
                           (.setCaption dndc/link-move-submenu "Move to ...")
                           (.setEnabled dndc/link-move-submenu false)
                           (.setEnabled dndc/link-selectall-menuitem false)
                           (.setEnabled dndc/link-cut-menuitem false)
                           (.setEnabled dndc/link-copy-menuitem false)
                           (if (:selected @(rf/subscribe [:dnd/get-clipboard]))
                             (.setEnabled dndc/link-paste-menuitem true)
                             (.setEnabled dndc/link-paste-menuitem false))
                           (dndc/setCaptionAndAccelerator dndc/link-delete-menuitem "Delete" "Del")
                           (.setEnabled dndc/link-delete-menuitem false)
                           (dndc/place-context-menu dndc/link-context-menu))}

       ;; Drop marker class "blank-drop-marker" and "blank-element" class is used by ctrl-v paste :dnd/mouseover-pasteFromClipboard
       [:div.blank-drop-marker {:style {:position "absolute"
                                        :background (if (and @contextmenuVisible? (= de (dissoc (:rightClicked @clipboard) :index)))
                                                      "linear-gradient(to bottom, white 2px, rgba(0,0,0,0) 4px, rgba(0,0,0,0) 100%)"                                                                           
                                                      @bgcolor) :top "44%" :width "100%" :height "4px"}}]])))



(defmethod dropped-widget
  :tablink
  [dropzoneElement embedded? embeddedWidgetWidth dropzoneRef lastInCol textColorForWindowId]
  (let [s (r/atom {})]
    (fn [dropzoneElement embedded? embeddedWidgetWidth dropzoneRef lastInCol]
      (let [thirdOfScreen (int (* (.-innerWidth js/window) 0.3))
            {type :type title :title url :url id :id windowId :windowId parentId :parentId dateAdded :dateAdded index :index} dropzoneElement
            currentDragState (rf/subscribe [:dnd/get-drag-state]) ;; for 1440px monitor about 432px
            elementHeight (when  (:componentRef @s) (px-to-int (get-computed-style (:componentRef @s) "height"))) ;; integer
            elementOffsetTop (when  (:componentRef @s) (get-property (:componentRef @s) "offsetTop")) ;; integer
            clientHeightDropzoneHeight (when (:componentRef @dropzoneRef) (get-property (:componentRef @dropzoneRef) "clientHeight") ) ;; integer
            extendedBottomOverlayHeight
            (when (and (:componentRef @s) (:componentRef @dropzoneRef))
              (- clientHeightDropzoneHeight elementOffsetTop (js/Math.round (* .75 elementHeight))))
            isCtrlDown @(rf/subscribe [:dnd/get-keystate :ctrlIsDown])
            isShiftDown @(rf/subscribe [:dnd/get-keystate :shiftIsDown])
            contextmenuVisible? @(rf/subscribe [:dnd/get-contextmenu-visible])
            getSelected @(rf/subscribe [:dnd/get-selected :tab-history :tabselected])
            clipboardContents @(rf/subscribe [:dnd/get-clipboard])] 
        [:div.link-element
         {:style {:display "flex" :align-items "stretch" :box-sizing "border-box"
                  :position "relative" :width (if embedded? embeddedWidgetWidth thirdOfScreen)
                  ;; :border (if (some #{(:id dropzoneElement)} @(rf/subscribe [:dnd/get-selected :tab-history :tabselected]))
                  ;;           "2px solid black" "2px solid transparent")
                  :border (let [rightclicked? (= dropzoneElement (:rightClicked clipboardContents))
                                selected? (some #{(:id dropzoneElement)} getSelected)]
                            (cond (and contextmenuVisible? rightclicked?) "2px solid white"
                                  selected? (if themeColor "2px solid #222222" "2px solid black")
                                  :else "2px solid transparent"))
                  :background-image (if (some #{(:id dropzoneElement)} getSelected)
                                      "radial-gradient(circle at center, #AF0404 0%, gold 100%)" "none")} 
          :id (str "dropped-element-" (name (:id dropzoneElement))) 
          :draggable true ;; required for divs because  only images and links are draggable by default
          ;; DANGER WITHOUT when guard: :ref #(swap! s assoc :componentRef %) ;; will cause infinite updates!
          ;;:ref #(swap! s assoc :componentRef %)
          :ref (fn [el] (when el (swap! s assoc :componentRef el))) 
          
          :class (cond @currentDragState "link-element no-hover"
                       (or isCtrlDown isShiftDown) "link-element-modifier-down"
                       :else "link-element")
          :on-context-menu (fn [e]
                             (.preventDefault e) ;; disables default right click menu
                             ;; close folder menu, there is no need to hide the link menu because it will placed below
                             (dndc/hide-menu dndc/folder-context-menu dndc/folder-move-submenu-container)
                             (clear-all-selections-except :tabselected)
                             ;; set the new right clicked element and maintain the selected clipboard contents until pasted or new copy or cut
                             ;;                                                         rightClicked     selected 
                             (rf/dispatch-sync [:dnd/initialize-or-update-clipboard nil dropzoneElement (:selected clipboardContents)])

                             ;; disable all link menu items except for "select all" and "copy"
                             (.setEnabled dndc/link-newlink-menuitem false)
                             (.setEnabled dndc/link-newfolder-menuitem false)
                             (.setEnabled dndc/link-insertdivider-menuitem false)
                             (.setEnabled dndc/link-rename-menuitem false)
                             (.setCaption dndc/link-open-menuitem "Open in new tab")
                             (.setEnabled dndc/link-open-menuitem false)
                             (.setEnabled dndc/link-show-menuitem false)
                             (.setCaption dndc/link-move-submenu "Move to ...")
                             (.setEnabled dndc/link-move-submenu false)
                             (.setEnabled dndc/link-selectall-menuitem true)
                             (.setEnabled dndc/link-cut-menuitem false)
                             (.setEnabled dndc/link-copy-menuitem true)
                             (.setEnabled dndc/link-paste-menuitem false)
                             (dndc/setCaptionAndAccelerator dndc/link-delete-menuitem "Delete" "Del")
                             (.setEnabled dndc/link-delete-menuitem false)
                             (dndc/place-context-menu dndc/link-context-menu))

          :on-click (fn onclick [event]
                      (let [isCtrlDown @(rf/subscribe [:dnd/get-keystate :ctrlIsDown])
                            isShiftDown @(rf/subscribe [:dnd/get-keystate :shiftIsDown])
                            currentSelectedTabElements @(rf/subscribe [:dnd/get-selected :tab-history :tabselected])
                            dropzoneElementId (:id dropzoneElement)]
                        (cond isCtrlDown (do (clear-all-selections-except :tabselected)
                                             (rf/dispatch [:dnd/reset-selected (vec (remove #{:anchor} currentSelectedTabElements))
                                                           :tab-history :tabselected])
                                             (if (some #{dropzoneElementId} currentSelectedTabElements) 
                                               (rf/dispatch [:dnd/remove-selected :tab-history dropzoneElementId :tabselected])
                                               (rf/dispatch [:dnd/append-selected :tab-history dropzoneElementId :tabselected])))
                              isShiftDown (do (clear-all-selections-except :tabselected)
                                              (let [allTabElementIds
                                                    (if (.hasOwnProperty js/chrome "bookmarks")
                                                      (map :id @(rf/subscribe [:dnd/get-tabs]))
                                                      (map :id (for [x (range 10)]
                                                                 {:id (str (+ 9000000000 x)) :windowId (str (+ 900000 x))
                                                                  :title "DuckDuckGo - Privacy, simplified." :url "https://duckduckgo.com/"
                                                                  :type :tablink}))) 
                                                      
                                                    setAnchorTabElements (cond (empty? currentSelectedTabElements) [(:id dropzoneElement) :anchor] 
                                                                               (some #{:anchor} currentSelectedTabElements) currentSelectedTabElements
                                                                               :else (conj  (vec currentSelectedTabElements) :anchor))
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
                                                (rf/dispatch [:dnd/reset-selected newSelectedTabElements :tab-history :tabselected])))
                              
                              :else (clear-all-selections-except))))
          
          
          :on-drag-start #(do (rf/dispatch [:dnd/set-drag-state true]) 
                              #_(println "started dragging") 
                              #_(.setData (.-dataTransfer %) "text/my-custom-data"
                                          (str (name (:type dropzoneElement)) "," (:id dropzoneElement) ","
                                               (str "dropzone-" (:parentId dropzoneElement))))
                              (.setData (.-dataTransfer %) "text/my-custom-data" (pr-str dropzoneElement))
                              ;; this allows link to be dropped on the location bar but ignored by drop handlers which take my-custom-data type
                              ;; note that multiple urls in text/uri-list do not open in tabs, so it's useless? unless maybe dropping onto bookmarks
                              (.setData (.-dataTransfer %) "text/plain" (:url dropzoneElement))
                              (let [selectedIds @(rf/subscribe [:dnd/get-selected :tab-history :tabselected])]
                                ;; if the dragged element is not among the selectedIds, then unselect all immediately
                                ;; ie. (dispatch-sync version of clear-all-selections-except) so that center-overlay-ondrop
                                ;;     will not move the selected elements when :on-drop event occurs, only the unselected element
                                (when (not (nat-int? (.indexOf selectedIds (:id dropzoneElement))))
                                  (clear-all-selections-except))))
          
          :on-drag-end #(rf/dispatch [:dnd/set-drag-state false])}
         ;; link widget : popups must be enabled for open in background to work, or else make the whole div an anchor and set target to _blank 
         ;; but then only single click works.
         ;; debug: (if-let [winId (:windowId dropzoneElement)]  (str title " window: " winId) title)
         [:div {:title (str title "\n" url) 
                :style {:overflow "hidden" :user-select "none" :white-space "nowrap" :text-overflow "ellipsis" :display "flex"
                        :align-items "center" :flex-grow "1" :color textColorForWindowId} 
                :on-double-click #(.open js/window (:url dropzoneElement) "_blank")}
          [:img.icon-link {:draggable false
                           ;; waiting for onload is to hide broken image icons
                           :style {:visibility "hidden"}
                           :on-load #(setstyle (.-target %) "visibility" "visible")
                           :on-error #(setattrib (.-target %) "src" "images/link16.png")
                           :src (cond (#{"chrome://" "chrome-extension://" "file://"} (re-find #"^[a-zA-Z0-9]+://" (:url dropzoneElement)))
                                      "images/link16.png"
                                      (.hasOwnProperty js/chrome "bookmarks")
                                      (str "https://www.google.com/s2/favicons?domain=" (:url dropzoneElement))
                                      :else "images/link16.png")}]
          [:span {:style {:overflow "hidden" :text-overflow "ellipsis"}} (str title ", " id )]]]))))




(defmethod dropped-widget
  :historylink
  [dropzoneElement embedded? embeddedWidgetWidth dropzoneRef lastInCol]
  (let [s (r/atom {})]
    (fn [dropzoneElement embedded? embeddedWidgetWidth dropzoneRef lastInCol]
      (let [thirdOfScreen (int (* (.-innerWidth js/window) 0.3))
            {type :type title :title url :url id :id lastVisitTime :lastVisitTime parentId :parentId dateAdded :dateAdded index :index} dropzoneElement
            currentDragState (rf/subscribe [:dnd/get-drag-state]) ;; for 1440px monitor about 432px
            elementHeight (when  (:componentRef @s) (px-to-int (get-computed-style (:componentRef @s) "height"))) ;; integer
            elementOffsetTop (when  (:componentRef @s) (get-property (:componentRef @s) "offsetTop")) ;; integer
            clientHeightDropzoneHeight (when (:componentRef @dropzoneRef) (get-property (:componentRef @dropzoneRef) "clientHeight") ) ;; integer
            extendedBottomOverlayHeight
            (when (and (:componentRef @s) (:componentRef @dropzoneRef))
              (- clientHeightDropzoneHeight elementOffsetTop (js/Math.round (* .75 elementHeight))))
            isCtrlDown @(rf/subscribe [:dnd/get-keystate :ctrlIsDown])
            isShiftDown @(rf/subscribe [:dnd/get-keystate :shiftIsDown])
            contextmenuVisible? @(rf/subscribe [:dnd/get-contextmenu-visible])
            getSelected @(rf/subscribe [:dnd/get-selected :tab-history :historyselected])
            clipboardContents @(rf/subscribe [:dnd/get-clipboard])]
        [:div.link-element
         {:style {:display "flex" :align-items "stretch" :box-sizing "border-box"
                  :position "relative" :width (if embedded? embeddedWidgetWidth thirdOfScreen)
                  ;; :border (if (some #{(:id dropzoneElement)} @(rf/subscribe [:dnd/get-selected :tab-history :historyselected]))
                  ;;           "2px solid black" "2px solid transparent")
                  :border (let [rightclicked? (= dropzoneElement (:rightClicked clipboardContents))
                                selected? (some #{(:id dropzoneElement)} getSelected)]
                            (cond (and contextmenuVisible? rightclicked?) "2px solid white"
                                  selected? (if themeColor "2px solid #222222" "2px solid black")
                                  :else "2px solid transparent"))
                  :background-image (if (some #{(:id dropzoneElement)} getSelected)
                                      "radial-gradient(circle at center, #AF0404 0%, gold 100%)" "none")}
          :id (str "dropped-element-" (name (:id dropzoneElement))) 
          :draggable true ;; required for divs because  only images and links are draggable by default
          ;; DANGER WITHOUT "when" to guard: :ref #(swap! s assoc :componentRef %) ;; will cause infinite updates!
          ;; :ref (fn [el] (when el (swap! s assoc :componentRef el)))
          ;; :ref #(swap! s assoc :componentRef %)
          :ref (fn [el] (when el (swap! s assoc :componentRef el))) 


          :class (cond @currentDragState "link-element no-hover"
                       (or isCtrlDown isShiftDown) "link-element-modifier-down"
                       :else "link-element")

          :on-context-menu (fn [e]
                             (.preventDefault e) ;; disables default right click menu
                             ;; close folder menu, there is no need to hide the link menu because it will placed below
                             (dndc/hide-menu dndc/folder-context-menu dndc/folder-move-submenu-container)
                             (clear-all-selections-except :historyselected)
                             ;; set the new right clicked element and maintain the selected clipboard contents until pasted or new copy or cut
                             ;;                                                         rightClicked     selected 
                             (rf/dispatch-sync [:dnd/initialize-or-update-clipboard nil dropzoneElement (:selected clipboardContents)])

                             ;; disable all link menu items except for "select all" and "copy"
                             (.setEnabled dndc/link-newlink-menuitem false)
                             (.setEnabled dndc/link-newfolder-menuitem false)
                             (.setEnabled dndc/link-insertdivider-menuitem false)
                             (.setEnabled dndc/link-rename-menuitem false)
                             (.setCaption dndc/link-open-menuitem "Open in new tab")
                             (.setEnabled dndc/link-open-menuitem false)
                             (.setEnabled dndc/link-show-menuitem false)
                             (.setCaption dndc/link-move-submenu "Move to ...")
                             (.setEnabled dndc/link-move-submenu false)
                             (.setEnabled dndc/link-selectall-menuitem true)
                             (.setEnabled dndc/link-cut-menuitem false)
                             (.setEnabled dndc/link-copy-menuitem true)
                             (.setEnabled dndc/link-paste-menuitem false)
                             (dndc/setCaptionAndAccelerator dndc/link-delete-menuitem "Delete" "Del")
                             (.setEnabled dndc/link-delete-menuitem false)
                             (dndc/place-context-menu dndc/link-context-menu))
          
          :on-click (fn onclick [event]
                      (let [isCtrlDown @(rf/subscribe [:dnd/get-keystate :ctrlIsDown])
                            isShiftDown @(rf/subscribe [:dnd/get-keystate :shiftIsDown])
                            currentSelectedTabElements @(rf/subscribe [:dnd/get-selected :tab-history :historyselected])
                            dropzoneElementId (:id dropzoneElement)]
                        (cond isCtrlDown (do (clear-all-selections-except :historyselected)
                                             (rf/dispatch [:dnd/reset-selected (vec (remove #{:anchor} currentSelectedTabElements))
                                                           :tab-history :historyselected])
                                             (if (some #{dropzoneElementId} currentSelectedTabElements)
                                               (rf/dispatch [:dnd/remove-selected :tab-history dropzoneElementId :historyselected])
                                               (rf/dispatch [:dnd/append-selected :tab-history dropzoneElementId :historyselected])))
                              isShiftDown (do (clear-all-selections-except :historyselected)
                                              (let [allTabElementIds
                                                    (if (.hasOwnProperty js/chrome "bookmarks")
                                                      (map :id @(rf/subscribe [:dnd/get-history]))
                                                      (map :id (for [x (range 10)]
                                                                 {:id (str (+ 9000000000 (* 10 x))) :title "Google History"
                                                                  :url "https://google.com/" :type :historylink})))
                                                    
                                                    setAnchorTabElements (cond (empty? currentSelectedTabElements) [(:id dropzoneElement) :anchor]
                                                                               (some #{:anchor} currentSelectedTabElements) currentSelectedTabElements
                                                                               :else (conj  (vec currentSelectedTabElements) :anchor))
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
                                                (rf/dispatch [:dnd/reset-selected newSelectedTabElements :tab-history :historyselected])))
                              
                              :else (clear-all-selections-except))))
          
          :on-drag-start #(do (rf/dispatch [:dnd/set-drag-state true]) 
                              #_(println "started dragging") 
                              #_(.setData (.-dataTransfer %) "text/my-custom-data"
                                        (str (name (:type dropzoneElement)) "," (:id dropzoneElement) ","
                                             (str "dropzone-" (:parentId dropzoneElement))))
                              (.setData (.-dataTransfer %) "text/my-custom-data" (pr-str dropzoneElement))
                              ;; this allows link to be dropped on the location bar but ignored by drop handlers which take my-custom-data type
                              ;; note that multiple urls in text/uri-list do not open in tabs, so it's useless? unless maybe dropping onto bookmarks
                              (.setData (.-dataTransfer %) "text/plain" (:url dropzoneElement))
                              (let [selectedIds @(rf/subscribe [:dnd/get-selected :tab-history :historyselected])]
                                ;; if the dragged element is not among the selectedIds, then unselect all immediately
                                ;; ie. (dispatch-sync version of clear-all-selections-except) so that center-overlay-ondrop
                                ;;     will not move the selected elements when :on-drop event occurs, only the unselected element
                                (when (not (nat-int? (.indexOf selectedIds (:id dropzoneElement))))
                                  (clear-all-selections-except))))
          
          :on-drag-end #(rf/dispatch [:dnd/set-drag-state false])}
         ;; link widget : popups must be enabled for open in background to work, or else make the whole div an anchor and set target to _blank 
         ;; but then only single click works.
         [:div {:title (str title "\n" url "\n" (.toString (js/Date. lastVisitTime)))
                :style {:overflow "hidden" :user-select "none" :white-space "nowrap" :text-overflow "ellipsis"
                        :display "flex" :align-items "center" :flex-grow "1"}
                :on-double-click #(.open js/window (:url dropzoneElement) "_blank")}
          [:img.icon-link {:draggable false
                           ;; waiting for onload is to hide broken image icons
                           :style {:visibility "hidden"}
                           :on-load #(setstyle (.-target %) "visibility" "visible")
                           :on-error #(setattrib (.-target %) "src" "images/link16.png")
                           :src (cond (#{"chrome://" "chrome-extension://" "file://"} (re-find #"^[a-zA-Z0-9]+://" (:url dropzoneElement)))
                                      "images/link16.png"
                                      (.hasOwnProperty js/chrome "bookmarks")
                                      (str "https://www.google.com/s2/favicons?domain=" (:url dropzoneElement))
                                      :else "images/link16.png")}]          
          (str title ", " id )]]))))



(defmethod dropped-widget
  :link
  [dropzoneElement embedded? embeddedWidgetWidth dropzoneRef lastInCol]
  (let [s (r/atom {})]
    (fn [dropzoneElement embedded? embeddedWidgetWidth dropzoneRef lastInCol]
      (let [thirdOfScreen (int (* (.-innerWidth js/window) 0.3))
            {type :type title :title url :url id :id parentId :parentId dateAdded :dateAdded index :index} dropzoneElement
            parentDropzoneKey (if-let  [searchDropzoneKey (:searchDropzone dropzoneElement)]
                                searchDropzoneKey
                                (fid->dkey (:parentId dropzoneElement)))
            currentDragState (rf/subscribe [:dnd/get-drag-state]) ;; for 1440px monitor about 432px
            
            fetchMenuFrame (when (:componentRef @dropzoneRef) (.closest (:componentRef @dropzoneRef) "div[id^='menu-dropzone']"))
            bottomOfMenuFrame (when fetchMenuFrame (.-bottom (.getBoundingClientRect fetchMenuFrame)))
            topOfLinkElement (when (:componentRef @s) (.-top (.getBoundingClientRect (:componentRef @s))))
            bottomBorderWidthOfMenuFrame (when fetchMenuFrame (px-to-int (get-computed-style fetchMenuFrame "border-bottom-width")))
            elementHeight (when  (:componentRef @s) (px-to-int (get-computed-style (:componentRef @s) "height"))) ;; integer

            ;; The reason why bottomofmenuframe is used instead of clientHeight, is because when scrolling is necessary, subtracting from
            ;; clientHeight will always be negative height, which is interpreted by the browser as 0, so dropping will no longer work.
            ;; With bottomofmenuframe, height is only negative when scrolled out of view.
            ;; eg newtitle222,41: (- 576 535 20 4) ;; 17px exactly
            calcHeight (max 0 (- bottomOfMenuFrame topOfLinkElement (js/Math.round (+ (* .75 (- elementHeight 4)) 2)) bottomBorderWidthOfMenuFrame))

            isCtrlDown @(rf/subscribe [:dnd/get-keystate :ctrlIsDown])
            isShiftDown @(rf/subscribe [:dnd/get-keystate :shiftIsDown])
            contextmenuVisible? @(rf/subscribe [:dnd/get-contextmenu-visible])
            getSelected @(rf/subscribe [:dnd/get-selected parentDropzoneKey])
            clipboardContents @(rf/subscribe [:dnd/get-clipboard])] 
        [:div.link-element
         {:style {:display "flex" :align-items "stretch" :box-sizing "border-box"
                  :position "relative" :width (if embedded? embeddedWidgetWidth thirdOfScreen)
                  ;; without a 2px solid black border, the highlighted radial gradient :background-image upon selection is looks too big
                  ;; the border must be black because otherwise with a transparent border the highlighted radial gradient will show through
                  ;; if visible, and border is white (rightclicked?), keep white
                  ;; if invisible, set to black or transparent, depending on if it's selected
                  ;; :border (if (some #{(:id dropzoneElement)} @(rf/subscribe [:dnd/get-selected parentDropzoneKey]))
                  ;;           "2px solid black" "2px solid transparent")
                  ;; "2px solid black"
                  :border (let [rightclicked? (= dropzoneElement (:rightClicked clipboardContents))
                                selected? (some #{(:id dropzoneElement)} getSelected)]
                            (cond (and contextmenuVisible? rightclicked?) "2px solid white"
                                  selected? (if themeColor "2px solid #222222" "2px solid black")
                                  :else "2px solid transparent"))
                  :background-image (if (some #{(:id dropzoneElement)} getSelected)
                                      "radial-gradient(circle at center, #AF0404 0%, gold 100%)" "none")}
          :id (str "dropped-element-" (name (:id dropzoneElement))) 
          :draggable true ;; required for divs because  only images and links are draggable by default
          ;; DANGER WITHOUT when guard: :ref #(swap! s assoc :componentRef %) ;; will cause infinite updates!
          ;; :ref #(swap! s assoc :componentRef %)
          :ref (fn [el] (when el (swap! s assoc :componentRef el))) 


          :class (cond @currentDragState "link-element no-hover"
                       (or isCtrlDown isShiftDown) "link-element-modifier-down"
                       :else "link-element")

          :on-context-menu (fn [e]
                             (.preventDefault e) ;; disables default right click menu
                             (when (:componentRef @s) (setstyle (:componentRef @s) "border" "2px solid white" ))
                             (dndc/hide-menu dndc/folder-context-menu dndc/folder-move-submenu-container) ;; close folder menu
                             (if-let [currentDz (:searchDropzone dropzoneElement)]
                               (:searchDropzone dropzoneElement) (clear-all-selections-except (fid->dkey (:parentId dropzoneElement))))
                             ;; set the new right clicked element and maintain the selected clipboard contents until pasted or new copy or cut
                             ;;                                                         rightClicked     selected 
                             (rf/dispatch-sync [:dnd/initialize-or-update-clipboard nil dropzoneElement (:selected clipboardContents)])
                             (let [countAllElementsSelected (count (fetch-all-selected))
                                   ;; different from countAllElementsSelected because subfolder links are count
                                   countAllLinksSelected (count (dndc/fetch-links-of-all-selected))]
                               ;; re-enable all menu items (to be safe), because some may have been disabled by :blank widget.
                               (.setEnabled dndc/link-newlink-menuitem true)
                               (.setEnabled dndc/link-newfolder-menuitem true)
                               (.setEnabled dndc/link-insertdivider-menuitem true)
                               (.setEnabled dndc/link-rename-menuitem true)
                               (.setEnabled dndc/link-open-menuitem true)
                               (.setEnabled dndc/link-show-menuitem true)
                               (.setEnabled dndc/link-move-submenu true)
                               (.setEnabled dndc/link-selectall-menuitem true)
                               (.setEnabled dndc/link-cut-menuitem true)
                               (.setEnabled dndc/link-copy-menuitem true)
                               (.setEnabled dndc/link-paste-menuitem true) 
                               (.setEnabled dndc/link-delete-menuitem true)

                               (if (:selected clipboardContents)
                                 (.setEnabled dndc/link-paste-menuitem true)
                                 (.setEnabled dndc/link-paste-menuitem false))
                               (if (> countAllElementsSelected  0)
                                 ;; if all that is selected are 
                                 (do (if (= 0 countAllLinksSelected)
                                       (do (.setCaption dndc/link-open-menuitem "Open in new tab")
                                           (.setEnabled dndc/link-open-menuitem false))
                                       (do (.setEnabled dndc/link-open-menuitem true)
                                           (.setCaption dndc/link-open-menuitem (str "Open all (" (str countAllLinksSelected) ") links in tabs"))))
                                     (.setCaption dndc/link-move-submenu (str "Move (" (str countAllElementsSelected) ") selected to ..."))
                                     (dndc/setCaptionAndAccelerator dndc/link-delete-menuitem (str "Delete (" (str countAllElementsSelected) ") selected") "Del"))
                                 ;; nothing is selected the count is 0 
                                 (do (.setEnabled dndc/link-open-menuitem true)
                                     (.setCaption dndc/link-open-menuitem "Open in new tab")
                                     (.setCaption dndc/link-move-submenu "Move to ...")
                                     (dndc/setCaptionAndAccelerator dndc/link-delete-menuitem "Delete" "Del")))
                               (dndc/place-context-menu dndc/link-context-menu)))
          
          :on-click (fn onclick [event]
                      (let [currentSelectedElements @(rf/subscribe [:dnd/get-selected parentDropzoneKey])
                            dropzoneElementId (:id dropzoneElement)]
                        (cond isCtrlDown (do (clear-all-selections-except parentDropzoneKey)
                                             (rf/dispatch [:dnd/reset-selected (vec (remove #{:anchor} currentSelectedElements))
                                                           parentDropzoneKey])
                                             ;; toggle selection:
                                             (if (some #{dropzoneElementId} currentSelectedElements)
                                               (rf/dispatch [:dnd/remove-selected parentDropzoneKey dropzoneElementId])
                                               (rf/dispatch [:dnd/append-selected parentDropzoneKey dropzoneElementId])))
                              isShiftDown (do (clear-all-selections-except parentDropzoneKey) 
                                              (let [allTabElementIds (map :id @(rf/subscribe [:dnd/dropped-elements parentDropzoneKey]))
                                                    ;; if no selections then shift click sets the anchor, if anchor exists do nothing
                                                    ;; if selections exist, but no anchor exists then the last selected element becomes anchor
                                                    setAnchorTabElements (cond (empty? currentSelectedElements) [(:id dropzoneElement) :anchor]
                                                                               (some #{:anchor} currentSelectedElements) currentSelectedElements
                                                                               :else (conj  (vec currentSelectedElements) :anchor))
                                                    ;; setAnchorTabElements in the above line guarantees an :anchor exists so you can get it:
                                                    anchorElement (nth setAnchorTabElements (dec (.indexOf setAnchorTabElements :anchor)))
                                                    anchorIndex (.indexOf allTabElementIds anchorElement)
                                                    indexOfShiftClickedElement (.indexOf allTabElementIds dropzoneElementId)

                                                    newSelectedTabElements
                                                    (let [newSelectedTabElementsNoAnchor
                                                          ;; select all elements between the anchor and the shift clicked element whether it
                                                          ;; is before or after the anchor:
                                                          (for [index (range (min anchorIndex indexOfShiftClickedElement)
                                                                             ;; inc because range open upper bound
                                                                             (inc (max anchorIndex indexOfShiftClickedElement)))]
                                                            (nth allTabElementIds index))
                                                          ;; restore the anchor as established in setAnchorTabElements
                                                          [before after] (split-at (inc (.indexOf newSelectedTabElementsNoAnchor anchorElement))
                                                                                   newSelectedTabElementsNoAnchor)]
                                                      (vec (concat before [:anchor] after)))]
                                                (rf/dispatch [:dnd/reset-selected newSelectedTabElements parentDropzoneKey])))
                              
                              :else (clear-all-selections-except))))
          
          :on-drag-start #(do (rf/dispatch [:dnd/set-drag-state true]) 
                              #_(println "started dragging") 
                              #_(.setData (.-dataTransfer %) "text/my-custom-data"
                                          (str (name (:type dropzoneElement)) "," (:id dropzoneElement) ","
                                               (str "dropzone-" (:parentId dropzoneElement))))
                              (.setData (.-dataTransfer %) "text/my-custom-data" (pr-str dropzoneElement))
                              ;; this allows link to be dropped on the location bar but ignored by drop handlers which take my-custom-data type
                              ;; note that multiple urls in text/uri-list do not open in tabs, so it's useless? unless maybe dropping onto bookmarks
                              (.setData (.-dataTransfer %) "text/plain" (:url dropzoneElement))
                              (let [selectedIds (map :id (fetch-all-selected))]
                                ;; if the dragged element is not among the selectedIds, then unselect all immediately
                                ;; ie. (dispatch-sync version of clear-all-selections-except) so that center-overlay-ondrop
                                ;;     will not move the selected elements when :on-drop event occurs, only the unselected element
                                (when (not (nat-int? (.indexOf selectedIds (:id dropzoneElement))))
                                  (clear-all-selections-except))))
          
          :on-drag-end #(rf/dispatch [:dnd/set-drag-state false])}
         ;; link widget : popups must be enabled for open in background to work, or else make the whole div an anchor and set target to _blank 
         ;; but then only single click works. 
         [:div {:title (str title "\n" url)
                :style {:overflow "hidden" :user-select "none" :white-space "nowrap" :text-overflow "ellipsis"
                        :display "flex" :align-items "center" :flex-grow "1"}
                :on-double-click #(if (.hasOwnProperty js/chrome "bookmarks")
                                    (.create js/chrome.tabs #js {"active" false "url" (:url dropzoneElement)})
                                    (.open js/window (:url dropzoneElement) "_blank"))}
          [:img.icon-link {:draggable false :height "16" :width "16"
                           ;; waiting for onload is to hide broken image icons
                           :style {:visibility "hidden"}
                           :on-load #(setstyle (.-target %) "visibility" "visible")
                           :on-error #(setattrib (.-target %) "src" "images/link16.png")
                           :src (cond (#{"chrome://" "chrome-extension://" "file://"} (re-find #"^[a-zA-Z0-9]+://" (:url dropzoneElement)))
                                      "images/link16.png"
                                      (.hasOwnProperty js/chrome "bookmarks")
                                      ;; (str "https://api.statvoo.com/favicon/?url=" (:url dropzoneElement))
                                      (str "https://www.google.com/s2/favicons?domain=" (:url dropzoneElement))
                                      :else "images/link16.png")}]
          [:span {:style {:overflow "hidden" :text-overflow "ellipsis"}} title ]]
         
         ;; overlay 1
         [link-overlay "calc(-25% - 4px)" "calc(50% + 4px)" s currentDragState dropzoneElement :top]
         
         ;; overlay 2
         [link-overlay "25%" "25%" s currentDragState dropzoneElement :top]
         
         ;; overlay 3
         ;; is ignored (overlapped by the above one) except for the bottom elements in the column. The indices generated by dropping
         ;; on the top overlay coincidentally always work out, because dropping on the bottom half of a non-bottom element is actually 
         ;; dropping on the top half of the overlay of the next element. And the dispatch always drops before it's target element.
         ;; height = 50%=14px + 4px dummy element + 5px drop-zone padding
         [link-overlay "50%" "25%" s currentDragState dropzoneElement :bottom]

         ;; overlay 4
         ;; is ignored (overlapped by the above one) except for the bottom elements in the column. The indices generated by dropping
         ;; on the top overlay coincidentally always work out, because dropping on the bottom half of a non-bottom element is actually
         ;; dropping on the top half of the overlay of the next element. And the dispatch always drops before it's target element.
         ;; height = 50%=14px + 4px dummy element + 5px drop-zone padding
         [link-overlay "75%" (if lastInCol (str calcHeight "px") "calc(50% + 4px)") s currentDragState dropzoneElement :bottom]

         ]))))


(defmethod dropped-widget
  :dummy [de embedded? embeddedWidgetWidth dropzoneRef lastInCol]
  (let [s (r/atom nil)
        dummyClass (r/atom "dummy-element")

        dzGeomWidth (r/atom (when (:componentRef @dropzoneRef)  (getstyle (:componentRef @dropzoneRef) "width")))
        dzGeomHeight (r/atom (when (:componentRef @dropzoneRef)  (getstyle (:componentRef @dropzoneRef) "height")))
        
        fetchMenuFrame (when (:componentRef @dropzoneRef) (.closest (:componentRef @dropzoneRef) "div[id^='menu-dropzone']"))
        ;; $0.closest("div[id^='menu-dropzone']").getBoundingClientRect().bottom ;;=> 512
        bottomOfMenuFrame (when fetchMenuFrame (.-bottom (.getBoundingClientRect fetchMenuFrame)))
        ;; $0.getBoundingClientRect().top ;; => 415
        ;; topOfDummyElement (when @s (.-top (.getBoundingClientRect @s)))
        topOfDummyElement (when (:componentRef @s) (.-top (.getBoundingClientRect (:componentRef @s))))
        ;; eg. 426 - 415 = 11 which is 2px + 9px(5px btm dz padding and 4px dropmarker). Embedded menu border is "border: 2px solid white;" inline
        ;; eg. 512 - 499 = 13 which is 4 more than 9px(5px btm dz padding and 4px dropmarker) or 4px is exact border width of floating menu frame
        ;; getComputedStyle($0.closest("div[id^='menu-dropzone']")).getPropertyValue("border-bottom-width") ;; => '2px' or '4px'
        bottomBorderWidthOfMenuFrame (when fetchMenuFrame (get-computed-style fetchMenuFrame "border-bottom-width"))
        gridTemplateRows (when (:componentRef @dropzoneRef) (get-computed-style (:componentRef @dropzoneRef) "grid-template-rows"))
        paddingBottom (when (:componentRef @dropzoneRef) (get-computed-style (:componentRef @dropzoneRef) "padding-bottom"))
        dzHeight (apply + (map js/parseInt (clojure.string/split (str gridTemplateRows " " paddingBottom) #" ")))
        elementOffsetTop (when  (:componentRef @s) (get-property (:componentRef @s) "offsetTop"))
        scrollbar? (when (:componentRef @dropzoneRef) (> (get-property (:componentRef @dropzoneRef) "scrollHeight")
                                                         (get-property (:componentRef @dropzoneRef) "clientHeight")))

        ;; if a scrollbar is present use the grid-template-rows to a priori calculate the minimal height for any dummy element,
        ;; because using the dropzone height will result in a feedback loop increasing the dummy element height arbitrarily.
        calcHeight (if scrollbar? (- dzHeight elementOffsetTop)
                       (max 0 (- bottomOfMenuFrame topOfDummyElement bottomBorderWidthOfMenuFrame)))
        
        highlight (r/atom false)]
    (fn [de embedded? embeddedWidgetWidth dropzoneRef lastInCol]
      (let [calcHeight 
            (if lastInCol
              (let [dzGeomWidth (r/atom (when (:componentRef @dropzoneRef)  (getstyle (:componentRef @dropzoneRef) "width")))
                    dzGeomHeight (r/atom (when (:componentRef @dropzoneRef)  (getstyle (:componentRef @dropzoneRef) "height")))

                    fetchMenuFrame (when (:componentRef @dropzoneRef) (.closest (:componentRef @dropzoneRef) "div[id^='menu-dropzone']"))
                    ;; $0.closest("div[id^='menu-dropzone']").getBoundingClientRect().bottom ;;=> 512
                    bottomOfMenuFrame (when fetchMenuFrame (.-bottom (.getBoundingClientRect fetchMenuFrame)))
                    ;; $0.getBoundingClientRect().top ;; => 415
                    topOfDummyElement (when (:componentRef @s) (.-top (.getBoundingClientRect (:componentRef @s))))
                    ;; eg. 426 - 415 = 11 which is 2px + 9px(5px btm dz padding and 4px dropmarker). Embedded menu border is "border: 2px solid white;" inline
                    ;; eg. 512 - 499 = 13 which is 4 more than 9px(5px btm dz padding and 4px dropmarker) or 4px is exact border width of floating menu frame
                    ;; getComputedStyle($0.closest("div[id^='menu-dropzone']")).getPropertyValue("border-bottom-width") ;; => '2px' or '4px'
                    bottomBorderWidthOfMenuFrame (when fetchMenuFrame (px-to-int (get-computed-style fetchMenuFrame "border-bottom-width")))

                    gridTemplateRows (when (:componentRef @dropzoneRef) (get-computed-style (:componentRef @dropzoneRef) "grid-template-rows"))
                    paddingBottom (when (:componentRef @dropzoneRef) (get-computed-style (:componentRef @dropzoneRef) "padding-bottom"))
                    dzHeight (apply + (map js/parseInt (clojure.string/split (str gridTemplateRows " " paddingBottom) #" ")))
                    elementOffsetTop (when  (:componentRef @s) (get-property (:componentRef @s) "offsetTop"))

                    dzScrollHeight (get-property (:componentRef @dropzoneRef) "scrollHeight")
                    dzClientHeight (get-property (:componentRef @dropzoneRef) "clientHeight")
                    dzScrollWidth (get-property (:componentRef @dropzoneRef) "scrollWidth")
                    dzClientWidth (get-property (:componentRef @dropzoneRef) "clientWidth")
                    ;; scrollbar? (when (:componentRef @dropzoneRef) (> (get-property (:componentRef @dropzoneRef) "scrollHeight")
                    ;;                                                  (get-property (:componentRef @dropzoneRef) "clientHeight")))
                    scrollbar? (when (:componentRef @dropzoneRef) (> (- dzScrollHeight
                                                                        ;; subtract the horizontal scrollbar height of 17px if it exists else 0
                                                                        (if (> dzScrollWidth dzClientWidth)
                                                                          ;; (- offsetHeight clientHeight) is scrollbar height of 17px
                                                                          (- (get-property (:componentRef @dropzoneRef) "offsetHeight")
                                                                             dzClientHeight)
                                                                          0))
                                                                     dzClientHeight))]
                ;; if a scrollbar is present use the grid-template-rows to a priori calculate the minimal height for any dummy element,
                ;; because using the dropzone height will result in a feedback loop increasing the dummy element height arbitrarily.
                (if scrollbar? (- dzHeight elementOffsetTop) 
                    (max 0 (- bottomOfMenuFrame topOfDummyElement bottomBorderWidthOfMenuFrame
                              ;; subtract the horizontal scrollbar height of 17px if it exists else 0
                              (if (> dzScrollWidth dzClientWidth)
                                ;; (- offsetHeight clientHeight) is scrollbar height of 17px
                                (- (get-property (:componentRef @dropzoneRef) "offsetHeight")
                                   dzClientHeight)
                                0)))))
              ;; if not lastincol just use 4px reduces computations
              "4px")

            rightclicked? (= de (:rightClicked @(rf/subscribe [:dnd/get-clipboard])))
            contextmenuVisible? @(rf/subscribe [:dnd/get-contextmenu-visible])]
        [:div
         {:id (str "dropped-element-" (name (:id de)))
          ;; :ref #(swap! s assoc :componentRef %)
          ;; :ref (fn [el] (when el (reset! s el)))
          :ref (fn [el] (when el (swap! s assoc :componentRef el)))
          :title "insert or paste"
          :class @dummyClass

          :data-lastincol (if lastInCol (pr-str de) "false") ;; the index is used by ctrl-v hover paste
          
          :on-mouse-over #(when lastInCol (reset! highlight true))
          :on-mouse-out #(when lastInCol (reset! highlight false))

          :on-context-menu (fn [e] (when lastInCol
                                     ;; this is a copy and paste from :blank widget :on-context-menu except for initialize-or-update-clipboard
                                     ;; uses "de" as the rightclicked argument, instead of (assoc de :index 0)
                                     (.preventDefault e) ;; disables default right click menu
                                     ;; close folder menu, there is no need to hide the link menu because it will placed below
                                     (reset! highlight true)
                                     (dndc/hide-menu dndc/folder-context-menu dndc/folder-move-submenu-container) 
                                     ;; folderbutton approach:
                                     ;; set the new right clicked element and maintain the selected clipboard contents until pasted or new copy or cut
                                     ;; because the new right clicked element will affect the count of dndc/fetch-links-of-all-selected
                                     ;;                                                            rightClicked     selected 
                                     ;; (rf/dispatch-sync [:dnd/initialize-or-update-clipboard nil dropzoneElement  (:selected @(rf/subscribe [:dnd/get-clipboard]))])
                                     ;; link approach
                                     ;; set the new right clicked element and maintain the selected clipboard contents until pasted or new copy or cut
                                     ;;                                                            rightClicked          selected 
                                     ;; (rf/dispatch-sync [:dnd/initialize-or-update-clipboard nil dropzoneElement      (:selected @(rf/subscribe [:dnd/get-clipboard]))])
                                     ;; newPageDialog, newFolderDialog, insertDividerDialog, and pasteFromClipboard all only use :index and :parentId from rightclicked clipboard element
                                     ;; ["from :blank widget: de:" {:type :blank, :id "blank-element1", :parentId "4418"}] ;; missing :index 
                                     ;; set the blank element as the rightClicked element for use in dialogs, but set it's index to 0:
                                     ;; lastincol dummy widget looks like: {:id "dummyid3472", :type :dummy, :index 7, :parentId "3471", :last-in-col true}
                                     (rf/dispatch-sync [:dnd/initialize-or-update-clipboard    nil de  (:selected @(rf/subscribe [:dnd/get-clipboard]))])
                                     (.setEnabled dndc/link-newlink-menuitem true)
                                     (.setEnabled dndc/link-newfolder-menuitem true)
                                     (.setEnabled dndc/link-insertdivider-menuitem true)
                                     (.setEnabled dndc/link-rename-menuitem false)
                                     (.setCaption dndc/link-open-menuitem "Open in new tab")
                                     (.setEnabled dndc/link-open-menuitem false)
                                     (.setEnabled dndc/link-show-menuitem false)
                                     (.setCaption dndc/link-move-submenu "Move to ...")
                                     (.setEnabled dndc/link-move-submenu false)
                                     (.setEnabled dndc/link-selectall-menuitem true)
                                     (.setEnabled dndc/link-cut-menuitem false)
                                     (.setEnabled dndc/link-copy-menuitem false)
                                     (if (:selected @(rf/subscribe [:dnd/get-clipboard]))
                                       (.setEnabled dndc/link-paste-menuitem true)
                                       (.setEnabled dndc/link-paste-menuitem false))
                                     (dndc/setCaptionAndAccelerator dndc/link-delete-menuitem "Delete" "Del")
                                     (.setEnabled dndc/link-delete-menuitem false)
                                     (dndc/place-context-menu dndc/link-context-menu)))
          
          :style {:width (if embedded? embeddedWidgetWidth)
                  :background (when (or (and contextmenuVisible? rightclicked?) @highlight)
                                "linear-gradient(to bottom, white 2px, rgba(0,0,0,0) 4px, rgba(0,0,0,0) 100%)")
                  :cursor (when lastInCol "pointer")
                  :height calcHeight}}]))))


(declare cutoffDropzoneElements)
(defmethod dropped-widget
  :loadmorebutton
  [dropzoneElement embedded? embeddedWidgetWidth dropzoneRef lastInCol]
  (let [s (r/atom {})]
    (fn [dropzoneElement embedded? embeddedWidgetWidth dropzoneRef lastInCol]
      (let [tabOrHistorySelected @(rf/subscribe [:dnd/get-tabOrHistorySelected])
            cutoffDropzoneElements (rf/subscribe [:dnd/get-cutoff-elements (:id dropzoneElement) tabOrHistorySelected]) 
            countAllDroppedElements (:countAll dropzoneElement)
            loadMoreAmount (min 50 (max 0 (- countAllDroppedElements @cutoffDropzoneElements)))
            loadAllAmount (max 0 (- countAllDroppedElements @cutoffDropzoneElements))
            thirdOfScreen (int (* (.-innerWidth js/window) 0.3))]
        
        [:div.link-element
         {:style {:display "flex" :align-items "stretch" :box-sizing "border-box" :justify-content "space-evenly"
                  :position "relative" :width (if embedded? embeddedWidgetWidth thirdOfScreen)
                  :border "2px solid transparent"}
          
          :id "loadmorebutton"
          ;; :draggable true ;; required for divs because  only images and links are draggable by default
          ;; DANGER WITHOUT "when" to guard: :ref #(swap! s assoc :componentRef %) ;; will cause infinite updates!
          ;; :ref (fn [el] (when el (swap! s assoc :componentRef el)))
          ;; :ref #(swap! s assoc :componentRef %)
          :ref (fn [el] (when el (swap! s assoc :componentRef el)))}

         (when (not= loadMoreAmount loadAllAmount)
           [:div.loadButton {:on-click #(rf/dispatch [:dnd/reset-cutoff-elements (:id dropzoneElement)
                                                      (+ @cutoffDropzoneElements loadMoreAmount) tabOrHistorySelected]) 
                             :style {:overflow "hidden" :user-select "none" :white-space "nowrap" :text-overflow "ellipsis"
                                     :flex-grow "1"
                                     :display "flex" :align-items "center" :justify-content "space-evenly"  }}
            [:span {:style {:overflow "hidden" :text-overflow "ellipsis"}} (str "load " loadMoreAmount " more") ]])

         [:div.loadButton {:on-click #(rf/dispatch [:dnd/reset-cutoff-elements (:id dropzoneElement)
                                                    countAllDroppedElements tabOrHistorySelected])
                           :style {:overflow "hidden" :user-select "none" :white-space "nowrap" :text-overflow "ellipsis" :flex-grow "1"
                                   :display "flex" :align-items "center" :justify-content "space-evenly"  }}
          [:span {:style {:overflow "hidden" :text-overflow "ellipsis"}} (str "load all " loadAllAmount " more") ]]]))))



;; see: \bookmarkext-clojurescript-notes\example-dropzone-component.cljs for example walkthrough
(defn drop-zone 
  [id embedded? changeColumns positionState visibilityState searchText show-containing-folder-element-id]
  ;; show-containing-folder-element-id is ignored but necessary as this is the render function for
  ;; set-position-and-render-dropzone in Menu component
  ;; id is a key of the form :dropzone-26
  ;;[id embedded? changeColumns searchText] ;; id is a key of the form :dropzone-26
  (let [allDroppedElements (rf/subscribe [:dnd/dropped-elements id])
        cutoffDropzoneElements (rf/subscribe [:dnd/get-cutoff-elements id]) 
        dropped-elements (take @cutoffDropzoneElements @allDroppedElements)
        ;; The thirdOfScreen for dropped-elements is 0.3, thirdOfScreenPadding is 0.32 to provide empty 2% padding with the scrollbar
        ;; for 1440px monitor 30% is 432px 32% is 461px, so 29px of padding 
        s (r/atom {}) ;; hold on to a dom reference to the button using reagent :ref attribute below
        dzGeomWidth (r/atom nil)
        dzGeomHeight (r/atom nil)
        observerGuard (r/atom true)
        dzObserver (new js/ResizeObserver (fn [entries]
                                            (let [contentRect (.-contentRect (first (array-seq entries)))]
                                              (reset! dzGeomWidth (.-width contentRect))
                                              (reset! dzGeomHeight (.-height contentRect))
                                              ;;(when (:componentRef @s) (setstyle (:componentRef @s) "width" "auto" )) ;; fail
                                              ;;(when (:componentRef @s) (.click (:componentRef @s) )) ;; fail
                                              )))

        ;; embeddedMenuConfiguration
        ;; [{:optionClass "tabOption", :show true, :startCollapsed true, :defaultColumns 4, :minRows 103, :maxRows 109}
        ;;  {:optionClass "historyOption", :show true, :startCollapsed false, :defaultColumns 4, :minRows 85, :maxRows 89}
        ;;:dropzone-1 is index 2:  {:optionClass "barOption", :show true, :startCollapsed true, :defaultColumns 5, :minRows 55, :maxRows 69}
        ;;:dropzone-2 is index 3:  {:optionClass "otherOption", :show false, :startCollapsed false, :defaultColumns 5, :minRows 34, :maxRows 54}]
        storedArrayIndex (if (= id :dropzone-1) 2 3)
        possibleColumnsEmbedded (r/atom (max (min (:defaultColumns (nth embeddedMenuConfiguration storedArrayIndex)) 8) 1))
        minRows (max (min (:minRows (nth embeddedMenuConfiguration storedArrayIndex)) 999) 1)
        maxRows (max (min (:maxRows (nth embeddedMenuConfiguration storedArrayIndex)) 999) 1)

        collapsedStartupToggle (rf/subscribe [:dnd/get-collapsedStartupToggle id])
        
        cRows-fn (fn [totalElements possibleColumnsEmbedded] (js/Math.ceil (max (/ totalElements possibleColumnsEmbedded) 1)))

        next-biggest-num (fn [possibleColumnsEmbeddedArray x] (if-let [nextBiggestFound (first (filter #(< x %) possibleColumnsEmbeddedArray))]
                                                                nextBiggestFound (last possibleColumnsEmbeddedArray)))
        prev-smallest-num (fn [possibleColumnsEmbeddedArray x] (if-let [prevSmallestFound (last (filter #(< % x) possibleColumnsEmbeddedArray))]
                                                                 prevSmallestFound (first possibleColumnsEmbeddedArray)))]

    
    (fn [id embedded? changeColumns positionState visibilityState searchText]
      (let [dropped-elements (take @cutoffDropzoneElements @(rf/subscribe [:dnd/dropped-elements id])) 

            fetchMenuFrame (when (:componentRef @s) (.closest (:componentRef @s) "div[id^='menu-dropzone']"))
            ;; supposedly an observer is garbage collected when it's htmlelement has been destroyed?
            _ (when (and @observerGuard dzObserver (:componentRef @s))
                (reset! observerGuard false)
                (.observe dzObserver (:componentRef @s)))

            ;; if there are more elements than the cutoff, then make room in totalElements for the :loadmorebutton
            totalElements (if (> (count @allDroppedElements) @cutoffDropzoneElements) (inc (count dropped-elements)) (count dropped-elements))
            
            possibleColumnsEmbeddedArray (reduce (fn [x y] (conj x (first y))) []
                                                 (vals (group-by (partial cRows-fn totalElements) [1 2 3 4 5 6 7 8])))
            ;;;;(next-biggest-num possibleColumnsEmbeddedArray 3);; possibleColumnsEmbeddedArray starts as 
            ;;_ (if (nil? @possibleColumnsEmbedded) (reset! possibleColumnsEmbedded 4)) 
            thirdOfScreenPadding (int (* (.-innerWidth js/window) 0.32))  ;; padding of 2% for scrollbar
            computedStyle (when (:componentRef @s) (.getComputedStyle js/window (:componentRef @s) nil))
            padding (when computedStyle ( + (js/parseFloat (.-paddingLeft computedStyle))
                                         (js/parseFloat (.-paddingRight computedStyle))))
            scrollbar (when (:componentRef @s) (- (.-offsetWidth (:componentRef @s) )
                                                  (.-clientWidth (:componentRef @s) )))
            ;; element width
            stringWidth (when computedStyle (.-width computedStyle)) ;; eg// returns "1296px"
            intWidth (js/parseInt (when stringWidth (subs stringWidth 0 (- (count stringWidth) 2)))) ;; returns 1296

            ;; height is not used
            ;; stringHeight (when (:componentRef @s) (.-height (.getComputedStyle js/window (:componentRef @s) nil))) ;; returns "305px"
            ;; intHeight (int (when stringWidth (subs stringHeight 0 (- (count stringHeight) 2)))) ;; returns 305

            ;; (+ 50 intWidth) accounts for 17px scrollbar width rounded to 20 and 30px to see up to beginning of icons
            ;; returns 1296/415 = 3.12286... = 4 ceil calculates visible cols and possibly 1 invisible column
            ;; intWidth - 30 for floating dropzone causes 30px resizable before possibleColumnsFloating is incremented
            possibleColumnsFloating (js/Math.ceil (max (/ (- intWidth 30) thirdOfScreenPadding) 1))
            ;; returns 1296/415 = 3.12286... = 3 int calculates only visible columns, intWidth -30 is unecessary because embedded not resized

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

            embeddedWidgetWidth (int (/ (- intWidth padding scrollbar) (min totalElements @possibleColumnsEmbedded)) )
            
            ;; ceil ensures the last col will never be full
            cRows (if embedded? (js/Math.ceil (max (/ totalElements @possibleColumnsEmbedded) 1)) 
                      (js/Math.ceil (max (/ totalElements possibleColumnsFloating) 1)))
            
            
            gridElements ;; gridElements: alternate dummy with dropped-elements, but add two dummy elements for every new column
            (cond (or (= totalElements 0) (and (= totalElements 1) (= (first dropped-elements) :folder-has-no-children)))
                  ;; dummy newcol elements are never given indexes, because they will never be dropped onto,
                  ;; including in the context of the loadmorebutton below
                  (list {:id "dummyid-newcol0" :type :dummy :parentId (dkey->fid id)}
                        {:type :blank :id "blank-element1" :parentId (dkey->fid id)}) ;;(subs (name id) 9)
                  (and (= totalElements 1) (= (first dropped-elements) :search-not-found)) 
                  (when (:componentRef @s)
                    (list {:type :search-not-found :id "search-not-found-id"
                           :searchText @searchText
                           :searchBoxRef (if embedded? (.querySelector (.-parentNode (:componentRef @s)) "#search")
                                             (.querySelector (.-parentNode (:componentRef @s)) "#floatingsearch"))
                           :parentId "search-not-found-parent-id"}))
                  :else
                  (let [alternating-elts (flatten (for [x dropped-elements]
                                                    (list x {:id (str "dummyid" (:id x)) :type :dummy
                                                             ;; the index of dummy elements are inc by 1 so that pasting in lastincol will drop after
                                                             :index (inc (:index x)) :parentId (dkey->fid id)})))
                        part-result (partition-all (* 2 cRows) alternating-elts)
                        part-result-withlastcol
                        ;; mark the last element in every column to set it's bottom overlay to extend to the bottom of the 
                        ;; dropzone in the widget
                        ;;(split-at 2 [1 2 3 4 5]) ;;=> [(1 2) (3 4 5)] 
                        ;;(let [[before after] (split-at 3 [1 2 3 4 5 6])] (vec (concat before ['a 'b 'c] after))) ;; => [1 2 3 a b c 4 5 6]
                        (map (fn [part] 
                               (let [[before after] (split-at (- (count part) 2) part)]
                                 (concat before (map #(assoc % :last-in-col true) after)))) part-result)]
                    
                    ;; Add the :loadmorebutton
                    (if (> (count @allDroppedElements) @cutoffDropzoneElements)
                      ;; if each column has equal number of elements and there is more than one column, then the loadmorebutton starts a new column, 
                      ;; and should have an extra "dummyid-newcol" element preceding it, or not otherwise.
                      ;; in case cutoff is to display only 1 element then a new col for loadmore must be started
                      (if (or (= 1 @cutoffDropzoneElements) 
                              (and (> (count part-result-withlastcol) 1) (apply = (map count part-result-withlastcol))))                          
                        (concat
                         (flatten (map-indexed
                                   (fn [idx itm] (concat (list {:id (str "dummyid-newcol" idx) :type :dummy :parentId (dkey->fid id)} ) itm))
                                               part-result-withlastcol))
                         [{:id (str "dummyid-newcol" (count part-result-withlastcol) ) :type :dummy :parentId (dkey->fid id)}
                          {:type :loadmorebutton :id id :countAll (count @allDroppedElements)}])
                        (concat
                         (flatten (map-indexed
                                   (fn [idx itm] (concat (list {:id (str "dummyid-newcol" idx) :type :dummy :parentId (dkey->fid id)} ) itm))
                                               part-result-withlastcol))
                         [{:type :loadmorebutton :id id :countAll (count @allDroppedElements)}]))

                      ;; if there is no cutoff necessary then don't add a loadmore button
                      (flatten (map-indexed
                                (fn [idx itm] (concat (list {:id (str "dummyid-newcol" idx) :type :dummy :parentId (dkey->fid id)} ) itm))
                                part-result-withlastcol)))))]

        @dzGeomWidth ;; force update trick
        @dzGeomHeight ;; force update trick
        [:div
         ;; this sets the html id tag of the dropzone to drop-zone-"id argument passed in"
         {;; DANGER WITHOUT "when" to guard: :ref #(swap! s assoc :componentRef %) ;; will cause infinite updates!
          ;; :ref (fn [el] (when el (swap! s assoc :componentRef el))) 
          :ref (fn [el] (when el (swap! s assoc :componentRef el))) 
          :draggable "false"  
          :id        (str "drop-zone-" (name id)) 
          :class (if embedded? "drop-zone-embedded" "drop-zone-floating")
          :style {:display (cond (and embedded? @collapsedStartupToggle) "none"
                                 (or (= totalElements 0) (and (= totalElements 1) (= (first dropped-elements) :search-not-found))) "block"
                                 :else "grid")
                  :grid-template-rows (str "repeat("  cRows ", 4px 28px) 4px")
                  :width (when (not embedded?) thirdOfScreenPadding)
                  :min-width (when (not embedded?) thirdOfScreenPadding)
                  ;; :max-height : is 9 rows or : 297 = 9 * (32=28+4) + 4 + 5
                  ;; :min-height : if unset, an empty embeddedmenu will take on the minimum height of a :blank element which is 4px dummy
                  ;; 28px dropmarker 4px lastincol = 36 pixels + 5 pixels dropzone-embedded bottom padding = 41 pixels
                  ;; (str (+ (* n 32) 9)
                  ;; :min-height (if embedded? "41px" (/ thirdOfScreenPadding 4))
                  ;; :max-height (when embedded? "297px")}
                  :min-height (if embedded? (str (+ (* minRows 32) 9) "px") (/ thirdOfScreenPadding 4))
                  :max-height (when embedded? (str (+ (* maxRows 32) 9) "px"))}
          
          :on-context-menu (fn [e] (.preventDefault e)) ;; disable chrome's context menu
          
          :on-click #(let [ targetClass (.-className (.-target %))]
                       
                       (cond (= targetClass "drop-zone-embedded") (clear-all-selections-except)
                             (= targetClass "drop-zone-floating") (clear-all-selections-except)
                             (= targetClass "dummy-element") (clear-all-selections-except)))} 
         ;; the following constructs the actual drop zone elements, doall to unroll lazyseq required for atomic reference
         (doall (map (fn [de]
                       ^{:key de}
                       [dropped-widget de embedded? embeddedWidgetWidth s
                        (if (:last-in-col de) {:width @dzGeomWidth :height @dzGeomHeight} false) ])
                     gridElements))]))))






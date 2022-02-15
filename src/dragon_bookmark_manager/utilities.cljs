(ns dragon-bookmark-manager.utilities
  (:require
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [re-frame.core :as rf]
   [clojure.zip :as zip]
   [goog.object :as gobj]
   [cljs.reader]
   [clojure.pprint]))

;; << utility functions >> 
;; From: https://stackoverflow.com/questions/33446913/reagent-react-clojurescript-warning-every-element-in-a-seq-should-have-a-unique
;; (def uniqkey (atom 0))
;; (defn gen-key []
;;   (let [res (swap! uniqkey inc)]
;;     res))

;; GLOBAL CONFIGURAION VALUES

(def lastZIndex (atom 0))


;; fetch and set global: defaultCutoffDropzoneElements
;; Used in EmbeddedMenuTabs, Menu, and EmbeddedMenu, FolderButton, show-folder, recursive-drop-offline-new
;; set defaultCutoffDropzoneElements to localstorage value of 'defaultcutoff'
;; if the defaultcutoff is NaN  then set it to 100 and return 100
;; empty string case: (js/isNaN (js/parseInt "")) ;;=> true (js/isNaN (js/parseInt "e")) ;;=> true
(def defaultCutoffDropzoneElements
  (let [defaultcutoff (js/parseInt (.getItem js/localStorage "defaultcutoff"))]
    (if (or (js/isNaN defaultcutoff) (< defaultcutoff 1)) (do (.setItem js/localStorage "defaultcutoff" 100) 100)
        defaultcutoff)))

;; fetch and set global: embeddedMenuConfiguration
(try
  (let [embeddedMenuConfigurationString (.getItem js/localStorage "embeddedMenuConfiguration")
        configArrayJs (.parse js/JSON embeddedMenuConfigurationString)]
    ;; nb: throw does not return
    (if (nil? configArrayJs)
      (throw {:type :custom-error :message "utilities.cljs: (def embeddedMenuConfiguration ...): configArray: (.parse js/JSON embeddedMenuConfigurationString) returned null"})
      (let [configArrayCljs (js->clj configArrayJs :keywordize-keys true)
            arrayCheck (= cljs.core/PersistentVector (type configArrayCljs))
            optionNameCheck (= (map :optionClass configArrayCljs) '("tabOption" "historyOption" "barOption" "otherOption"))
            lengthCheck (= 4 (count configArrayCljs))
            booleanCheck (every? identity (concat (map (comp boolean? :show) configArrayCljs)
                                                  (map (comp boolean? :startCollapsed) configArrayCljs)))

            integerCheck (every? identity (concat (map (comp integer? :defaultColumns) configArrayCljs)
                                                  (map (comp integer? :minRows) configArrayCljs)
                                                  (map (comp integer? :maxRows) configArrayCljs)))
            ]
        (if (and arrayCheck optionNameCheck lengthCheck booleanCheck integerCheck)
          (def embeddedMenuConfiguration configArrayCljs)
          (throw {:type :custom-error :message "utilities.cljs: (try ... (def embeddedMenuConfiguration ...) ...): The stored array is invalid "})))))
  (catch :default e
    (println "This is first startup or an error occured from utilities.cljs: (def embeddedMenuConfiguration ...): " e)
    (.setItem js/localStorage "embeddedMenuConfiguration" "[{\"optionClass\":\"tabOption\",\"show\":true,\"startCollapsed\":false,\"defaultColumns\":4,\"minRows\":1,\"maxRows\":9},{\"optionClass\":\"historyOption\",\"show\":true,\"startCollapsed\":false,\"defaultColumns\":4,\"minRows\":1,\"maxRows\":9},{\"optionClass\":\"barOption\",\"show\":true,\"startCollapsed\":false,\"defaultColumns\":5,\"minRows\":1,\"maxRows\":9},{\"optionClass\":\"otherOption\",\"show\":true,\"startCollapsed\":false,\"defaultColumns\":5,\"minRows\":1,\"maxRows\":9}]")
    (def embeddedMenuConfiguration
      [{:optionClass "tabOption",     :show true, :startCollapsed false, :defaultColumns 4, :minRows 1, :maxRows 9}
       {:optionClass "historyOption", :show true, :startCollapsed false, :defaultColumns 4, :minRows 1, :maxRows 9}
       {:optionClass "barOption",     :show true, :startCollapsed false, :defaultColumns 5, :minRows 1, :maxRows 9}
       {:optionClass "otherOption",   :show true, :startCollapsed false, :defaultColumns 5, :minRows 1, :maxRows 9}])))

;; fetch and set global: themeColor, if 'theme' does not exist, undefined or malformed in localstorage then this will set it.
(let [themeString (.getItem js/localStorage "theme")]
  (if (or (= themeString "false") (= themeString "true"))
    (def themeColor (cljs.reader/read-string themeString))
    (do (.setItem js/localStorage "theme" false)
        (def themeColor false))))

;; set the body color:
(if themeColor (gobj/set (.querySelector js/document "body") "className" "lightMode")
    (gobj/set (.querySelector js/document "body") "className" "darkMode")) 

(defn gen-next-zindex 
  ([]  (let [res (swap! lastZIndex inc)] res)))

(defn fid->dkey [folderId] (keyword (str "dropzone-" folderId)))
(defn dkey->fid [dropzone-id] (subs (name dropzone-id) 9)) ;; 9 is magic no. of characters in "dropzone-"

(defn get-dz-element
  [db dz-id draggable-id]
  (->>
   (get-in db [:dnd/state :drop-zones dz-id])
   (filter (comp (partial = draggable-id) :id));; filter previous line by draggable id equal to "draggable-id"
   first)) ;; retrieve first result of filter

;; from https://stackoverflow.com/questions/32467299/clojurescript-convert-arbitrary-javascript-object-to-clojure-script-map
(defn obj->clj
  [obj]
  (if (goog.isObject obj)
    (-> (fn [result key]
          (let [v (goog.object/get obj key)]
            (if (= "function" (goog/typeOf v))
              result
              (assoc result key (obj->clj v)))))
        (reduce {} (.getKeys goog/object obj)))
    obj))

;; << style and property functions >>

(defn px-to-int [x] (js/parseInt (subs x 0 (- (count x) 2))))
(defn setstyle [e x y] (gobj/set (gobj/get e  "style") x y))
(defn getstyle [e x] (gobj/get (gobj/get e  "style") x))
(defn setattrib [e x y] (gobj/set e x y))
(defn get-property [e x] (gobj/get e x))
(defn get-computed-style "get computed style" [x y] (gobj/get (.getComputedStyle js/window x nil) y))


(defn destroymenu [dropzone-id]
  (when (.querySelector js/document (str "#menu-dropzone-" (subs (name dropzone-id) 9)) )
    (rf/dispatch [:dnd/set-menuOpen-state dropzone-id false])
    (rf/dispatch [:dnd/set-pinned-state dropzone-id false])
    (rdom/unmount-component-at-node (.getElementById js/document (str "menu-" (name dropzone-id))))
    (.remove (.getElementById js/document (str "menu-" (name dropzone-id))))))



;; Debugging: 
(def last-output (r/atom ""))
(defn delta-out [x] 
  (when (not= @last-output x)
    (reset! last-output x)
    (println x)))

(def last-output2 (r/atom ""))
(defn delta-out2 [x] 
  (when (not= @last-output2 x)
    (reset! last-output2 x)
    (println x)))
;; << selection functions >>
;; (defn clear-all-selections-except)
;; (defn select_all_elements_at_point)

;; This synchronous version of the function uses dispatch-sync so that rightclicking an element, will immediately
;; update the database, and fetch-all-selected will not fetch out of date information.
;; eg. (clear-all-selections-except :tabselected :historyselected :dropzone-1)
(defn clear-all-selections-except [& exceptions]
"eg. (clear-all-selections-except :tabselected :historyselected :dropzone-1)"
  (let [allZones (concat (keys @(rf/subscribe [:dnd/dropzone-options])) [:tabselected :historyselected])
        filteredZones (remove (set exceptions) allZones)] 
    (doseq [x filteredZones]
      (if (or (= x :tabselected) (= x :historyselected)) (rf/dispatch-sync [:dnd/reset-selected [] :tab-history x])
          (rf/dispatch-sync [:dnd/reset-selected [] x])))))

;; You cannot call dispatch-sync inside of an event dispatch, so this is is the asynchronous version used in
;; select_all_elements_at_point below:
;; event.cljs L30 (.addEventListener js/document "keydown" #(re-frame/dispatch [:dnd/set-keystate % true])) and (.addEventListener js/document "keyup" #(re-frame/dispatch [:dnd/set-keystate % false])) calls =>  events.cljs L245 :dnd/set-keystate calls => utilities.cljs L124 select_all_elements_at_point => (clear-all-selections-except-async)
;; eg. (clear-all-selections-except-async :tabselected :historyselected :dropzone-1)
(defn clear-all-selections-except-async [& exceptions]
  (let [allZones (concat (keys @(rf/subscribe [:dnd/dropzone-options])) [:tabselected :historyselected])
        filteredZones (remove (set exceptions) allZones)] 
    (doseq [x filteredZones]
      (if (or (= x :tabselected) (= x :historyselected)) (rf/dispatch [:dnd/reset-selected [] :tab-history x])
          (rf/dispatch [:dnd/reset-selected [] x])))))


(defn select_all_elements_at_point []
  ;; used async version because you can't call dispatch-sync from within :dnd/set-keystate event
  (clear-all-selections-except-async) 
  (let [mousePosition @(rf/subscribe [:dnd/mouse-position])
        elementsAtPointArray (js->clj (.elementsFromPoint js/document (:x mousePosition) (:y mousePosition)))
        matchResults (filter #(.matches % "div[id^='menu-dropzone'],div[id='menu-tab-history']") elementsAtPointArray)
        menuDropzoneIds (map #(get-property % "id") matchResults)
        ;; (first '()) is nil, subs ... 5, strips of menu- from menu-tab-history or menu-dropzone-2068
        matchingDropzone  (keyword (if-not (nil? (first menuDropzoneIds)) (subs (first menuDropzoneIds) 5) "nothingfound"))]
    (cond (= matchingDropzone :nothingfound) (prn matchingDropzone)
          (= matchingDropzone :tab-history)
          (if (= :tabselected @(rf/subscribe [:dnd/get-tabOrHistorySelected]))
            (rf/dispatch [:dnd/reset-selected (vec (map :id @(rf/subscribe [:dnd/get-tabs]))) :tab-history :tabselected])
            (rf/dispatch [:dnd/reset-selected (vec (map :id @(rf/subscribe [:dnd/get-history]))) :tab-history :historyselected]))
          :else
          (let [dropzoneElementsVec (vec (map :id @(rf/subscribe [:dnd/dropped-elements matchingDropzone])))]
            (rf/dispatch [:dnd/reset-selected dropzoneElementsVec matchingDropzone])))))


          
;; << subfolder search and zipper functions >>

(defn zip-walk [f z]
  (if (zip/end? z)
    (zip/root z) ;; return root at the end
    (recur f (zip/next (f z)))))


;; map-vec-zipper usage:
;; (def mysubtree
;;   {:children [{:dateAdded 1634252090524, :id "6457", :index 0, :parentId "6450", :title "google222", :url "http://www.google222.com/"}
;;     {:dateAdded 1634252090524, :id "6456", :index 1, :parentId "6450", :title "Toronto: 10:59", :url "https://time.is/Toronto"}
;;     {:dateAdded 1634252090523, :id "6455", :index 2, :parentId "6450", :title "DuckDuckGo - Privacy, simplified.", :url "https://duckduckgo.com/"}
;;     {:dateAdded 1634252090523, :id "6454", :index 3, :parentId "6450", :title "asdfasdf", :url "https://duckduckgo.com/"}],
;;    :dateAdded 1634252090196, :dateGroupModified 1636279393920, :id "6450", :index 1, :parentId "1", :title "newtitle222"})
;; (zip/node (map-vec-zipper mysubtree)) ;; starts at "newtitle222" folder
;; (zip/node (-> (map-vec-zipper mysubtree) (zip/next) (zip/next) (zip/next)  ))
;; => {:dateAdded 1634252090524, :id "6457", :index 0, :parentId "6450", :title "google222", :url "http://www.google222.com/"}
(defn map-vec-zipper [m]
  (zip/zipper
   (fn [x] (or (map? x) (sequential? x))) ;; branch? can be map or vector
   ;; note: to switch from clojure to cljs you must swap clojure.lang... with cljs.core/... for types
   (fn [x] (if (isa? (type x) cljs.core/PersistentVector) (seq x) ;; if branch is a vector, convert to seq and return it
               (seq (:children x)))) ;; if branch is a map get children, convert to seq and return it
   ;; make node -- untested, maybe used in zip/insert-child,left,right, zip/replace?
   ;; should do type checking on children : only strings and ints for maps, and only maps for vectors
   ;; zip/root calls zip/up which calls make-node which causes problems
   (fn [node children]
     (if (isa? (type node) cljs.core/PersistentArrayMap)
       (assoc node :children children) ;; the node is a map
       (into [] children))) ;;otherwise its a vector and it is simply comprised of all of its new children   
   m))


(defn subfolder? []
  (let [accum (r/atom [])]
    (fn
      ([loc] (cond (vector? (zip/node loc)) loc
                   (map? (zip/node loc)) (if (:children (zip/node loc))
                                           (do (reset! accum (conj @accum (:id (zip/node loc)))) loc)
                                           loc)
                   :else (do (if (= :folder-has-no-children (zip/node loc))
                               (println "From subfolder?: Folder drop-zone is empty with only :folder-has-no-children, continuing...")
                               (println "From subfolder?: not a branch"))
                             loc))) 
      ([] @accum))))
      

(defn zip-walk-closure [f z]
  (if (zip/end? z)
    ;; calls f with no args so that @accum is returned
    (f) 
    (recur f (zip/next (f z)))))


;; Example Usage: Folder working-hk,595
;; if folder opened and dropzone exists => ["596" "606" "610" "639" "644" "648"]
;; otherwise if folder was never opened once => []
(defn get-all-subfolders [parentFolderId]
  (zip-walk-closure (subfolder?) (map-vec-zipper (vec @(rf/subscribe [:dnd/dropped-elements (fid->dkey parentFolderId)])))))

(defn get-all-subfolders-from-map [map]
  (zip-walk-closure (subfolder?) (map-vec-zipper (vec map))))

(declare run-fade-alert)



;; testing:
;; (rf/dispatch [:dnd/isDestinationSubfolderOfSource-new [:dropzone-1 ["2202" "2021" "6"]] [:dropzone-2202 0]]) ;; fade-alert error but moves 2021, 6
;; (rf/dispatch [:dnd/isDestinationSubfolderOfSource-new [:dropzone-1 ["2021" "6"]] [:dropzone-2202 0]]) ;; moves 2021, 6 only
(rf/reg-event-db  
 :dnd/isDestinationSubfolderOfSource-new
 (fn [db [_ [sourceDropZoneId source-element-id-array] [destinationDropZoneId droppedPosition]]]
   (let [sourceFolders (filterv #(= :folderbox (:type (get-dz-element @(rf/subscribe [:dnd/db]) sourceDropZoneId %))) source-element-id-array)
         destinationFolderIdString (dkey->fid destinationDropZoneId)
         movableFolders (filterv (fn [source-element-id] (not (nat-int? (.indexOf
                                                                         (conj (get-all-subfolders source-element-id) source-element-id)
                                                                         destinationFolderIdString)))) sourceFolders)]
     (if (= (set movableFolders) (set sourceFolders)) 
       (do ;; if no recursive drop then dispatch the move
         (rf/dispatch [:my-drop-dispatch-offline-new [sourceDropZoneId source-element-id-array] [destinationDropZoneId droppedPosition]]))
       ;; else a recursive drop is occurring so show the error
       (let [sourceLinks (vec (remove (set sourceFolders) source-element-id-array)) 
             movableItems (distinct (concat movableFolders sourceLinks))]
         ;; note if movableItems (2nd arg) is [] :my-drop-dispatch-offline-new will skip (doseq... chrome-move-bookmark...) and only synchronize dropzones
         (rf/dispatch [:my-drop-dispatch-offline-new [sourceDropZoneId movableItems] [destinationDropZoneId droppedPosition]])
         (println ["movableItems: " movableItems "source-element-id-array: " source-element-id-array ])
         (run-fade-alert "Cannot move a folder into itself."))))
   db))

;; << create bookmark functions >>

;; //==========================================================testing chrome-create-bookmark
(defn chrome-create-bookmark [ & {:keys [parentId index title url] :or {parentId "2"}}]
  ;;(println {:parentId parentId :index index :title title :url url})
  ;; without title --  no url creates a folder, with url it becomes a link 

  ;; at least an empty object is required for invocation and creates an empty folder in the other bookmarks folder placed 
  ;; in the last location

  ;; if only parentId (string) is specified, then last index of that folder will be inserted after

  ;; string	(optional) parentId Defaults to the Other Bookmarks folder.
  ;; integer	(optional) index The 0-based position of this node within its parent folder.
  ;; string	(optional) title Bookmark title
  ;; string	(optional) url Bookmark url : no url means folder
  (cond (not (or (string? parentId) (nil? parentId))) 
        (throw {:type :custom-arg-error :message "chrome-create-bookmark: parentId is not (nil or a string)"})
        (not (or (int? index) (nil? index)))          
        (throw {:type :custom-arg-error :message "chrome-create-bookmark: index is not (nil or an int)"})
        (not (or (string? title) (nil? title)))       
        (throw {:type :custom-arg-error :message "chrome-create-bookmark: title is not (nil or a string)"})
        (not (or (string? url) (nil? url)))           
        (throw {:type :custom-arg-error :message "chrome-create-bookmark: url is not (nil or a string)"})

        ;; Only way to catch if bookmarkid doesn't exist is js/chrome.runtime.lastError, which chrome checks if
        ;;  you checked the variable in an undocumented way. 
        ;; Every other way I tried to check for js/chrome.runtime.lastError or throwing my own error fails:
        ;; ref: https://stackoverflow.com/questions/28431505/unchecked-runtime-lasterror-when-using-chrome-api
        (not (nil? parentId))
        (.. js/chrome -bookmarks (create (clj->js {"parentId" parentId "index" index "title" title "url" url}) 

                                         (fn [x] (when js/chrome.runtime.lastError 
                                                   (js/console.log (str "chrome-create-bookmark: missing parentId: " parentId 
                                                                        " js/chrome.runtime.lastError.message: "  
                                                                        js/chrome.runtime.lastError.message)  )
                                                   (when (= js/chrome.runtime.lastError.message "Invalid URL.")
                                                     (run-fade-alert "New Page failed, invalid url."))
                                                   ))))))



;; (try (chrome-create-bookmark) ;; => Should create an empty folder at the end of "Other Bookmarks" 
;;      (catch :default e (println "Error Occured: " e)))

;; ;; called with no arguments the arguments default to nil

;; (try (chrome-create-bookmark :parentId "2068") ;; => Should create an empty folder at the end of  "newtitle222" folder 
;;      (catch :default e (println "Error Occured: " e)))

;; (try (chrome-create-bookmark :parentId "9999") ;; => 1. Should throw an error that parentId does not exist
;; Error Occured: chrome-create-bookmark: missing parentId: 9999 js/chrome.runtime.lastError.message: Can't find parent bookmark for id.
;;      (catch :default e (println "Error Occured: " e)))

;; (try (chrome-create-bookmark :parentId 2)  ;; => 2. Should throw an error that parentId is not a string
;; Error Occured:  {:type :custom-arg-error, :message chrome-create-bookmark: parentId is not nil and not a string}
;;      (catch :default e (println "Error Occured: " e)))

;; (try (chrome-create-bookmark :index 2) ;; => Should create an empty folder in 3rd position of "Other Bookmarks"
;;      (catch :default e (println "Error Occured: " e)))

;; (try (chrome-create-bookmark :index "2") ;; => 3. Should throw an error that index is not an int
;; Error Occured:  {:type :custom-arg-error, :message chrome-create-bookmark: index is not nil and not an int}
;;       (catch :default e (println "Error Occured: " e)))

;; (try (chrome-create-bookmark :title "newtitle")  ;; => Should create an empty folder at end of "Other Bookmarks" with title "newtitle"
;;      (catch :default e (println "Error Occured: " e)))

;; (try (chrome-create-bookmark :title 'newtitle) ;; => 4. Should throw an error that title is not a string
;; Error Occured:  {:type :custom-arg-error, :message chrome-create-bookmark: title is not nil and not a string}
;;       (catch :default e (println "Error Occured: " e)))

;; (try (chrome-create-bookmark :url "https://www.newurl.com") ;; => Should create link at the end of "Other Bookmarks" to www.newurl.com
;;      (catch :default e (println "Error Occured: " e)))

;; (try (chrome-create-bookmark :url 'newurl) ;; => Should throw an error that url is not a string
;;      (catch :default e (println "Error Occured: " e)))

;; Summary: 
;; Success! newtitle222 has an empty folder at the end
;; Success! Other Bookmarks at the end has: empty folder, "newtitle" folder, and www.newurl.com link, as well as empty folder in 3rd position
;; Success! 4 errors should be thrown

;; \\==========================================================end chrome-create-bookmark



(defn find-id [zipper bookmarkId] {:pre [(string? bookmarkId)]}
  (let [find-id-inner (fn [loc]
                        (cond (= (:id (zip/node loc)) bookmarkId) loc
                              (zip/end? loc) (throw {:type :custom-arg-error 
                                                     :message (str "find-id: id was not found bookmarkId: " bookmarkId
                                                                   "dropzone options dump: " @(rf/subscribe [:dnd/dropzone-options])
                                                                   " type of bookmarkId: " (type bookmarkId))})
                              :else (recur (zip/next loc))))]
    (find-id-inner zipper)))

#_(try (find-id (map-vec-zipper bkmrks) "9999") ;; => Error Occured:  {:type :custom-arg-error, :message find-id: id was not found}
       (catch :default e (println "Error Occured: " e)))

#_(try (find-id (map-vec-zipper bkmrks) "2068")
       (catch :default e (println "Error Occured: " e)))


(defn insert-child-and-reindex-array [childArray id parentId index title url dateAdded dateGroupModified hasKids]
  (let [indexVerified (cond (nil? index) (count childArray) 
                            ;; clojure throw: (throw (Exception. "index is out of bounds"))
                            (or (< (count childArray) index) (> 0 index)) 
                            (throw {:type :custom-arg-error :message "insert-child-and-reindex-array: index is out of bounds"})
                            :else index)
        newId (if (not (nil? id)) id (+ 10000 (rand-int 90000)))  ;; set the id to a number between 10000 and 99999 
        newElement (if (not (nil? url)) { :dateAdded dateAdded :id newId :index indexVerified :parentId parentId :title title :url url}
                       { :children hasKids :dateAdded dateAdded :dateGroupModified dateGroupModified :id newId 
                        :index indexVerified :parentId parentId :title title})
        [before after] (split-at indexVerified childArray)

        newChildArray (vec (concat before [newElement] (map #(update % :index inc) after) ))]

    newChildArray))

#_(try
    (insert-child-and-reindex-array [{:dateAdded 1598108365507, :id "2073", :index 0, :parentId "2072", :title "Currency World", :url "https://currency.world/?utm_source=time.is&utm_medium=web&utm_campaign=adb"}
                                     {:dateAdded 1598108400700, :id "2074", :index 1, :parentId "2072", :title "Toronto Transit Commission - TTC", :url "http://ttc.ca/index.jsp"}
                                     {:dateAdded 1598108468646, :id "2075", :index 2, :parentId "2072", :title "IGA | Supermarket in Quebec: Recipes, Online Grocery, Flyer", :url "https://www.iga.net/"}]
                                    10101010 "2072" 0 "NEWBOOKMARKTITLE" "NEWBOOKMARKURL")
    (catch :default e (println "Error Occured: " e)))

#_(try
    (insert-child-and-reindex-array [{:dateAdded 1598108365507, :id "2073", :index 0, :parentId "2072", :title "Currency World", :url "https://currency.world/?utm_source=time.is&utm_medium=web&utm_campaign=adb"}
                                     {:dateAdded 1598108400700, :id "2074", :index 1, :parentId "2072", :title "Toronto Transit Commission - TTC", :url "http://ttc.ca/index.jsp"}
                                     {:dateAdded 1598108468646, :id "2075", :index 2, :parentId "2072", :title "IGA | Supermarket in Quebec: Recipes, Online Grocery, Flyer", :url "https://www.iga.net/"}]
                                    10101010 "2072" 3 "NEWBOOKMARKFOLDERTITLE" nil)
    (catch :default e (println "Error Occured: " e)))

#_(try ;; => Error Occured:  {:type :custom-arg-error, :message insert-child-and-reindex-array: index is out of bounds}
    (insert-child-and-reindex-array [{:dateAdded 1598108365507, :id "2073", :index 0, :parentId "2072", :title "Currency World", :url "https://currency.world/?utm_source=time.is&utm_medium=web&utm_campaign=adb"}
                                     {:dateAdded 1598108400700, :id "2074", :index 1, :parentId "2072", :title "Toronto Transit Commission - TTC", :url "http://ttc.ca/index.jsp"}
                                     {:dateAdded 1598108468646, :id "2075", :index 2, :parentId "2072", :title "IGA | Supermarket in Quebec: Recipes, Online Grocery, Flyer", :url "https://www.iga.net/"}]
                                    10101010 "2072" 4 "NEWBOOKMARKFOLDERTITLE" nil)
    (catch :default e (println "Error Occured: " e))) 




;; //==========================================================testing stub-create-bookmark

(defn stub-create-bookmark [ bookmarkstub & {:keys [id parentId index title url dateAdded dateGroupModified hasKids] 
                                             :or {parentId "2"}}]
  (let 
      ;; If parentId is not found throw an error
      ;; find the parent folder node (if not supplied it is 2 by default)
      [myzipper (map-vec-zipper bookmarkstub) 
       foundParentZipperLoc (find-id myzipper parentId) ;;(try (find-id myzipper parentId) (catch :default e (println "Error Occured: " e)))
       foundKids (if (nil? hasKids) [] hasKids)
       ;; assume parent node found and edit the parent node by associng with new children
       ;; find the size of the children array:
       ;; if index out of range throw error, if index not supplied set it to end of collection
       ;; finally re-index the array
       currentTimeEpoch (js/Date.now)
       newDateAdded (if (nil? dateAdded) currentTimeEpoch dateAdded)
       newDateGroupModified (if (nil? dateGroupModified) currentTimeEpoch dateGroupModified)
       
       oldChildArray (if-let [childVal (:children (zip/node foundParentZipperLoc))] childVal
                             (throw {:type :custom-arg-error :message 
                                     (str "stub-create-bookmark: target parentId: " parentId " has nil children, is not a folder")})) 
       
       ;; insert-child-and-reindex-array adds :dateGroupModified if url is nil ie. a folder, otherwise it is omitted
       ;; :dateAdded and :dateGroupModified are used as given, and above, are set to current epoch time if nil
       newChildArray (insert-child-and-reindex-array oldChildArray id parentId index title url 
                                                     newDateAdded newDateGroupModified foundKids)

       newBookmarkStub (zip/root (zip/edit foundParentZipperLoc assoc :children newChildArray
					   ;; don't edit :dateAdded this is when the parent folder was created
					   :dateGroupModified currentTimeEpoch))] ;; edit this to show children have been updated
    newBookmarkStub)) 

;; --testing id
;; no id: => random id 53853 => {:dateAdded 1600622830115, :id 53853, :index 0, :parentId 2068, :title NEWBOOKMARK, :url NEWBOOKMARKURL.COM}
#_(try ;; :dateAdded 1600262806539 ~approx~ Date.now() 1600262853866
    (println (stub-create-bookmark bkmrks :parentId "2068" :index 0 :title "NEWBOOKMARK" :url "NEWBOOKMARKURL.COM"))     
    (catch :default e (println "Error Occured: " e)))
;; with id: "123456789" => success id 123456789 => {:dateAdded 1600622830131, :id 123456789, :index 0, :parentId 2068, :title NEWBOOKMARK, :url NEWBOOKMARKURL.COM}
#_(try ;; :dateAdded 1600262806539 ~approx~ Date.now() 1600262853866
    (println (stub-create-bookmark bkmrks :id "123456789" :parentId "2069" :index 0 :title "NEWBOOKMARK" :url "NEWBOOKMARKURL.COM"))     
    (catch :default e (println "Error Occured: " e)))
;; --end testing id 


;; --testing date
;; automatic date for link:
#_(try ;; success: :dateAdded 1600262806539 ~approx~ Date.now() 1600262853866
    (println (stub-create-bookmark bkmrks :parentId "2068" :index 0 :title "NEWBOOKMARK" :url "NEWBOOKMARKURL.COM"))     
    (println (str "(js/Date.now): " (js/Date.now)))
    (catch :default e (println "Error Occured: " e)))

;; automatic date for folder
#_(try ;; success: :dateAdded 1600263537889, :dateGroupModified 1600263537889 ~approx~ Date.now() 1600263547738
    (println (stub-create-bookmark bkmrks :parentId "2068" :index 6 :title "NEWBOOKMARKFOLDER"))
    (println (str "(js/Date.now): " (js/Date.now)))
    (catch :default e (println "Error Occured: " e)))

;; given date for link:
#_(try ;; success: :dateAdded 9999999999999
    (println (stub-create-bookmark bkmrks :parentId "2068" :index 0 :title "NEWBOOKMARK" :url "NEWBOOKMARKURL.COM" :dateAdded 9999999999999))     
    (catch :default e (println "Error Occured: " e)))

;; given date for folder:
#_(try ;; success :dateAdded 99999999999999, :dateGroupModified 8888888888888
    (println (stub-create-bookmark bkmrks :parentId "2068" :index 6 :title "NEWBOOKMARKFOLDER" :dateAdded 9999999999999 :dateGroupModified 7777777777777))
    (catch :default e (println "Error Occured: " e)))
;; --end testing date

;; --testing index out of bounds
#_(try ;; => {:type :custom-arg-error, :message insert-child-and-reindex-array: index is out of bounds}
    (stub-create-bookmark bkmrks :parentId "2068" :index 7 :title "NEWBOOKMARK" :url "NEWBOOKMARKURL.COM")
    (println (str "(js/Date.now): " (js/Date.now)))
    (catch :default e (println "Error Occured: " e)))
;; --end testing index out of bounds 
;; \\==========================================================end stub-create-bookmark


;; << move bookmark functions >>

;; //==========================================================testing stub-removeTree-bookmark
(defn stub-removeTree-bookmark [bookmarkstub bookmarkid] 
  (let [myZipper (map-vec-zipper bookmarkstub)
        foundZipperLoc (find-id myZipper bookmarkid) ;; find-id checks that the bookmarkid exists and asserts it is a string
        saveParentId (:parentId (zip/node foundZipperLoc))
        nodeDeleted (zip/root (zip/remove foundZipperLoc)) ;; zip/root returns a tree not a zipper
        ;; find-id takes a zipper so map-vec-zipper must be called again
        parentLoc (find-id (map-vec-zipper nodeDeleted) saveParentId)
        oldChildArray (:children (zip/node parentLoc))
        newChildArray (map-indexed (fn [vecIndex mapItem] (assoc mapItem :index vecIndex)) oldChildArray)

        currentTimeEpoch (js/Date.now)
        newBookmarkStub (zip/root (zip/edit parentLoc assoc :children newChildArray
                                            ;; don't edit :dateAdded this is when the parent folder was created
                                            :dateGroupModified currentTimeEpoch)) ;; edit this to show children have been updated
        ]

    newBookmarkStub))

#_(try 
    (stub-removeTree-bookmark bkmrks 2075) ;; from find-id => Error Occured:  #object[Error Error: Assert failed: (string? bookmarkId)]
    (catch :default e (println "Error Occured: " e)))

;; tested in figwheel & works:
#_(try 
    (println (str "before: " bkmrks))
    (println (str "after: remove 2070 duckduckgo: :index 1 " 
                  (stub-removeTree-bookmark bkmrks "2070"))) ;; success => should return bkmrks without {... :id "2075" ... :parentId "2072"  ...  "https://www.iga.net/"}
    (println (str "after: remove 2072 full folder: :index 4 " 
                  (stub-removeTree-bookmark bkmrks "2072"))) ;; success => should return bkmrks without {:children [...] ... :id "2072" ... :title "full folder"} 
    (catch :default e (println "Error Occured: " e)))


;; \\==========================================================end stub-removeTree-bookmark






;; //==========================================================testing stub-move-bookmark

(defn stub-move-bookmark [bookmarkstub sourceId & {:keys [parentId index]}]

  (let [myzipper (map-vec-zipper bookmarkstub) 
        deleteSourceBookmarkStub (stub-removeTree-bookmark bookmarkstub sourceId)
        foundParentZipperLoc (find-id myzipper sourceId)  ;; find-id will throw an error if sourceId out of range
        foundParentZipperNode (zip/node foundParentZipperLoc)] ;; find-id will throw an error if sourceId out of range


    (cond  ;; sourceId cannot be nil but parentId or index can be nil
      (not (string? sourceId))
      (throw {:type :custom-arg-error :message "chrome-move-bookmark: sourceId is not a string"}) 
      (not (or (string? parentId) (nil? parentId))) 
      (throw {:type :custom-arg-error :message "chrome-move-bookmark: parentId is not (nil or a string)"})
      (not (or (int? index) (nil? index))) 
      (throw {:type :custom-arg-error :message "chrome-move-bookmark: index is not (nil or an int)"})


      :else
      (do
        (stub-create-bookmark deleteSourceBookmarkStub
                              :id (:id foundParentZipperNode)
                              :parentId parentId
                              :index index
                              :title (:title foundParentZipperNode)
                              :url (:url foundParentZipperNode)
                              :dateAdded (:dateAdded foundParentZipperNode)
                              :dateGroupModified (:dateGroupModified foundParentZipperNode)
                              :hasKids (:children foundParentZipperNode))))))

  ;; nil parentId, nil index  ; do nothing
  ;; nil parentId, yes index  ; move to same folder at new index location
  ;; yes parentId, nil  index ; move to new  folder at     index location 0
  ;; yes parentId, yes index  ; move to new  folder at new index location


 #_(try
  (stub-move-bookmark bkmrks "2069" :parentId "2072" :index 2) ;; success: move google to full folder at index 2
  ;;(stub-move-bookmark bkmrks "2072" :parentId "2076" :index 0) ;; success: move full folder to empty folder
  ;;(stub-move-bookmark bkmrks "2076" :parentId "2072" :index 3) ;; success: move empty folder to full folder at index 3 (the end)
  ;; Error move empty folder to full folder at index 4 (past the end) ;; Error Occured:  {:type :custom-arg-error, :message insert-child-and-reindex-array: index is out of bounds}
  ;;(stub-move-bookmark bkmrks "2076" :parentId "2072" :index 4) 
  (catch :default e (println "Error Occured: " e))) 

;; \\==========================================================end stub-move-bookmark


(rf/reg-event-fx
 :my-drop-dispatch-offline-new
 ;;destructuring: (defn myfun [{db :db}] (print "the db is: " db)) and (myfun {:a 1 :b 2 :db 3}) => "the db is:  3"
 (fn [{db :db}
      [_ [source-drop-zone-id source-element-id-array] ;; string: source-element-id is the folderId string
       [targetParentDropzoneId dropped-position]]] ;;position = index in the list of dropped elements
   #_(println ":my-drop-dispatch-offline-new is running")
   {:db       db
    :dispatch-n
    (let [targetParentId (dkey->fid targetParentDropzoneId)
          bkmrks (rf/subscribe [:dnd/bookmark-atom])]
      (loop [chopArray source-element-id-array newdb @bkmrks]
        (if (empty? chopArray) ;; seq used instead of (not (empty? x))
          (list [:dnd/initialize-bookmark-atom newdb] [:dnd/synch-all-dropzones-to-folderId])
          (let [source-element-id (first chopArray)
                ;; _ (prn ["source-element-id: " source-element-id])
                sourceElement (get-dz-element db source-drop-zone-id source-element-id)
                targetOfflineIndex (if (= source-drop-zone-id targetParentDropzoneId)
                                     (if (>= (:index sourceElement) dropped-position ) dropped-position (max 0 (dec dropped-position)))
                                     dropped-position)]
            ;; (prn ["chopArray: " chopArray]) 
            (recur (rest chopArray)
                   (stub-move-bookmark newdb source-element-id :parentId targetParentId :index targetOfflineIndex))))))}))






(defn onevent-dispatch-refresh-fnhandle []
  (rf/dispatch [:chrome-synch-all-dropzones-to-folderId {:type :updateAll}]))



;; //==========================================================testing chrome-move-bookmark

(defn chrome-move-bookmark [sourceId updateDzs & {:keys [parentId index last]}]
  #_(println "chrome-move-bookmark [sourceId & {:keys [parentId index]}]: [sourceId parentId index]: " [sourceId parentId index])

  (cond  ;; sourceId cannot be nil but parentId or index can be nil
    (not (string? sourceId))
    (throw {:type :custom-arg-error :message "chrome-move-bookmark: sourceId is not a string"})
    (not (or (string? parentId) (nil? parentId))) 
    (throw {:type :custom-arg-error :message "chrome-move-bookmark: parentId is not (nil or a string)"})
    (not (or (int? index) (nil? index))) 
    (throw {:type :custom-arg-error :message "chrome-move-bookmark: index is not (nil and or an int)"})

    :else
    (.. js/chrome -bookmarks (move sourceId (clj->js {"parentId" parentId "index" index}) 

                                   (fn [x] (when last
                                             (rf/dispatch [:chrome-synch-all-dropzones-to-folderId updateDzs])
                                             (.addListener js/chrome.bookmarks.onMoved onevent-dispatch-refresh-fnhandle))
                                     (if js/chrome.runtime.lastError 
                                       (js/console.log 
                                        (str "chrome-move-bookmark: sourceId, parentId or index out of bounds: [sourceId parentId index]: " 
                                             [sourceId parentId index]
                                             " js/chrome.runtime.lastError.message: "  
                                             js/chrome.runtime.lastError.message)  )))))))

#_(try ;; throws => {:type :custom-arg-error :message "chrome-move-bookmark: sourceId is not a string"}
    (chrome-move-bookmark 2204)
    (catch :default e (println "Error Occured: " e)))

#_(try ;; throws => {:type :custom-arg-error :message "chrome-move-bookmark: parentId is not (nil or a string)"}
    (chrome-move-bookmark "2204" :parentId 2202)
    (catch :default e (println "Error Occured: " e)))

#_(try ;; throws => {:type :custom-arg-error :message "chrome-move-bookmark: index is not (nil and or an int)"}
    (chrome-move-bookmark "2204" :parentId "2202" :index "0")
    (catch :default e (println "Error Occured: " e)))

#_(try ;; throws => chrome-move-bookmark: sourceId, parentId or index out of bounds: [sourceId parentId index]: ["99999" "2202" 0] js/chrome.runtime.lastError.message: Can't find bookmark for id.
    (chrome-move-bookmark "99999" :parentId "2202" :index 0)
    (catch :default e (println "Error Occured: " e)))

#_(try ;; throws => chrome-move-bookmark: parentId or index out of bounds: [parentId index]: ["99999" 0] js/chrome.runtime.lastError.message: Can't find parent bookmark for id.
    (chrome-move-bookmark "2204" :parentId "99999" :index 0)
    (catch :default e (println "Error Occured: " e)))

#_(try ;; chrome-move-bookmark: parentId or index out of bounds: [parentId index]: ["2202" 99999] js/chrome.runtime.lastError.message: Index out of bounds.
    (chrome-move-bookmark "2204" :parentId "2202" :index 99999)
    (catch :default e (println "Error Occured: " e)))

#_(try ;; do nothing
    (chrome-move-bookmark "2204")
    (catch :default e (println "Error Occured: " e)))

#_(try ;; move to same folder at new index location
    (chrome-move-bookmark "2204" :index 1)
    (catch :default e (println "Error Occured: " e)))

#_(try ;; move to new  folder at     index location 0
    (chrome-move-bookmark "2204" :parentId "2202")
    (chrome-move-bookmark "2204" :parentId "2068") ;; move it back to newtitle222 folder
    (catch :default e (println "Error Occured: " e)))

#_(try ;; success: moves id: 2204, "asdfasdf" from newtitle222 folder to beginning of id: 2202 "dest folder"
    (chrome-move-bookmark "2204" :parentId "2202" :index 0)
    (catch :default e (println "Error Occured: " e)))

;; \\==========================================================end chrome-move-bookmark



;; //==========================================================start chrome-getSubTree-bookmark
;; debugging:
;; chrome.bookmarks.search("newtitle222", function(treeArray) {console.table(treeArray)})

(defn chrome-getSubTree-bookmark [ bookmarkid & [callbackFunction]]
  ;; (println "args to chrome-getSubTree-bookmark are: " bookmarkid callbackFunction)
  (if-not callbackFunction
    ;; check if arg is a not a string and if so throw an error and exit
    (if (not (string? bookmarkid)) (throw {:type :custom-arg-error :message "chrome-getSubTree-bookmark: bookmarkid is not a string"})
        (.. js/chrome -bookmarks (getSubTree bookmarkid (fn [x] (if js/chrome.runtime.lastError 
                                                                  (js/console.log 
                                                                   (str "chrome-getSubTree-bookmark: missing bookmarkId: " 
                                                                        bookmarkid 
                                                                        " js/chrome.runtime.lastError.message: " 
                                                                        js/chrome.runtime.lastError.message))
                                                                  (prn (js->clj x :keywordize-keys true)))))))
    (.. js/chrome -bookmarks (getSubTree bookmarkid callbackFunction))))

;; (println "(.hasOwnProperty js/chrome \"bookmarks\"): " (.hasOwnProperty js/chrome "bookmarks"))

;; https://stackoverflow.com/questions/28431505/unchecked-runtime-lasterror-when-using-chrome-api 
;; test bookmarkid is not a string => => Error Occured:  {:type :custom-arg-error, :message chrome-getSubTree-bookmark: bookmarkid is not a string}
#_(try (chrome-getSubTree-bookmark 2068) 
       (catch :default e (println "Error Occured: " e)))

;; test missing bookmarkid => chrome-getSubTree-bookmark: missing bookmarkId: 99999 js/chrome.runtime.lastError.message: Can't find bookmark for id.
#_(try (chrome-getSubTree-bookmark "99999") 
       (catch :default e (println "Error Occured: " e)))

;; test retrieve existing folder newtitle222 => successfully returns newtitle222 folder
#_(try (chrome-getSubTree-bookmark "2068") 
       (catch :default e (println "Error Occured: " e)))

;; test retrieve existing link "DuckDuckGo...." => successfully returns duckduckgo link
#_(try (chrome-getSubTree-bookmark "2070") 
       (catch :default e (println "Error Occured: " e)))

#_(.. js/chrome -bookmarks (getSubTree "2068"
				       #(do (js/console.log "getSubTree returns:")
					    (prn (js->clj % :keywordize-keys true)))))

;; \\==========================================================end chrome-getSubTree-bookmark



(defn recursive-drop-offline-new [[sourceDropZoneId source-element-id-array] [destinationDropZoneId droppedPosition]]
  ;; Initialize the drop-zone options for this folder with folderId if it does not already exist by being open=true or closed=false
  ;; :menuOpen false is checked by folderbutton to mount a [Menu ...] component upon click.
  ;; If the target dropzone's options are not instantiated,
  ;; *-move-bookmark will fail because the target folder id, (which becomes the parent folder whose children array is updated
  ;; by stub-move-bookmark or simply the target folder for chrome-move-bookmark) is undefined.
  (when (nil? @(rf/subscribe [:dnd/menuOpen-state destinationDropZoneId] ))
    (rf/dispatch [:dnd/initialize-drop-zone destinationDropZoneId ;; keyword is the dropzone-id
                  ;; options 
                  {:folderId (dkey->fid destinationDropZoneId)
                   :pinned false
                   :menuOpen false
                   :collapsedStartupToggle false
                   :cutoffDropzoneElements defaultCutoffDropzoneElements
                   :selected []
                   :z-index 1}])
    (try (if (.hasOwnProperty js/chrome "bookmarks") (rf/dispatch [:chrome-synch-all-dropzones-to-folderId {:type :updateAll}])
             (rf/dispatch [:dnd/synch-all-dropzones-to-folderId]))
         ;; tested error => Error Occured:  {:type :custom-arg-error, :message find-id: id was not found}
         (catch :default e (println "Error Occured: " e))))
  
  (let [sourceFolders (filterv #(= :folderbox (:type (get-dz-element @(rf/subscribe [:dnd/db]) sourceDropZoneId %))) source-element-id-array)]
      (if (seq sourceFolders) ;; (not (empty? sourceFolders))
        (do
          ;; Initialize the drop-zone options for the source if it is a folder if it does not already exist (nil? check) by being open=true 
          ;; or closed=false, or is open (:menuOpen false is checked by folderbutton to mount a [Menu ...] component upon click.)
          ;; This ensures compiling all subfolders to check for recursive drops, will work because the dropzone of the source being
          ;; moved will exist before it is searched by get-all-subfolders. A pinned menu for a parent folder when closed will destroy a
          ;; drop zone when closed even if it's subfolders exist as dropzones and are still are open as menus.
          (doseq [source-element-id sourceFolders]
           (when (nil? @(rf/subscribe [:dnd/menuOpen-state (fid->dkey source-element-id)] ))
             #_(println ["running second initialize" "(fid->dkey source-element-id)" (fid->dkey source-element-id)  " @(rf/subscribe [:dnd/menuOpen-state (fid->dkey sourceElementId)] )" @(rf/subscribe [:dnd/menuOpen-state (fid->dkey sourceElementId)] )])
             ;; we know the source must be a folder so fid->dkey string mangling will work
             (rf/dispatch [:dnd/initialize-drop-zone (fid->dkey source-element-id) 
                           ;; options
                           {:folderId source-element-id
                            :pinned false
                            :menuOpen false
                            :collapsedStartupToggle false
                            :cutoffDropzoneElements defaultCutoffDropzoneElements
                            :selected []
                            :z-index 1}])))
          ;; due to the above test (= :folderbox ...)  I can assume here source-element-id is a folder :
          (try (if (.hasOwnProperty js/chrome "bookmarks") (rf/dispatch [:chrome-synch-all-dropzones-to-folderId {:type :updateAll}])
                     (rf/dispatch [:dnd/synch-all-dropzones-to-folderId]))
                 ;; tested error => Error Occured:  {:type :custom-arg-error, :message find-id: id was not found}
               (catch :default e (println "Error Occured: " e)))
          (rf/dispatch [:dnd/isDestinationSubfolderOfSource-new [sourceDropZoneId source-element-id-array] [destinationDropZoneId droppedPosition]]))
        ;; if source-element-id-array has no folders recursive drop is impossible so allow the move to go through
        (rf/dispatch [:my-drop-dispatch-offline-new [sourceDropZoneId source-element-id-array] [destinationDropZoneId droppedPosition]]))))

;; recursive drop error:
;; note that due to last element being processed seperately here, outside of the doseq loop, it is likely that if source-element-id-array
;; is empty because the only element which was a folder was removed due to a recursive drop error, then an error will be thrown with message:
;; Error Occured:  {:type :custom-arg-error, :message chrome-move-bookmark: sourceId is not a string}, because sourceId will be nil
(defn chrome-move-bookmark-selected-wrapper
  [source-element-id-array targetParentDropzoneId dropped-position updateDzs]
  #_(prn ["from chrome-move-bookmark-selected-wrapper: source-element-id-array" source-element-id-array])
  (let [targetParentId (dkey->fid targetParentDropzoneId)]
    (doseq [source-element-id (butlast source-element-id-array)]
      (try
        (chrome-move-bookmark source-element-id updateDzs :parentId targetParentId :index dropped-position :last false)
        (catch :default e (println "Error Occured: " e))))
    (try
      (chrome-move-bookmark (last source-element-id-array) updateDzs :parentId targetParentId :index dropped-position :last true)
      (catch :default e (println "Error Occured: " e)))))

(declare clear-all-selections-except)
(defn recursive-drop-dispatch-multiple [[sourceDropZoneId source-element-id-array] [destinationDropZoneId droppedPosition]]
  (prn ["recursive-drop-dispatch-multiple is running!"
        "[[sourceDropZoneId source-element-id-array] [destinationDropZoneId droppedPosition]]"
        [[sourceDropZoneId source-element-id-array] [destinationDropZoneId droppedPosition]]])
  (clear-all-selections-except)
  (if (not (.hasOwnProperty js/chrome "bookmarks"))
    (recursive-drop-offline-new [sourceDropZoneId source-element-id-array] [destinationDropZoneId droppedPosition])
    (let [destinationFolderIdString (dkey->fid destinationDropZoneId)
          parse-subtree
          (fn parse-subtree [firstSourceElementIdArray restSourceElementIdArray accumSourceFolders accumSourceLinks subtree]
            (let [cljsSubtree (first (js->clj subtree :keywordize-keys true))
                  children (:children cljsSubtree)
                  returnVal (map (fn [x] (if (nil? (:children x)) (assoc x :type :link) (assoc x :type :folderbox  ))) children)
                  subfolders (conj (get-all-subfolders-from-map returnVal) firstSourceElementIdArray)
                  ;; if firstSourceElementIdArray has children (is a folder) and destination folder is not within it's subfolders
                  ;; then add it to accumSourceFolders, otherwise keep accumSourceFolders the same (initialized as [])
                  newAccumSourceFolders (if (and children (not (some #{destinationFolderIdString} subfolders)))
                                          (conj accumSourceFolders firstSourceElementIdArray)
                                          accumSourceFolders)
                  ;; add to link vector if it has no children, and it is not nil, due to id not found in bookmark tree
                  newAccumSourceLinks (if (and (nil? children) (not (nil? cljsSubtree))) 
                                        (conj accumSourceLinks firstSourceElementIdArray) accumSourceLinks)]
              (if (empty? restSourceElementIdArray)
                (let [reconstructArray (distinct (concat newAccumSourceFolders newAccumSourceLinks))]
                  (if (= (set reconstructArray) (set source-element-id-array))
                    (do #_(prn ["reconstructArray: " reconstructArray])
                        (chrome-move-bookmark-selected-wrapper reconstructArray destinationDropZoneId droppedPosition
                                                               {:type :updateAll}))
                    (do #_(prn ["reconstructArray: " reconstructArray])
                        (chrome-move-bookmark-selected-wrapper reconstructArray destinationDropZoneId droppedPosition
                                                               {:type :updateall})
                        (run-fade-alert "Cannot move a folder into itself."))))
                ;;else recurse
                (do #_(prn ["source-element-id-array: " source-element-id-array
                          "firstSourceElementIdArray: " firstSourceElementIdArray "subfolders: " subfolders
                          "destinationFolderIdString: " destinationFolderIdString])
                    (chrome-getSubTree-bookmark (first restSourceElementIdArray)
                                                (partial parse-subtree
                                                         (first restSourceElementIdArray)
                                                         (rest restSourceElementIdArray)
                                                         newAccumSourceFolders
                                                         newAccumSourceLinks))))))]

      (chrome-getSubTree-bookmark (first source-element-id-array)
                                  (partial parse-subtree
                                           (first source-element-id-array)
                                           (rest source-element-id-array)
                                           []
                                           [])))))




;; << update bookmark functions >>
;; //==========================================================testing stub-update-bookmark

;; WRAPPED BY  :dnd/update-bookmark in events.cljs
;; depends on map-vec-zipper, zipwalk and testing requires bkmrk, ie. nodeupdate unnecessary
(defn stub-update-bookmark [bookmarkstub bookmarkid & {:keys [title url]} ] 
  ;; ... <map> bookmarkstub (mandatory) ...
  ;; ... <string> bookmarkid (mandatory) ...
  ;; ... <string> title (optional) ...
  ;; ... <string> url (optional) ...

  (let [myzipper (map-vec-zipper bookmarkstub)
        nodeupdate-all (fn [loc]  (cond (vector? (zip/node loc)) loc ;; both maps and vectors are branches, ignore vectors
                                        (map? (zip/node loc)) (if (= (:id (zip/node loc)) bookmarkid) 
                                                                (zip/edit loc assoc :title title :url url) loc)
                                        :else (do (println "stub-update-bookmark: nodeupdate-all: not a branch") loc)) )
        nodeupdate-title (fn [loc]  (cond (vector? (zip/node loc)) loc ;; both maps and vectors are branches, ignore vectors
                                          (map? (zip/node loc)) (if (= (:id (zip/node loc)) bookmarkid) 
                                                                  (zip/edit loc assoc :title title) loc)
                                          :else (do (println "stub-update-bookmark: nodeupdate-title: not a branch") loc)) )
        nodeupdate-url (fn [loc]  (cond (vector? (zip/node loc)) loc ;; both maps and vectors are branches, ignore vectors
                                        (map? (zip/node loc)) (if (= (:id (zip/node loc)) bookmarkid) 
                                                                (zip/edit loc assoc :url url) loc)
                                        :else (do (println "stub-update-bookmark: nodeupdate-url: not a branch") loc)) )]
    
    (cond (not (string? bookmarkid)) ;; "bookmarkid is not a string or is undefined" 
          (throw {:type :custom-arg-error :message "stub-update-bookmark: bookmarkid is not a string or is undefined"}) ;;temporary: this throw syntax is javascript only
          (every? nil? [title url]) ;; "both title and url are undefined"
          (throw {:type :custom-arg-error :message "stub-update-bookmark: both title and url are undefined"}) ;;temporary: this throw syntax is javascript only
          ;; only title exist
          (nil? url) (zip-walk nodeupdate-title myzipper)

          ;; only url exists
          (nil? title) (zip-walk nodeupdate-url myzipper)

          ;; both title and url exist
          :else (zip-walk nodeupdate-all myzipper))))


 

#_(try 
    (stub-update-bookmark bkmrks 2070 :title "FOUNDDUCKDUCKGO" :url "FOUNDDDG.COM") ;; => "bookmarkid is not a string or is undefined"
    (catch :default e (println "Error Occured: " e)))

#_(try 
    (stub-update-bookmark bkmrks "2070") ;; => "both title and url are undefined"
    (catch :default e (println "Error Occured: " e)))

;; tested in figwheel & works:
#_(try 
    (println (stub-update-bookmark bkmrks "2075" :title "FOUNDIGA" :url "FOUNDIGA.COM")) ;; => success
    (println (stub-update-bookmark bkmrks "2075" :title "FOUNDIGA")) ;; => success
    (println (stub-update-bookmark bkmrks "2075" :url "FOUNDIGA.com")) ;; => success
    (catch :default e (println "Error Occured: " e)))


;; \\==========================================================end stub-update-bookmark

;; //==========================================================start chrome-update-bookmark
(defn chrome-update-bookmark [bookmarkid & {:keys [title url]} ]

  ;; ... <string> bookmarkid (mandatory) ...
  ;; ... <string> title (optional) ...
  ;; ... <string> url (optional) ...

  (cond (not (string? bookmarkid)) (throw {:type :custom-arg-error :message "chrome-update-bookmark: bookmarkid is not a string or is undefined"})
        (every? nil? [title url]) (throw {:type :custom-arg-error :message "chrome-update-bookmark: both title and url are undefined"})
        (nil? url) (.. js/chrome -bookmarks (update bookmarkid (clj->js {"title" title})))
        (nil? title) (.. js/chrome -bookmarks (update bookmarkid (clj->js {"url" url})))
        :else (.. js/chrome -bookmarks (update bookmarkid (clj->js {"title" title "url" url})
                                               (fn [x] (if js/chrome.runtime.lastError 
                                                         (js/console.log (str "chrome-update-bookmark: missing bookmarkId: " bookmarkid 
                                                                              " js/chrome.runtime.lastError.message: " 
                                                                              js/chrome.runtime.lastError.message))))))))


;; => chrome-update-bookmark: missing bookmarkId: 99999 js/chrome.runtime.lastError.message: Can't find parent bookmark for id.
#_(try (chrome-update-bookmark "99999" :title "mynewtitle" :url "http://mynewtitle.com") 
       (catch :default e (println "Error Occured: " e)))

#_(try (chrome-update-bookmark "20") ;; => {:type :custom-arg-error, :message chrome-update-bookmark: both title and url are undefined}
       (catch :default e (println "Error Occured: " e)))

#_(try (chrome-update-bookmark 123) ;; => {:type :custom-arg-error, :message chrome-update-bookmark: bookmarkid is not a string or is undefined}
       (catch :default e (println "Error Occured: " e)))

#_(try (chrome-update-bookmark "2068" :title "newtitle222") ;; => success
       (catch :default e (println "Error Occured: " e)))

#_(try (chrome-update-bookmark "2069" :title "google222" :url "http://www.google222.com") ;; => success
       (catch :default e (println "Error Occured: " e)))


;; \\==========================================================end chrome-update-bookmark


;; << ondrop preprocessing and overlays >>
;; 
;; ===========================================================================================================
;; center-overlay-ondrop: (destination is blank:(:parentId de) or folder-middle:(:id dropzoneElement) 
;;                      or edge:(:parentId dropzoneElement) when called)
;; if it's a searchDropzone, and online   disable onMoved event listeners, and call recursive-drop-dispatch-multiple
;; if it's a searchDropzone, and offline                                       call recursive-drop-dispatch-multiple

;; I.  else
;; if source and dest different, and online  then disable onMoved event listeners, and call process-drop-dispatch
;; if source and dest different, and offline then                                      call recursive-drop-dispatch-multiple

;; II. else
;; if source and dest same,      and online  then disable onMoved event listeners, partially reverse selection order, and call process-drop-dispatch
;; if source and dest different, and offline then                                  partially reverse selection order, and call recursive-drop-dispatch-multiple

;; (I. and II. are the same, except selection order is partially reversed when source and destination are the same)

;; ===========================================================================================================
;; process-drop-dispatch: (fast version of recursive-drop-dispatch-multiple, by skipping dropzone synchs, 
;;                         and only searching the appdb for recursive drop errors. Only works online)
;; If a there is a recursive drop error, remove those erroneous folders, show an error, and move the rest. 
;; Or else, just move all the folders and links with:
;; chrome-move-bookmark-selected-wrapper: which calls chrome-move-bookmark for all source elements except 
;;                                        the last one with :last false, and the last one with :last true
;; chrome-move-bookmark: moves any bookmark. But if signalled with :last true argument, calls 
;;                       :chrome-synch-all-dropzones-to-folderId and adds back the onmoved event listener.

;; The updateDzs map looks like: 
;; {:type :updateSome :updateDzs (vec (distinct [sourceDropzoneId destinationDropZoneId])) }
;; (in process-drop-dispatch) or {:type :updateAll} (in (defn onevent-dispatch-refresh-fnhandle ...) 
;; calling :chrome-synch-all-dropzones-to-folderId).
;; The updateDzs map is an argument for :chrome-synch-all-dropzones-to-folderId, but it is passed through 
;; these functions:
;;        process-drop-dispatch -> chrome-move-bookmark-selected-wrapper 
;;                              -> chrome-move-bookmark -> :chrome-synch-all-dropzones-to-folderId and 
;; indicates to :chrome-synch-all-dropzones-to-folderId which dropzones to update when :type is 
;; :updateSome, or all dropzones when 
;; :type is :updateAll as with when (defn onevent-dispatch-refresh-fnhandle ...) is called by the 
;; chrome.bookmarks.onMoved event

;; ===========================================================================================================
;; recursive-drop-dispatch-multiple: (slow version of process-drop-dispatch, but also handles the 
;;                                    offline case. 
;;                                    recursive-drop-dispatch-multiple is only called in 
;;                                    center-overlay-ondrop where it is called 4 times)
;; If offline call (recursive-drop-offline-new [sourceDropZoneId source-element-id-array] 
;;                  [destinationDropZoneId droppedPosition])
;; If online, for each source-element-id-array, recursively call chrome-getSubTree-bookmark to check for 
;; recursive drop errors. Then finally call chrome-move-bookmark-selected-wrapper with argument 
;; {:type :updateAll} to update all dropzones, instead of just some.

;; recursive-drop-dispatch-multiple is no better than process-drop-dispatch, except that it does not 
;; depend on the appdb to check for recursive drop errors, by fetching the real subtree for each 
;; bookmark (which is slow). It also updates all dropzones instead of some which is slow. Speed up 
;; from recursive-drop-dispatch-multiple to process-drop-dispatch was approximately 4s to <1s. 
;; However most of the slowdown was caused by multiple calls to synch dropzones, rather than the 
;; recursive getSubtree calls.


;;sourceDropzoneId (fid->dkey (:parentId widget)), destinationDropZoneId (fid->dkey destination)
(defn process-drop-dispatch [[ sourceDropzoneId source-element-id-array] [destinationDropZoneId dropIndex]]

  (let [selectedSetPred (set source-element-id-array)
        ;; converts id to elements (maps)
        selectedElementList (filterv #(selectedSetPred (:id %)) @(rf/subscribe [:dnd/dropped-elements sourceDropzoneId]))
        ;; filters out elements and links
        sourceFolderElementList (filterv #(= :folderbox (:type %)) selectedElementList)
        sourceLinkIdList (vec (sort-by #(.indexOf source-element-id-array %) (mapv :id (filterv #(= :link (:type %)) selectedElementList))))
        destinationFolderIdString (dkey->fid destinationDropZoneId)
        ;; remove all source folders, which have the destination folder among it's children (ie. a recursive drop error)
        movableFolderElementList 
        (filterv (fn [sourceFolderElement]
                   (not (nat-int? (.indexOf
                                   (conj (get-all-subfolders-from-map (:children sourceFolderElement)) (:id sourceFolderElement))
                                   destinationFolderIdString)))) sourceFolderElementList)
        ;; reconstructArray is same as selectedelementlist, but with folders that have recursive drop errors removed
        reconstructArray (vec (sort-by #(.indexOf source-element-id-array %)
                                       (distinct (concat (mapv :id movableFolderElementList) sourceLinkIdList))))]

    #_(println ["source-element-id-array: " source-element-id-array
              "movableFolderElementList: " (mapv :id movableFolderElementList)
              "reconstructArray: " reconstructArray
              "(not= reconstructArray source-element-id-array)" (not= (set reconstructArray) (set source-element-id-array))])
    
    (cond (= (set sourceLinkIdList) (set source-element-id-array)) ;; all source elements are links
          (chrome-move-bookmark-selected-wrapper sourceLinkIdList destinationDropZoneId dropIndex
                                                 {:type :updateSome
                                                  :updateDzs (vec (distinct [sourceDropzoneId destinationDropZoneId])) })
          (not= (set reconstructArray) (set source-element-id-array)) ;; some folders are recursive drop errors, but move the rest
          (do (run-fade-alert "Cannot move a folder into itself.")
              (chrome-move-bookmark-selected-wrapper reconstructArray destinationDropZoneId dropIndex
                                                     {:type :updateSome
                                                      :updateDzs (vec (distinct [sourceDropzoneId destinationDropZoneId])) }))
          :else ;; a mix of folders and links to be moved, but no recursive drop errors
          (chrome-move-bookmark-selected-wrapper reconstructArray destinationDropZoneId dropIndex
                                                 {:type :updateSome
                                                  :updateDzs (vec (distinct [sourceDropzoneId destinationDropZoneId])) }))))

;; fetch-all-selected primarily used in: center-overlay-ondrop, contextmenus, :link and folder on-drag-start
(defn fetch-all-selected []
  (let [ ;; selectedInDropzones of the form ([:dropzone-1 ["7" "2021"]] [:dropzone-2 []])
        ;; {:dropzone-1 {:folderId "1", :pinned false, :menuOpen true, :cutoffDropzoneElements 20, :selected [], :z-index 1},
        ;;  :dropzone-2 {:folderId "2", :pinned false, :menuOpen true, :cutoffDropzoneElements 20, :selected [], :z-index 1}} ;; =>
        ;; ([:dropzone-1 ["7" "2021"]] [:dropzone-2 []]) ;;=> ([:dropzone-1 ["7" "2021"]]) 
        selectedInDropzones (for [[x y] @(rf/subscribe [:dnd/dropzone-options])]  [x (:selected y)])
        ;; [[:tabselected ["9000000004" :anchor "9000000005"]]]
        tabSelected @(rf/subscribe [:dnd/get-selected :tab-history :tabselected])
        ;; [[:historyselected ["9000000040" :anchor "9000000050"]]]
        historySelected @(rf/subscribe [:dnd/get-selected :tab-history :historyselected])
        fetchSelectedWithAnchor ;; removes all dropzones with no selections
        ;; ([:dropzone-1 []] [:dropzone-2 []] [:dropzone-2068 []] [:tabselected []] [:historyselected ["9000000070" :anchor "9000000080"]])
        ;; => [:historyselected ["9000000070" :anchor "9000000080"]]
        (first (remove #(empty? (second %)) (concat selectedInDropzones [[:tabselected tabSelected]] [[:historyselected historySelected]])))

        fetchSelected [(first fetchSelectedWithAnchor) (remove #(= % :anchor) (second fetchSelectedWithAnchor))]
        ;; filter out the anchor ;; => [:dropzone-1 ("7" "2021")] or [:historyselected ("9000000070" "9000000080")] or [:tabselected ("90001" "90004")]

        fetchElements (case (first fetchSelected) ;;  (first fetchSelected) is: :dropzone-???, :tabselected or :historyselected
                        :tabselected @(rf/subscribe [:dnd/get-tabs]) ;; vector of maps
                        :historyselected @(rf/subscribe [:dnd/get-history]) ;; vector of maps
                        @(rf/subscribe [:dnd/dropped-elements (first fetchSelected)]))

        selectedElements (filter #((set (second fetchSelected)) (:id %)) fetchElements)]
    (remove nil? selectedElements)))


;; arg: selectedIdIndexList (sort-by last (map #(list (:id %) (:index %) ) selectedElementList))
;; arg: dropIndex is actual :index of target element being dropped on even in a :searchDropzone
;; where selectedElementList is a list of dropzone elements.
;; Examples:
;; [dropIndex:  1 before: selectedIdIndexList:  ((3808 3) (4128 4) (4168 5) (4169 6)) after: reversedPartitionedSelectedIdIndexList:  [4169 4168 4128 3808]]
;; [dropIndex:  4 before: selectedIdIndexList:  ((3808 3) (4128 4) (4168 5) (4169 6)) after: reversedPartitionedSelectedIdIndexList:  [3808 4169 4168 4128]]
;; [dropIndex:  5 before: selectedIdIndexList:  ((3808 3) (4128 4) (4168 5) (4169 6)) after: reversedPartitionedSelectedIdIndexList:  [3808 4128 4169 4168]]
;; [dropIndex:  6 before: selectedIdIndexList:  ((3808 3) (4128 4) (4168 5) (4169 6)) after: reversedPartitionedSelectedIdIndexList:  [3808 4128 4168 4169]]
;; [dropIndex:  10 before: selectedIdIndexList:  ((3808 3) (4128 4) (4168 5) (4169 6)) after: reversedPartitionedSelectedIdIndexList:  [3808 4128 4168 4169]]
(defn reorder-selected-elements [selectedIdIndexList dropIndex]
  (let [partitionedSelectedIdIndexList (partition-by #(>= (second %) dropIndex) selectedIdIndexList)
        ;; when moving selections: items before the drop index should be reversed : Items, dropIndex
        ;; when moving selections: items on or after the drop index should not be reversed : dropIndex, Items
        ;; (If target is selected then, selected elements before the target will not move the target; they will push elements up.
        ;; But selected elements after the target will push the target down. Therefore the target, and all selected elements
        ;; after it should be reversed.) Essentially anything on or after the dropIndex is reversed.
        reversedPartitionedSelectedIdIndexList (cond
                                                 ;; case dropIndex, Items : reverse all items
                                                 (and (= 1 (count partitionedSelectedIdIndexList))
                                                      (<= dropIndex (second (first selectedIdIndexList)) ))
                                                 (vec (reverse (map first (first partitionedSelectedIdIndexList))))
                                                 ;; case Items, dropIndex : reverse nothing
                                                 (and (= 1 (count partitionedSelectedIdIndexList))
                                                      (>= dropIndex (second (last selectedIdIndexList))))
                                                 (vec (map first (first partitionedSelectedIdIndexList)))
                                                 :else
                                                 ;; case dropIndex is selected reverse everything on or after the dropIndex
                                                 (vec (map first (concat (first partitionedSelectedIdIndexList)
                                                                         (reverse (second partitionedSelectedIdIndexList))))))]
    reversedPartitionedSelectedIdIndexList))

;; selectedElements a list of elements (maps), dropIndex an int
(defn count-selected-before-dropindex [selectedElements dropIndex]
  (let [totalElements (count selectedElements)
        greaterThan (map #(< (:index %) dropIndex) selectedElements)
        countGreaterThan (count (filter true? greaterThan))]
    countGreaterThan))

;; destination is blank:(:parentId de) or folder-middle:(:id dropzoneElement) or edge:(:parentId dropzoneElement) when called
;; showAlert is true or false
(defn center-overlay-ondrop [s event destination dropIndex showAlert]
  ;; 3 outcomes: #floatingsearch is found then click, #search is found then click, or nothing is found (floating but title bar is present) do nothing
  (when (:componentRef @s)
    (if-let [searchElement (.querySelector (-> (:componentRef @s)  .-parentNode .-parentNode ) "#search,#floatingsearch")] (.click searchElement)))
  (let [widget (cljs.reader/read-string (.getData (.-dataTransfer event) "text/my-custom-data"))
        source-id (:id widget)]
    (cond (= (:type widget) :tablink)
          ;; if it is a :tablinks fetch all selected :tablinks, then create them in destinationDropZoneId
          (let [get-selected (vec (distinct (conj @(rf/subscribe [:dnd/get-selected :tab-history :tabselected]) source-id)))
                get-tabs
                (if (.hasOwnProperty js/chrome "bookmarks")
                  @(rf/subscribe [:dnd/get-tabs])
                  (for [x (range 10)] {:id (str (+ 9000000000 x)) :windowId (str (+ 900000 x)) :title "DuckDuckGo - Privacy, simplified." :url "https://duckduckgo.com/" :type :tablink}))
                sourceTabArray (reverse (filter #((set get-selected) (:id %)) get-tabs ))
                destDzIdString destination]
            (clear-all-selections-except)
            (when showAlert (run-fade-alert (str (count get-selected) " dropped" ))
                  (prn ["from center-overlay-ondrop: get-selected: " get-selected
                        "(str (count get-selected) dropped): " (str (count get-selected) " dropped" )]))
            (when (.hasOwnProperty js/chrome "bookmarks")
              (.removeListener js/chrome.bookmarks.onCreated onevent-dispatch-refresh-fnhandle))
            (doseq [createTab sourceTabArray]
              ;; only called in components' javascript :on-drop events, not from any re-frame event dispatch
              (rf/dispatch-sync [:dnd/create-bookmark (:id createTab) destDzIdString dropIndex (:title createTab) (:url createTab)] ))
            (when (.hasOwnProperty js/chrome "bookmarks")
              (.addListener js/chrome.bookmarks.onCreated onevent-dispatch-refresh-fnhandle)
              ;; for offline :dnd/create-bookmark calls [:dnd/synch-all-dropzones-to-folderId] for each bookmark created
              (rf/dispatch [:chrome-synch-all-dropzones-to-folderId {:type :updateAll}])))
          (= (:type widget) :historylink)
          ;; if it is a :historylink fetch all selected :historylinks, then create them in destinationDropZoneId
          (let [get-selected (vec (distinct (conj @(rf/subscribe [:dnd/get-selected :tab-history :historyselected]) source-id)))
                get-history
                (if (.hasOwnProperty js/chrome "bookmarks")
                  @(rf/subscribe [:dnd/get-history])
                  (for [x (range 10)] {:id (str (+ 9000000000 (* 10 x))) :title "Google History" :url "https://google.com/" :type :historylink}))
                sourceTabArray (reverse (filter #((set get-selected) (:id %)) get-history))
                destDzIdString destination]
            (clear-all-selections-except)
            (when showAlert (run-fade-alert (str (count get-selected) " dropped" )))
            (when (.hasOwnProperty js/chrome "bookmarks")
              (.removeListener js/chrome.bookmarks.onCreated onevent-dispatch-refresh-fnhandle))
            (doseq [createTab sourceTabArray]
              ;; only called in components' javascript :on-drop events, not from any re-frame event dispatch
              (rf/dispatch-sync [:dnd/create-bookmark (:id createTab) destDzIdString dropIndex (:title createTab) (:url createTab)] ))
            (when (.hasOwnProperty js/chrome "bookmarks")
              (.addListener js/chrome.bookmarks.onCreated onevent-dispatch-refresh-fnhandle)
              ;; for offline :dnd/create-bookmark calls [:dnd/synch-all-dropzones-to-folderId] for each bookmark created
              (rf/dispatch [:chrome-synch-all-dropzones-to-folderId {:type :updateAll}])))
          :else
          ;; else fetch selected items from sourceDropzoneId and run recursive-dropdispatch-multiple with destinationDropZoneId
          (let [sourceDropzoneId (fid->dkey (:parentId widget))
                destinationDropZoneId (fid->dkey destination)
                ;; If the dragged widget is not selected, then :link and :folderbox will deselect everything on drag start long before 
                ;; center-overlay-ondrop. If nothing is selected, then the dragging widget must be added here.
                selectedElementList (distinct (concat (fetch-all-selected) (list widget)))
                source-element-id-array (distinct (conj (map :id (fetch-all-selected)) source-id))
                source-element-id-array-reversed (vec (reverse source-element-id-array))
                selectedSetPred (set source-element-id-array)]
            (clear-all-selections-except)
            (when showAlert (run-fade-alert (str (count source-element-id-array) " dropped" )))
            (cond (some? (:searchDropzone widget)) ;; dropzone is a search result abort
                  (let [selectedElementsFromDestDz (filter #(= (fid->dkey (:parentId %)) destinationDropZoneId) selectedElementList)
                        selectedElementsNotFromDestDz (filter #(not (= (fid->dkey (:parentId %)) destinationDropZoneId)) selectedElementList)
                        reorderSelectedElementsFromDestDz
                        (reorder-selected-elements (map #(list (:id %) (:index %) ) selectedElementsFromDestDz)  dropIndex)
                        ;; You must place the external dropzone elements at the dropIndex location in order to ensure they always end up
                        ;; at the end. Elements moving forwards should not be reversed. While Elements moving backwards or from external
                        ;; dropzones push elements down and must be reversed. The External dropzone elements should be placed between the two.
                        partIndex (count-selected-before-dropindex selectedElementsFromDestDz dropIndex)
                        splitSelected (split-at partIndex reorderSelectedElementsFromDestDz)
                        spliceExternalDropzones (concat (first splitSelected) (mapv :id (reverse selectedElementsNotFromDestDz))
                                                        (second splitSelected))]
                    (when (.hasOwnProperty js/chrome "bookmarks") (.removeListener js/chrome.bookmarks.onMoved onevent-dispatch-refresh-fnhandle))
                    (recursive-drop-dispatch-multiple [ sourceDropzoneId spliceExternalDropzones ] [destinationDropZoneId  dropIndex]))
                  
                  (not= sourceDropzoneId destinationDropZoneId) ;; is not a search result but source and dest are different
                  (if (.hasOwnProperty js/chrome "bookmarks")
                    (do (.removeListener js/chrome.bookmarks.onMoved onevent-dispatch-refresh-fnhandle)
                        (process-drop-dispatch [sourceDropzoneId source-element-id-array-reversed] [destinationDropZoneId dropIndex]))
                    (recursive-drop-dispatch-multiple [ sourceDropzoneId source-element-id-array-reversed] [destinationDropZoneId  dropIndex]))
                  
                  (= sourceDropzoneId destinationDropZoneId) ;; is not a search result but source and dest are same
                  (let [selectedIdIndexList (sort-by last (map #(list (:id %) (:index %) ) selectedElementList))
                        reversedPartitionedSelectedIdIndexList (reorder-selected-elements selectedIdIndexList dropIndex)]
                    (if (.hasOwnProperty js/chrome "bookmarks")
                      (do (.removeListener js/chrome.bookmarks.onMoved onevent-dispatch-refresh-fnhandle)
                          (process-drop-dispatch [sourceDropzoneId reversedPartitionedSelectedIdIndexList] [destinationDropZoneId dropIndex]))
                      (recursive-drop-dispatch-multiple [ sourceDropzoneId reversedPartitionedSelectedIdIndexList] [destinationDropZoneId  dropIndex])))
                  
                  :else ;; unknown
                  (do (.removeListener js/chrome.bookmarks.onMoved onevent-dispatch-refresh-fnhandle)
                      (recursive-drop-dispatch-multiple [ sourceDropzoneId source-element-id-array-reversed ] [destinationDropZoneId  dropIndex]))
                  )))))

(defn edge-overlay-ondrop [s dropzoneElement topOrBottom elementClass] 
  "topOrBottom is a key :top or :bottom"
  (fn [e]
    (do (.preventDefault e) ;; drag and drop works without this
        (.setAttribute (.-previousElementSibling (:componentRef @s)) "class" "dummy-element")
        (.setAttribute (.-nextElementSibling (:componentRef @s)) "class" "dummy-element") 

        (when @s (setattrib (:componentRef @s) "className" elementClass))

        (center-overlay-ondrop s e (:parentId dropzoneElement)
                               (if (= :top topOrBottom) (:index dropzoneElement) (inc (:index dropzoneElement))) false))))


(defn link-overlay [top height s currentDragState dropzoneElement topOrBottom]
  [:div {:style {:position "absolute" :left "0px" :top top :height height :width "100%"
                 ;; in display is set to none so that hover event is not captured by this overlay div
                 :display (if @currentDragState "block" "none")}
         :on-drop (edge-overlay-ondrop s dropzoneElement topOrBottom "link-element")
         
         :on-drag-over #(do (.preventDefault %) ;; it works without this
                            (if (= :bottom topOrBottom)
                              (.setAttribute (.-nextElementSibling (:componentRef @s)) "class" "dummy-element_drag_over")
                              (.setAttribute (.-previousElementSibling (:componentRef @s)) "class" "dummy-element_drag_over")))
         
         :on-drag-leave #(do (if (= :bottom topOrBottom)
                               (.setAttribute (.-nextElementSibling (:componentRef @s)) "class" "dummy-element_drag_leave")
                               (.setAttribute (.-previousElementSibling (:componentRef @s)) "class" "dummy-element_drag_leave")))}])



;; << alerts >>


(def alert-msg (r/atom nil))

;; :pointer-events "none" allows for mouse clicks to ignore it and pass through to elements behind it
;; (+ 20000 (inc zIndex)) is used to stay above the contextmenu which has 20000 zIndex. But @lastzIndex is not set to 20000 to keep
;; the floating menus beneath the context menu.
(defn fade-alert [zIndex]
  (fn [zIndex]
    [:div#fade-alert.fade-alert {:style {:position "absolute" :z-index (+ 20000 (inc zIndex)) :left "50vw"  :top "40vh"
                                         :transform "translate(-50%, -50%)"
                                         :font-style "Montserrat" :font-weight "bold" :font-size "2.5vw"  :pointer-events "none"}}
     @alert-msg]))


(defn after-mount-fade-alert [comp]
  (let [fade-alert-htmlelement (rdom/dom-node comp)] ;; r/dom-node: react-class -> HTMLElement

    (.addEventListener fade-alert-htmlelement "animationend" (fn [e] (setstyle fade-alert-htmlelement "display" "none") ))))


 
(defn mount-fade-alert [zIndex]
  (r/create-class {:reagent-render  fade-alert
                   :component-did-mount after-mount-fade-alert})) 

(defn run-fade-alert [message]
  (reset! alert-msg message)

  (when (some? (.getElementById js/document "fade-alert"))
    (rdom/unmount-component-at-node (.getElementById js/document "fade-alert-container")))
  
  (when (nil? (.getElementById js/document "fade-alert"))
    (rdom/render [mount-fade-alert @lastZIndex] (.getElementById js/document "fade-alert-container")))

  (when (some? (.getElementById js/document "fade-alert"))
    (setstyle (.getElementById js/document "fade-alert") "display" "block")))


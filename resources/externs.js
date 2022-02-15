/*  include 2640 chrome.runtime.openOptionsPage, 2957 chrome.tabs, 3830 chrome.bookmarks, 4774 chrome.history */

/**
 * @param {function(): void=} opt_callback Callback function.
 * @return {undefined}
 */
chrome.runtime.openOptionsPage = function(opt_callback) {};


/**
 * @const
 * @see https://developer.chrome.com/extensions/tabs
 */
chrome.tabs = {};


/**
 * @enum {string}
 * @see https://developer.chrome.com/extensions/tabs#type-TabStatus
 */
chrome.tabs.TabStatus = {
  COMPLETE: '',
  LOADING: '',
};


/**
 * @const {number}
 * @see https://developer.chrome.com/extensions/tabs#property-TAB_ID_NONE
 */
chrome.tabs.TAB_ID_NONE = -1;


/**
 * @typedef {?{
 *   code: (string|undefined),
 *   file: (string|undefined),
 *   allFrames: (boolean|undefined),
 *   matchAboutBlank: (boolean|undefined),
 *   runAt: (string|undefined)
 * }}
 */
chrome.tabs.InjectDetails;


/**
 * @see https://developer.chrome.com/extensions/tabs#method-captureVisibleTab
 * @param {number|!chrome.types.ImageDetails|function(string):void}
 *     windowIdOrOptionsOrCallback One of:
 *     The target window.
 *     An object defining details about the format and quality of an image, in
 *     which case the window defaults to the current window.
 *     A callback function which accepts the data URL string of a JPEG encoding
 *     of the visible area of the captured tab.
 * @param {(!chrome.types.ImageDetails|function(string):void)=}
 *     opt_optionsOrCallback Either an object defining details about the
 *     format and quality of an image, or a callback function which accepts the
 *     data URL string of a JPEG encoding of the visible area of the captured
 *     tab.
 * @param {function(string):void=} opt_callback A callback function which
 *     accepts the data URL string of a JPEG encoding of the visible area of the
 *     captured tab.
 * @return {undefined}
 */
chrome.tabs.captureVisibleTab = function(
    windowIdOrOptionsOrCallback, opt_optionsOrCallback, opt_callback) {};


/**
 * @param {number} tabId Tab Id.
 * @param {{name: (string|undefined)}=} connectInfo Info Object.
 * @return {Port} New port.
 */
chrome.tabs.connect = function(tabId, connectInfo) {};


/**
 * @typedef {?{
 *   windowId: (number|undefined),
 *   index: (number|undefined),
 *   url: (string|undefined),
 *   active: (boolean|undefined),
 *   pinned: (boolean|undefined),
 *   openerTabId: (number|undefined)
 * }}
 */
chrome.tabs.CreateProperties;


/**
 * @param {!chrome.tabs.CreateProperties} createProperties Info object.
 * @param {function(!Tab): void=} opt_callback The callback function.
 * @return {undefined}
 */
chrome.tabs.create = function(createProperties, opt_callback) {};


/**
 * @see https://developer.chrome.com/extensions/tabs#method-detectLanguage
 * @param {number|function(string): void} tabIdOrCallback The tab id, or a
 *     callback function that will be invoked with the language of the active
 *     tab in the current window.
 * @param {function(string): void=} opt_callback An optional callback function
 *     that will be invoked with the language of the tab specified as first
 *     argument.
 * @return {undefined}
 */
chrome.tabs.detectLanguage = function(tabIdOrCallback, opt_callback) {};


/**
 * @see https://developer.chrome.com/extensions/tabs#method-discard
 * @param {number|function(!Tab): void} tabIdOrCallback
 * @param {function(!Tab): void=} opt_callback
 */
chrome.tabs.discard;


/**
 * @see https://developer.chrome.com/extensions/tabs#method-executeScript
 * @param {number|!chrome.tabs.InjectDetails} tabIdOrDetails
 *     Either the id of the tab in which to run the script, or an object
 *     containing the details of the script to run, in which case the script
 *     will be executed in the active tab of the current window.
 * @param {(!chrome.tabs.InjectDetails|function(!Array<*>):void)=}
 *     opt_detailsOrCallback Either an object containing the details of the
 *     script to run, if the tab id was speficied as first argument, or a
 *     callback that will be invoked with the result of the execution of the
 *     script in every injected frame.
 * @param {function(!Array<*>):void=} opt_callback A callback that will be
 *     invoked with the result of the execution of the script in every
 *     injected frame.
 * @return {undefined}
 */
chrome.tabs.executeScript = function(
    tabIdOrDetails, opt_detailsOrCallback, opt_callback) {};


/**
 * @param {number} tabId Tab id.
 * @param {function(!Tab): void} callback Callback.
 * @return {undefined}
 */
chrome.tabs.get = function(tabId, callback) {};


/**
 * Note (2014-05-21): Because this function is deprecated, the types of it's
 * parameters were not upgraded to make the first parameter optional and to mark
 * the Array and Tab in the callback as non-null.
 *
 * @param {number?} windowId Window id.
 * @param {function(Array<Tab>): void} callback Callback.
 * @deprecated Please use tabs.query {windowId: windowId}.
 * @return {undefined}
 */
chrome.tabs.getAllInWindow = function(windowId, callback) {};


/**
 * @param {function(!Tab=): void} callback Callback.
 * @return {undefined}
 */
chrome.tabs.getCurrent = function(callback) {};


/**
 * Note (2014-05-21): Because this function is deprecated, the types of it's
 * parameters were not upgraded to make the first parameter optional and to mark
 * the Array and Tab in the callback as non-null.
 *
 * @param {number?} windowId Window id.
 * @param {function(Tab): void} callback Callback.
 * @deprecated Please use tabs.query({active: true}).
 * @return {undefined}
 */
chrome.tabs.getSelected = function(windowId, callback) {};


/**
 * @see https://developer.chrome.com/extensions/tabs#method-getZoom
 * @param {number|function(number): void} tabIdOrCallback
 * @param {function(number): void=} opt_callback
 * @return {undefined}
 */
chrome.tabs.getZoom = function(tabIdOrCallback, opt_callback) {};


/**
 * @see https://developer.chrome.com/extensions/tabs#type-ZoomSettings
 * @typedef {?{
 *   mode: (string|undefined),
 *   scope: (string|undefined),
 *   defaultZoomFactor: (number|undefined),
 * }}
 */
chrome.tabs.ZoomSettings;


/**
 * @see https://developer.chrome.com/extensions/tabs#method-getZoomSettings
 * @param {number|function(!chrome.tabs.ZoomSettings): void} tabIdOrCallback
 * @param {function(!chrome.tabs.ZoomSettings): void=} opt_callback
 */
chrome.tabs.getZoomSettings = function(tabIdOrCallback, opt_callback) {};


/**
 * @typedef {?{
 *   windowId: (number|undefined),
 *   tabs: (number|!Array<number>)
 * }}
 */
chrome.tabs.HighlightInfo;


/**
 * @see https://developer.chrome.com/extensions/tabs#method-highlight
 * @param {!chrome.tabs.HighlightInfo} highlightInfo
 * @param {function(!ChromeWindow): void=} callback Callback function invoked
 *    for each window whose tabs were highlighted.
 * @return {undefined}
 */
chrome.tabs.highlight = function(highlightInfo, callback) {};


/**
 * @link https://developer.chrome.com/extensions/tabs#method-insertCSS
 * @param {number|!chrome.tabs.InjectDetails} tabIdOrDetails
 *     Either the id of the tab in which to run the script, or an object
 *     containing the details of the CSS to insert, in which case the script
 *     will be executed in the active tab of the current window.
 * @param {(!chrome.tabs.InjectDetails|function():void)=}
 *     opt_detailsOrCallback Either an object containing the details of the
 *     CSS to insert, if the tab id was speficied as first argument, or a
 *     callback that will be invoked after the CSS has been injected.
 * @param {function():void=} opt_callback A callback that will be invoked after
 *     the CSS has been injected.
 * @return {undefined}
 */
chrome.tabs.insertCSS = function(
    tabIdOrDetails, opt_detailsOrCallback, opt_callback) {};


/**
 * @typedef {?{
 *   windowId: (number|undefined),
 *   index: number
 * }}
 */
chrome.tabs.MoveProperties;


/**
 * @param {number|!Array<number>} tabId Tab id or array of tab ids.
 * @param {!chrome.tabs.MoveProperties} moveProperties
 * @param {function((!Tab|!Array<!Tab>)): void=} opt_callback Callback.
 * @return {undefined}
 */
chrome.tabs.move = function(tabId, moveProperties, opt_callback) {};


/**
 * @typedef {?{
 *   active: (boolean|undefined),
 *   pinned: (boolean|undefined),
 *   audible: (boolean|undefined),
 *   muted: (boolean|undefined),
 *   highlighted: (boolean|undefined),
 *   discarded: (boolean|undefined),
 *   autoDiscardable: (boolean|undefined),
 *   currentWindow: (boolean|undefined),
 *   lastFocusedWindow: (boolean|undefined),
 *   status: (!chrome.tabs.TabStatus|string|undefined),
 *   title: (string|undefined),
 *   url: (!Array<string>|string|undefined),
 *   windowId: (number|undefined),
 *   windowType: (string|undefined),
 *   index: (number|undefined)
 * }}
 */
chrome.tabs.QueryInfo;


/**
 * @param {!chrome.tabs.QueryInfo} queryInfo
 * @param {function(!Array<!Tab>): void} callback Callback.
 * @return {undefined}
 */
chrome.tabs.query = function(queryInfo, callback) {};


/**
 * @see https://developer.chrome.com/extensions/tabs#method-query
 * @param {number} tabId The ID of the tab which is to be duplicated.
 * @param {(function(!Tab=):void)=} opt_callback A callback to be invoked with
 *     details about the duplicated tab.
 * @return {undefined}
 */
chrome.tabs.duplicate = function(tabId, opt_callback) {};


/**
 * @typedef {?{
 *   bypassCache: (boolean|undefined)
 * }}
 */
chrome.tabs.ReloadProperties;


/**
 * @see https://developer.chrome.com/extensions/tabs#method-reload
 * @param {(number|!chrome.tabs.ReloadProperties|function():void)=}
 *     opt_tabIdOrReloadPropertiesOrCallback One of:
 *     The ID of the tab to reload; defaults to the selected tab of the current
 *     window.
 *     An object specifying boolean flags to customize the reload operation.
 *     A callback to be invoked when the reload is complete.
 * @param {(!chrome.tabs.ReloadProperties|function():void)=}
 *     opt_reloadPropertiesOrCallback Either an object specifying boolean flags
 *     to customize the reload operation, or a callback to be invoked when the
 *     reload is complete, if no object needs to be specified.
 * @param {function():void=} opt_callback  A callback to be invoked when the
 *     reload is complete.
 * @return {undefined}
 */
chrome.tabs.reload = function(
    opt_tabIdOrReloadPropertiesOrCallback, opt_reloadPropertiesOrCallback,
    opt_callback) {};


/**
 * @param {number|!Array<number>} tabIds A tab ID or an array of tab IDs.
 * @param {function(): void=} opt_callback Callback.
 * @return {undefined}
 */
chrome.tabs.remove = function(tabIds, opt_callback) {};


/**
 * @typedef {?{
 *   frameId: (number|undefined)
 * }}
 */
chrome.tabs.SendMessageOptions;


/**
 * @param {number} tabId Tab id.
 * @param {*} request The request value of any type.
 * @param {(!chrome.tabs.SendMessageOptions|function(*): void)=}
 *     opt_optionsOrCallback The object with an optional "frameId" or the
 *     callback function.
 * @param {function(*): void=} opt_callback The callback function which
 *     takes a JSON response object sent by the handler of the request.
 * @return {undefined}
 */
chrome.tabs.sendMessage = function(
    tabId, request, opt_optionsOrCallback, opt_callback) {};


/**
 * @param {number} tabId Tab id.
 * @param {*} request The request value of any type.
 * @param {function(*): void=} opt_callback The callback function which
 *     takes a JSON response object sent by the handler of the request.
 * @deprecated Please use runtime.sendMessage.
 * @return {undefined}
 */
chrome.tabs.sendRequest = function(tabId, request, opt_callback) {};


/**
 * @see https://developer.chrome.com/extensions/tabs#method-setZoom
 * @param {number} tabIdOrZoomFactor
 * @param {(number|function(): void)=} opt_zoomFactorOrCallback
 * @param {function(): void=} opt_callback
 * @return {undefined}
 */
chrome.tabs.setZoom = function(
    tabIdOrZoomFactor, opt_zoomFactorOrCallback, opt_callback) {};


/**
 * @see https://developer.chrome.com/extensions/tabs#method-setZoomSettings
 * @param {number|!chrome.tabs.ZoomSettings} tabIdOrZoomSettings
 * @param {(!chrome.tabs.ZoomSettings|function(): void)=}
 *     opt_zoomSettingsOrCallback
 * @param {function(): void=} opt_callback
 */
chrome.tabs.setZoomSettings = function(
    tabIdOrZoomSettings, opt_zoomSettingsOrCallback, opt_callback) {};


/**
 * @typedef {?{
 *   url: (string|undefined),
 *   active: (boolean|undefined),
 *   highlighted: (boolean|undefined),
 *   pinned: (boolean|undefined),
 *   openerTabId: (number|undefined)
 * }}
 */
chrome.tabs.UpdateProperties;


/**
 * @see https://developer.chrome.com/extensions/tabs#method-update
 * @param {number|!chrome.tabs.UpdateProperties} tabIdOrUpdateProperties
 *     Either the id of the tab to update, or an object with new property
 *     values, in which case the selected tab of the current window will be
 *     updated.
 * @param {(!chrome.tabs.UpdateProperties|function(Tab):void)=}
 *     opt_updatePropertiesOrCallback Either an object with new property values,
 *     if the tabId was specified as first parameter, or an optional callback
 *     that will be invoked with information about the tab being updated.
 * @param {function(!Tab=): void=} opt_callback An optional callback that will
 *     be invoked with information about the tab being updated.
 * @return {undefined}
 */
chrome.tabs.update = function(
    tabIdOrUpdateProperties, opt_updatePropertiesOrCallback, opt_callback) {};


/**
 * @type {!ChromeEvent}
 * @deprecated Please use tabs.onActivated.
 */
chrome.tabs.onActiveChanged;


/** @type {!ChromeEvent} */
chrome.tabs.onActivated;


/** @type {!ChromeEvent} */
chrome.tabs.onAttached;


/** @type {!ChromeEvent} */
chrome.tabs.onCreated;


/** @type {!ChromeEvent} */
chrome.tabs.onDetached;


/**
 * @type {!ChromeEvent}
 * @deprecated Please use tabs.onHighlighted.
 */
chrome.tabs.onHighlightChanged;


/**
 * @type {!ChromeEvent}
 */
chrome.tabs.onHighlighted;


/** @type {!ChromeEvent} */
chrome.tabs.onMoved;


/** @type {!ChromeEvent} */
chrome.tabs.onRemoved;


/** @type {!ChromeEvent} */
chrome.tabs.onUpdated;


/** @type {!ChromeEvent} */
chrome.tabs.onReplaced;

// DEPRECATED:
// TODO(user): Remove once all usage has been confirmed to have ended.


/**
 * @type {!ChromeEvent}
 * @deprecated Please use tabs.onActivated.
 */
chrome.tabs.onSelectionChanged;


/**
 * @see https://developer.chrome.com/extensions/tabs#event-onZoomChange
 * @type {!ChromeObjectEvent}
 */
chrome.tabs.onZoomChange;


/**
 * @const
 * @see https://developer.chrome.com/extensions/bookmarks.html
 */
chrome.bookmarks = {};


/**
 * @typedef {?{
 *   parentId: (string|undefined),
 *   index: (number|undefined),
 *   url: (string|undefined),
 *   title: (string|undefined)
 * }}
 * @see https://developer.chrome.com/extensions/bookmarks#method-create
 */
chrome.bookmarks.CreateDetails;


/**
 * @typedef {?{
 *   query: (string|undefined),
 *   url: (string|undefined),
 *   title: (string|undefined)
 * }}
 * @see https://developer.chrome.com/extensions/bookmarks#method-search
 */
chrome.bookmarks.SearchDetails;


/**
 * @param {(string|Array<string>)} idOrIdList
 * @param {function(Array<BookmarkTreeNode>): void} callback The
 *     callback function which accepts an array of BookmarkTreeNode.
 * @return {Array<BookmarkTreeNode>}
 */
chrome.bookmarks.get = function(idOrIdList, callback) {};


/**
 * @param {string} id
 * @param {function(Array<BookmarkTreeNode>): void} callback The
 *     callback function which accepts an array of BookmarkTreeNode.
 * @return {Array<BookmarkTreeNode>}
 */
chrome.bookmarks.getChildren = function(id, callback) {};


/**
 * @param {number} numberOfItems The number of items to return.
 * @param {function(Array<BookmarkTreeNode>): void} callback The
 *     callback function which accepts an array of BookmarkTreeNode.
 * @return {Array<BookmarkTreeNode>}
 */
chrome.bookmarks.getRecent = function(numberOfItems, callback) {};


/**
 * @param {function(Array<BookmarkTreeNode>): void} callback The
 *     callback function which accepts an array of BookmarkTreeNode.
 * @return {Array<BookmarkTreeNode>}
 */
chrome.bookmarks.getTree = function(callback) {};


/**
 * @param {string} id The ID of the root of the subtree to retrieve.
 * @param {function(Array<BookmarkTreeNode>): void} callback The
 *     callback function which accepts an array of BookmarkTreeNode.
 * @return {Array<BookmarkTreeNode>}
 */
chrome.bookmarks.getSubTree = function(id, callback) {};


/**
 * @param {string|!chrome.bookmarks.SearchDetails} query
 * @param {function(Array<BookmarkTreeNode>): void} callback
 * @return {Array<BookmarkTreeNode>}
 */
chrome.bookmarks.search = function(query, callback) {};


/**
 * @param {chrome.bookmarks.CreateDetails} bookmark
 * @param {function(BookmarkTreeNode): void=} opt_callback The
 *     callback function which accepts a BookmarkTreeNode object.
 * @return {undefined}
 */
chrome.bookmarks.create = function(bookmark, opt_callback) {};


/**
 * @param {string} id
 * @param {Object} destination An object which has optional 'parentId' and
 *     optional 'index'.
 * @param {function(BookmarkTreeNode): void=} opt_callback
 *     The callback function which accepts a BookmarkTreeNode object.
 * @return {undefined}
 */
chrome.bookmarks.move = function(id, destination, opt_callback) {};


/**
 * @param {string} id
 * @param {Object} changes An object which may have 'title' as a key.
 * @param {function(BookmarkTreeNode): void=} opt_callback The
 *     callback function which accepts a BookmarkTreeNode object.
 * @return {undefined}
 */
chrome.bookmarks.update = function(id, changes, opt_callback) {};


/**
 * @param {string} id
 * @param {function(): void=} opt_callback
 * @return {undefined}
 */
chrome.bookmarks.remove = function(id, opt_callback) {};


/**
 * @param {string} id
 * @param {function(): void=} opt_callback
 * @return {undefined}
 */
chrome.bookmarks.removeTree = function(id, opt_callback) {};


/**
 * @param {function(): void=} opt_callback
 * @return {undefined}
 */
chrome.bookmarks.import = function(opt_callback) {};


/**
 * @param {function(): void=} opt_callback
 * @return {undefined}
 */
chrome.bookmarks.export = function(opt_callback) {};


/** @type {!ChromeEvent} */
chrome.bookmarks.onChanged;


/** @type {!ChromeEvent} */
chrome.bookmarks.onChildrenReordered;


/** @type {!ChromeEvent} */
chrome.bookmarks.onCreated;


/** @type {!ChromeEvent} */
chrome.bookmarks.onImportBegan;


/** @type {!ChromeEvent} */
chrome.bookmarks.onImportEnded;


/** @type {!ChromeEvent} */
chrome.bookmarks.onMoved;


/** @type {!ChromeEvent} */
chrome.bookmarks.onRemoved;



/**
 * @const
 * @see https://developer.chrome.com/extensions/history.html
 */
chrome.history = {};


/**
 * @param {Object<string, string>} details Object with a 'url' key.
 * @return {undefined}
 */
chrome.history.addUrl = function(details) {};


/**
 * @param {function(): void} callback Callback function.
 * @return {undefined}
 */
chrome.history.deleteAll = function(callback) {};


/**
 * @param {Object<string, string>} range Object with 'startTime'
 *     and 'endTime' keys.
 * @param {function(): void} callback Callback function.
 * @return {undefined}
 */
chrome.history.deleteRange = function(range, callback) {};


/**
 * @param {Object<string, string>} details Object with a 'url' key.
 * @return {undefined}
 */
chrome.history.deleteUrl = function(details) {};


/**
 * @param {Object<string, string>} details Object with a 'url' key.
 * @param {function(!Array<!VisitItem>): void} callback Callback function.
 * @return {!Array<!VisitItem>}
 */
chrome.history.getVisits = function(details, callback) {};


/**
 * @param {Object<string, string>} query Object with a 'text' (string)
 *     key and optional 'startTime' (number), 'endTime' (number) and
 *     'maxResults' keys.
 * @param {function(!Array<!HistoryItem>): void} callback Callback function.
 * @return {!Array<!HistoryItem>}
 */
chrome.history.search = function(query, callback) {};


/** @type {!ChromeEvent} */
chrome.history.onVisitRemoved;


/** @type {!ChromeEvent} */
chrome.history.onVisited;


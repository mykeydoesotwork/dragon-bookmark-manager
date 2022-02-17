# dragon-bookmark-manager

Bookmark Manager Extension

## Installation

Pending approval on chrome webstore. See "Build" instructions below.

## Usage

![select copy paste](/public/images/help/select-copy-paste.png)

Use CTRL or SHIFT click to select tabs, history, bookmarks or folders. Use Ctrl-A to select all, Ctrl-C to copy, Ctrl-X to cut, and Ctrl-V to paste above the link currently highlighted by the mouse, or into the folder currently highlighted by the mouse. To paste above a folder, instead of within it, right click the folder and select 'Paste (above)' from the popup context menu. Press DEL to delete selected folders or links. Deleting greater then 10 items requires confirmation. There is no undo -- undo has not been implemented yet.

![lock child](/public/images/help/lock-child.png)

Normally closing a parent folder will close all of it's children. Click the lock icon in the top left corner of a child folder, to prevent the child folder from being automatically closed by it's parent.

![lock child](/public/images/help/rollup.png)

If a floating window gets in the way, double click it's title bar to toggle hiding it's contents.

![bookmark manager override](/public/images/help/bookmark-manager-override.png)

Because this extension overrides the chrome bookmark manager, when you right click any bookmark or folder in chrome's native 'Bookmarks Bar' or 'Other Bookmarks' dropdown menu, and select 'Bookmark manager', this extension will open with that item's containing folder already shown. Similarly left clicking any divider which you have inserted via the popup context menu 'Insert divider...' option will do the same thing. Use the context menu 'Show parent folder' option to then show it's parent folder if necessary.

![show parent folder](/public/images/help/show-parent-folder.png)

To find the parent folder of any search result, link, or folder right click and select 'Show Parent Folder' from the popup context menu. Repeat to find all parent folders.

![history](/public/images/help/history.png)

Click the 'History: n days' text written on the history tab of the tab/history frame, to reset the number of days to search or display back to 1 day. By default, history is searched 1 day in the past -- use the (+) or (-) buttons to update the search results to look more days in the past.

![search box](/public/images/help/searchbox.png)
 To find the parent folder of a search result right click the item and select 'Show Parent Folder' from the popup context menu. Redo this for items in the parent folder to find further parent folders. Any search box can be cleared by pressing ESC or clicking the highlighted portion surrounding the search box. Search terms are combined with boolean 'AND'. All substrings of titles and urls are searched. Tabs, history or any folder can be searched by typing in the top right search box or clicking the magnifying glass icon in a floating window to reveal the search box in the titlebar.

![snap window](/public/images/help/snap-window.png)

Clicking the [<] [>] buttons attempts to increase the number of columns by decreasing the number of rows, up to a limit of at most 8 columns. The minimum and maximum number of rows and default columns can be set in the extension's options. Double click the border of a floating window to snap resize the window and show all of it's contents along that axis.

![recently modified](/public/images/help/recently-modified.png)

The 'Recently Modified' drop-down list shows a list of most recently modified folders. Deleting, renaming any element or folder, or moving any element out of a folder will not show up in 'Recently Modified'. Only creating a new element or folder, moving an element into a folder, or moving an element within a folder count as modifying the folder. This limitation relies upon the dateGroupModified property of BookmarkTreeNode in the chrome bookmark api.

## Options
![theme dark light](/public/images/help/theme-dark-light.png)  
Light or Dark Mode: The dark theme uses a black background with brown, red, and yellow borders. The light theme uses a grey background with the same borders.

Default column, row, and startup hide/show options for "Bookmarks Bar" and "Other Bookmarks" folder can be set in the extension options.

You can open the extension options by clicking the ![gear](/public/images/gear-option.png) icon in the upper right corner of the tabs/history frame.

## Build
Release Build:

    lein cljsbuild once

Then load unpacked extension in chrome from the "chrome-dragon-bookmark-manager" directory.

Figwheel Build:

    lein build-dev

The figwheel build does not have access to the chrome bookmarks api, and uses a localstorage key "otherbookmarks".
See start of dndmenu.cljs for instructions on how to create this localstorage key. 

## Credits
This code was originally based upon Marten Sytema's (Kah0ona) re-dnd:  
"A configurable drag/drop widget + API for re-frame apps"  
https://github.com/Kah0ona/re-dnd

Icons Used:  
![cross](/public/images/close16.png) [Close icons created by Pixel perfect - Flaticon](https://www.flaticon.com/free-icons/close)  
![info](/public/images/information.png) [Info icons created by Those Icons - Flaticon](https://www.flaticon.com/free-icons/info)  
![question mark](/chrome-dragon-bookmark-manager/images/help-web-button.png) [Question icons created by Freepik - Flaticon](https://www.flaticon.com/free-icons/question)  
![reset](/chrome-dragon-bookmark-manager/images/refreshing.png) [Refresh icons created by Freepik - Flaticon](https://www.flaticon.com/free-icons/refresh)  
![circle minus](/public/images/circle-minus.png)[Minus icons created by dmitri13 - Flaticon](https://www.flaticon.com/free-icons/minus)  
![circle plus](/public/images/circle-plus.png)[Plus icons created by dmitri13 - Flaticon](https://www.flaticon.com/free-icons/plus)  
![left chevron](/public/images/left-chevron.png)[Left arrow icons created by Google - Flaticon](https://www.flaticon.com/free-icons/left-arrow)  
![right chevron](/public/images/right-chevron.png) [Right arrow icons created by Google - Flaticon](https://www.flaticon.com/free-icons/right-arrow)  
![gear](/public/images/gear-option.png) [Cog icons created by Freepik - Flaticon](https://www.flaticon.com/free-icons/cog)  
![bar](/public/images/minus.png) [Minus icons created by Google - Flaticon](https://www.flaticon.com/free-icons/minus)  
![link](/public/images/link16.png) [Link icons created by Freepik - Flaticon](https://www.flaticon.com/free-icons/link)  
![magnifying glass](/public/images/magnifying-glass.png) [Search icons created by Those Icons - Flaticon](https://www.flaticon.com/free-icons/search)  
![folder](/public/images/folder16.png) [Empty folder icons created by Creative Stall Premium - Flaticon](https://www.flaticon.com/free-icons/empty-folder)  
![lock](/public/images/lock.png) [Lock icons created by Freepik - Flaticon](https://www.flaticon.com/free-icons/lock)  
:warning: [Error icons created by Vectors Market - Flaticon](https://www.flaticon.com/free-icons/error)  

## License

Copyright © 2022

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.


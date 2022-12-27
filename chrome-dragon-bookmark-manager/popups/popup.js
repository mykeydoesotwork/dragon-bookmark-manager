'use strict';


function fetchRecentlyModified(node) {
    var allFolderNodes = [];

    //from https://stackoverflow.com/a/10292336 -------------------------------------
    //run: console.log(chrome.bookmarks.getTree((item) => fetchRecentlyModified(item)))
    function processNode(node) {
	// recursively process child nodes
	if(node.children) {
	    if(node.dateGroupModified)
	    {
		allFolderNodes.push({title: node.title, id: node.id, dateGroupModified: node.dateGroupModified});
	    }
            node.children.forEach(function(child) { processNode(child); });
	}
    }

    processNode(node[0]);
    allFolderNodes.sort( (a,b) => {return b.dateGroupModified - a.dateGroupModified})

    repopulateMenuContainer(allFolderNodes.slice(0,5));

    
}

//bookmarkNodeObjArray
    // 0 : {title: 'ref9 -- chrome extension popup', id: '2764', dateGroupModified: 1671753911151}
    // 1 : {title: 'Other bookmarks', id: '2', dateGroupModified: 1671747250241}
    // 2 : {title: 'Bookmarks bar', id: '1', dateGroupModified: 1671747172050}
    // 3 : {title: 'tutorials', id: '237', dateGroupModified: 1671715973319}
    // 4 : {title: 'twitch', id: '2716', dateGroupModified: 1671683897462}

// ?id=2279
// <div class="menuItem"><img src='share.png'/><span>Recent Folder Title</span></div>

function createItemDiv(node) {

    const div = document.createElement("div");
    div.classList.add("menuItem");

    const img = document.createElement("img");
    img.src = 'share.png';

    const span = document.createElement("span");
    span.innerHTML = node.title;
    div.append(img, span);

    div.onclick = genClosure("chrome://bookmarks/?id=" + node.id);

    return div;
}

// <!-- <div class="menuItem"><img src='share.png'/><span>View Title</span></div> -->
function createViewDiv(viewNumber) {

    const view = localStorage.getItem("view-" + viewNumber.toString());
    const viewRe = new RegExp(':view-title(.+?),');
    // If view does not exist in local storage do not try to parse the null
    if (view) {
	try {var extractTitle = viewRe.exec(view)[1].trim().replace(/"/g,'');}
	catch(e) { //regexp fail due to malformed edn
	    localStorage.setItem("view-" + viewNumber.toString(),
			     '{:view-title ' + '"VIEW-' + viewNumber.toString() + '"' + ', :open-folder-dimensions ()}');
	    var extractTitle = "VIEW-" + viewNumber.toString();}
	
    } else { //else view does not exist
	localStorage.setItem("view-" + viewNumber.toString(),
			     '{:view-title ' + '"VIEW-' + viewNumber.toString() + '"' + ', :open-folder-dimensions ()}');
	var extractTitle = "VIEW-" + viewNumber.toString();
    }
    
    const div = document.createElement("div");
    div.classList.add("menuItem");

    const img = document.createElement("img");
    img.src = 'share.png';

    const span = document.createElement("span");
    span.innerHTML = extractTitle;
    div.append(img, span);

    div.onclick = genClosure("chrome://bookmarks/?view=" + viewNumber.toString());

    return div;
}

function repopulateMenuContainer(allFolderNodes) {
    const runAppDiv = createRunAppElement();
    const recentlyModifiedHeader = createHeaderDiv("Recently Modified")
    const recentlyModifiedArray = allFolderNodes.map(createItemDiv)
    const viewsHeader = createHeaderDiv("Views")
    var viewElementArray = [];
    for (let i of [1, 2, 3, 4, 5, 6]) viewElementArray = viewElementArray.concat(createViewDiv(i));
    
    
    document.querySelector("div.menuContainer").append(runAppDiv, recentlyModifiedHeader, ...recentlyModifiedArray,
						       viewsHeader, ...viewElementArray);

    console.log(recentlyModifiedArray);
    
}



function createRunAppElement() {

    const div = document.createElement("div");
    const img = document.createElement("img");
    const h3 = document.createElement("h3");

    div.classList.add("menuItem", "runApp");
    img.src = 'dragon.png';
    h3.innerHTML = "Bookmark Manager";


    div.appendChild(img);
    div.appendChild(h3);
    div.onclick = genClosure("webpage/index.html");

    return div;

    
}

function createHeaderDiv (innerHTML){
    // <div class="menuHeader"><h3><span>Recently Modified</span></h3></div>
    const div = document.createElement("div");
    div.classList.add("menuHeader");
    const h3 = document.createElement("h3");
    const span = document.createElement("span");
    span.innerHTML = innerHTML
    div.appendChild(h3).appendChild(span);
    return div;
}

function genClosure (url) {
    return function () {
	var newURL = url;
	chrome.tabs.create({ url: newURL });
    }
}


function startup() {
    // callback: fetchRecentlyModified -> repopulateMenuContainer
    chrome.bookmarks.getTree((item) => fetchRecentlyModified(item));

    
}

startup();





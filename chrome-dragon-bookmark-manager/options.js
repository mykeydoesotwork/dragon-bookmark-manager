'use strict';

// Global vars
var defaultEmbeddedMenuConfiguration =
    [{optionClass: "tabOption"    , show: true,  startCollapsed: false,  defaultColumns:4, minRows: 1, maxRows: 9},
     {optionClass: "historyOption", show: true,  startCollapsed: false, defaultColumns:4, minRows: 1,  maxRows: 9},
     {optionClass: "barOption"    , show: true,  startCollapsed: false,  defaultColumns:5, minRows: 1,  maxRows: 9},
     {optionClass: "otherOption"  , show: true,  startCollapsed: false, defaultColumns:5, minRows: 1,  maxRows: 9}];


var updateNotification = document.getElementById("updateNotification");
var loadMoreCutoffElement = document.getElementById("loadMoreCutoff");
var themeSliderElement = document.getElementById("themeSlider")

var helpDialogObj = document.getElementById ("helpDialog");
var resetDialogObj = document.getElementById ("resetDialog");
var resetDialogContents = document.getElementById ("resetDialogContents");
var helpDialogContents = document.getElementById ("helpDialogContents");

function displayUpdateNotification() {
    updateNotification.style.visibility="visible";
    var updateNotificationColors = ["red", "green", "blue"];
    updateNotification.style.color = updateNotificationColors[(updateNotificationColors.indexOf(updateNotification.style.color) + 1) % 3];
}

function startup() {

    // =============== Setup Light Dark Mode:
    var storedTheme = localStorage.getItem('theme');
    if(storedTheme === 'true' || storedTheme === 'false') {
	if (storedTheme === 'true'){
	    themeSliderElement.checked = true;
	} else if (storedTheme === 'false') {
	    themeSliderElement.checked = false;
	}
    }
    // localstorage invalid, reset local storage, and element
    else {
	localStorage.setItem('theme', false);
	themeSliderElement.checked = false;
    }    
    
    // =============== Setup Load More Cutoff:
    // if there is something wrong with the stored value, reset it to 100, then set the displayed inputbox value to 100
    // otherwise, just load the stored value into load more cutoff input box
    // !storedDefaultCutoff checks for empty string
    var storedDefaultCutoff = localStorage.getItem('defaultcutoff');
    if(isNaN(storedDefaultCutoff) || !storedDefaultCutoff || (parseInt(storedDefaultCutoff) < 1) || 999 < parseInt(storedDefaultCutoff)) {
	localStorage.setItem('defaultcutoff', 100);
	loadMoreCutoffElement.value = 100;
    }
    else {
	localStorage.setItem('defaultcutoff', parseInt(storedDefaultCutoff)); // parseInt to clobber floats
	loadMoreCutoffElement.value = parseInt(storedDefaultCutoff); 
    }


    // =============== Setup EmbeddedMenu Configuration:
    try {
	var embeddedMenuConfigurationString = localStorage.getItem('embeddedMenuConfiguration');
	var configArray = JSON.parse(embeddedMenuConfigurationString);
	if (configArray === null) {
	    throw new Error("configArray = JSON.parse(embeddedMenuConfigurationString) returned null");
	}
    }
    catch (err){
	console.log("Due to an JSON.parse error of localStorage.getItem('embeddedMenuConfiguration'), the default options were restored: "
		    + err.message)
	localStorage.setItem('embeddedMenuConfiguration', JSON.stringify(defaultEmbeddedMenuConfiguration));
	var embeddedMenuConfigurationString = localStorage.getItem('embeddedMenuConfiguration');
	var configArray = defaultEmbeddedMenuConfiguration;
    }

    
    // attach all event listeners
    loadMoreCutoffElement.addEventListener("input", inputLoadMoreCutoff);
    themeSliderElement.addEventListener("input", inputSlider);

    document.querySelector("#show.barOption").addEventListener("input", inputCheckBox);
    document.querySelector("#show.otherOption").addEventListener("input", inputCheckBox);

    document.querySelector("#startCollapsed.tabOption").addEventListener("input", inputCheckBox);
    document.querySelector("#startCollapsed.barOption").addEventListener("input", inputCheckBox);
    document.querySelector("#startCollapsed.otherOption").addEventListener("input", inputCheckBox);

    document.querySelector("#defaultColumns.tabOption").addEventListener("input", inputNumber);
    document.querySelector("#defaultColumns.barOption").addEventListener("input", inputNumber);
    document.querySelector("#defaultColumns.otherOption").addEventListener("input", inputNumber);

    for (let i = 0; i < 4; i++) {
	document.querySelector("#defaultColumns." + configArray[i].optionClass).addEventListener("input", inputNumber);
	document.querySelector("#minRows." + configArray[i].optionClass).addEventListener("input", inputNumber);
	document.querySelector("#maxRows." + configArray[i].optionClass).addEventListener("input", inputNumber);
    }

    for (let helpButton of document.querySelectorAll(".helpButton"))
    {
	helpButton.addEventListener("click", helpButtonHandler);
    }

    for (let resetButton of document.querySelectorAll(".resetButton"))
    {
	resetButton.addEventListener("click", resetButtonHandler);
    }

    resetDialogObj.addEventListener('close', resetDialogObjCloseHandler);

    // Setup Embedded Menu Configuration: 
    setupValidEmbeddedMenu(embeddedMenuConfigurationString);
    

}


function setupValidEmbeddedMenu (embeddedMenuConfigurationString) {

    try {
	var configArray = JSON.parse(embeddedMenuConfigurationString);
    }
    catch (err){
	console.log("Due to a JSON.parse error of localStorage.getItem('embeddedMenuConfiguration'), the default options were restored: "
		    + err.message)
	localStorage.setItem('embeddedMenuConfiguration', JSON.stringify(defaultEmbeddedMenuConfiguration));
	loadArrayIntoUI(defaultEmbeddedMenuConfiguration);
	return;
    }

    if (validateEmbeddedMenuConfiguration(configArray)) {
	loadArrayIntoUI(configArray);
    }
    else {
	localStorage.setItem('embeddedMenuConfiguration', JSON.stringify(defaultEmbeddedMenuConfiguration));
	loadArrayIntoUI(defaultEmbeddedMenuConfiguration);
    }

}

function validateEmbeddedMenuConfiguration(configArray) {

    var arrayCheck = Array.isArray(configArray);
    var optionNameCheck = configArray.every( (val, index) => (val.optionClass === ["tabOption", "historyOption", "barOption", "otherOption"][index]));
    
    var lengthCheck = configArray.length === 4;
    var booleanCheckShow = configArray.every( (val) => (typeof(val.show)==='boolean' ));
    var booleanCheckCollapse = configArray.every( (val) => (typeof(val.startCollapsed)==='boolean' ));

    var numberCheckCols = configArray.every( (val) => (typeof(val.defaultColumns)==='number' && val.defaultColumns > 0
						       && Number.isInteger(val.defaultColumns) ));

    var numberCheckMinRows = configArray.every( (val) => (typeof(val.minRows)==='number' && val.minRows > 0 && Number.isInteger(val.minRows)));
    var numberCheckMaxRows = configArray.every( (val) => (typeof(val.maxRows)==='number' && val.maxRows > 0 && Number.isInteger(val.maxRows)));

    if (arrayCheck && optionNameCheck && lengthCheck && booleanCheckShow && booleanCheckCollapse && numberCheckCols && numberCheckMinRows
	           && numberCheckMaxRows)
    { return true; }
    else { return false; }

}

function loadArrayIntoUI(configArray) {
	for (let i = 0; i < 4; i++) {
	    // only i=2 and i=3 rows of configArray: bookmarks bar "barOption", and other bookmarks "otherOption", are hideable.
	    if (i>1) {
		document.querySelector("#show." + configArray[i].optionClass).checked = configArray[i].show;
	    }
	    // exclude History: Start Collapsed? and Default Columns in 2nd row i == 1, this is set with Tabs: Start Collapsed? directly above it.
	    if (i !== 1) {
		document.querySelector("#startCollapsed." + configArray[i].optionClass).checked = configArray[i].startCollapsed;
	    }
	    document.querySelector("#defaultColumns." + configArray[i].optionClass).value = configArray[i].defaultColumns;
	    document.querySelector("#minRows." + configArray[i].optionClass).value = configArray[i].minRows;
	    document.querySelector("#maxRows." + configArray[i].optionClass).value = configArray[i].maxRows;
	}
}


function inputSlider(event) {

    if(themeSliderElement.checked === true || themeSliderElement.checked === false) {
	localStorage.setItem('theme', themeSliderElement.checked);
	displayUpdateNotification();
    }
    // themeSliderElement.checked is invalid, reset localstorage and slider to false
    else {
	localStorage.setItem('theme', false);
	themeSliderElement.checked = false;
    }    
}

function inputCheckBox(event) {

    try {
	var configArray = JSON.parse(localStorage.getItem('embeddedMenuConfiguration'));
    }
    catch (err){
	console.log("Due to an JSON.parse error of localStorage.getItem('embeddedMenuConfiguration'), the default options were restored: "
		    + err.message)
	localStorage.setItem('embeddedMenuConfiguration', JSON.stringify(defaultEmbeddedMenuConfiguration));
	loadArrayIntoUI(defaultEmbeddedMenuConfiguration);
	return;
    }

    // find the originating element class, if unknown abort, if valid then:
    // validate the input: if invalid abort, if valid then:
    // update the configarray, if invalid abort, if valid then:
    // save the configarray
    console.log("You checked checkbox: with className: " + event.target.className + " and id: " + event.target.id)
    var findClassNameIndex = configArray.findIndex(x => (x.optionClass === event.target.className) );

    if (!(findClassNameIndex === -1)) {
	if(event.target.checked === true || event.target.checked === false) {
	    configArray[findClassNameIndex][event.target.id] = event.target.checked;
	    if(validateEmbeddedMenuConfiguration(configArray)) {
		localStorage.setItem('embeddedMenuConfiguration', JSON.stringify(configArray));
		displayUpdateNotification();
	    }
	}
    }
}
    




function inputNumber(event) {

    try {
	var configArray = JSON.parse(localStorage.getItem('embeddedMenuConfiguration'));
    }
    catch (err){
	console.log("Due to an JSON.parse error of localStorage.getItem('embeddedMenuConfiguration'), the default options were restored: "
		    + err.message)
	localStorage.setItem('embeddedMenuConfiguration', JSON.stringify(defaultEmbeddedMenuConfiguration));
	loadArrayIntoUI(defaultEmbeddedMenuConfiguration);
	return;
    }

    // find the originating element class, if unknown abort, if valid then:
    // validate the input: if invalid abort, if valid then:
    // update the configarray, if invalid abort, if valid then:
    // save the configarray
    var findClassNameIndex = configArray.findIndex(x => (x.optionClass === event.target.className) );

    if (!(findClassNameIndex === -1)) {
	var parsedInputNumber = parseInt(event.target.value);
	// !parsedInputNumber checks for empty string; !"" => is true
	if (!(isNaN(parsedInputNumber) || !parsedInputNumber || (parseInt(parsedInputNumber) < event.target.min)
	      || event.target.max < parseInt(parsedInputNumber))) {
	    configArray[findClassNameIndex][event.target.id] = parsedInputNumber;
	    if(validateEmbeddedMenuConfiguration(configArray)) {
		localStorage.setItem('embeddedMenuConfiguration', JSON.stringify(configArray));
		event.target.value = configArray[findClassNameIndex][event.target.id]; // clobber any floats which parseInt will destroy
		displayUpdateNotification();
	    }
	} else
	{
	    event.target.value = configArray[findClassNameIndex][event.target.id];
	}
    }

}


function inputLoadMoreCutoff() {
    // !loadMoreCutoffElement.value checks for empty string
    if(isNaN(loadMoreCutoffElement.value) || !loadMoreCutoffElement.value || (parseInt(loadMoreCutoffElement.value) < 1)
       || 999 < parseInt(loadMoreCutoffElement.value))
    {
	loadMoreCutoffElement.value = localStorage.getItem('defaultcutoff');
	var savevalue =  localStorage.getItem('defaultcutoff');
    }
    else {var savevalue =  loadMoreCutoffElement.value};
    
    localStorage.setItem('defaultcutoff', String(parseInt(savevalue)));
    loadMoreCutoffElement.value = parseInt(savevalue); // clobber any floats which parseInt will destroy
    displayUpdateNotification();
}

// show the dialog box, and if confirmed reset to default, if canceled do nothing
function resetButtonHandler (event)
{
    resetDialogContents.innerText = event.target.title;
    resetDialogObj.showModal();
    resetDialogObj.dataDefaultField = event.target.dataset.default

}

// show the dialog box, only option is ok, then do nothing
function helpButtonHandler (event)
{
 helpDialogContents.innerText = event.target.title;
 helpDialogObj.showModal();
 }



function resetDialogObjCloseHandler() {    
    if (resetDialogObj.returnValue === "confirm") {

	displayUpdateNotification();
	
	if (resetDialogObj.dataDefaultField === "storedTheme") {
	    localStorage.setItem('theme', false);
	    themeSliderElement.checked = false;
	} else if (resetDialogObj.dataDefaultField === "storedDefaultCutoff") {
	    localStorage.setItem('defaultcutoff', 100);
	    loadMoreCutoffElement.value = 100;
	} else if (resetDialogObj.dataDefaultField === "resetAll") {
	    // reset theme
	    localStorage.setItem('theme', false);
	    themeSliderElement.checked = false;

	    // reset loadmorecutoff
	    localStorage.setItem('defaultcutoff', 100);
	    loadMoreCutoffElement.value = 100;

	    // set localstorage to default array
	    localStorage.setItem('embeddedMenuConfiguration', JSON.stringify(defaultEmbeddedMenuConfiguration));
	    var embeddedMenuConfigurationString = localStorage.getItem('embeddedMenuConfiguration');
	    setupValidEmbeddedMenu(embeddedMenuConfigurationString);
	    
	} else {
	    var optionClassAndFieldArray =  resetDialogObj.dataDefaultField.split (".");
	    try {
		var embeddedMenuConfigurationString = localStorage.getItem('embeddedMenuConfiguration');
		var configArray = JSON.parse(embeddedMenuConfigurationString);
		if (configArray === null)
		{
		    throw new Error("configArray = JSON.parse(embeddedMenuConfigurationString) returned null");
		}
	    }
	    catch (err) {
		console.log("Due to an JSON.parse error of localStorage.getItem('embeddedMenuConfiguration'), the default options were restored: "
			    + err.message)
		localStorage.setItem('embeddedMenuConfiguration', JSON.stringify(defaultEmbeddedMenuConfiguration));
		var embeddedMenuConfigurationString = localStorage.getItem('embeddedMenuConfiguration');
		var configArray = defaultEmbeddedMenuConfiguration;
	    }

	    var findClassNameIndex = configArray.findIndex(x => (x.optionClass === optionClassAndFieldArray[0]) );
	    
	    configArray[findClassNameIndex][optionClassAndFieldArray[1]] =
		defaultEmbeddedMenuConfiguration[findClassNameIndex][optionClassAndFieldArray[1]];
	    localStorage.setItem('embeddedMenuConfiguration', JSON.stringify(configArray));
	    loadArrayIntoUI(configArray);
	}
    }
}


startup();


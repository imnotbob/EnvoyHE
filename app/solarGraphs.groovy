/************************************************************************************************
|	Application Name: Solar Graphs								|
|	Copyright (C) 2019									|
|	Authors: Eric S. (@E_sch)								|
| Modified April 27, 2020									|
|************************************************************************************************/

import groovy.json.*
import java.text.SimpleDateFormat
import groovy.time.*
import groovy.transform.Field

definition(
	name: "Solar Graphs",
	namespace: "E_Sch",
	author: "Eric S.",
	description: "This App is used to display device graphs for Envoy Solar",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
	oauth: true)

static String appVersion() { "0.0.3" }

preferences {
	page(name: "startPage")
	page(name: "mainAutoPage")
}

mappings {
	path("/deviceTiles")	{action: [GET: "renderDeviceTiles"]}
}

def startPage() {
	log.info "startPage"
	if(!state?.access_token) { Boolean a=getAccessToken() }
	if(!state?.access_token) { enableOauth(); Boolean a=getAccessToken() }
	mainAutoPage()
}

def mainAutoPage() {
	if(!state?.autoDisabled) { state.autoDisabled = false }

		return dynamicPage(name: "mainAutoPage", title: pageTitleStr("Automation Configuration"), uninstall: true, install: true, nextPage:null ) {
			section() {
				if(settings?.autoDisabledreq) {
					paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("This Automation is currently disabled!\nTurn it back on to to make changes or resume operation")), required: true, state: null
				} else {
					if(getIsAutomationDisabled()) { paragraph imgTitle(getAppImg("i_inst"), paraTitleStr("This Automation is still disabled!\nPress Next and Done to Activate this Automation Again")), state: "complete" }
				}
				if(!getIsAutomationDisabled()) {
					input "energyDevice", "capability.energyMeter", title: imgTitle(getAppImg("lightning.png"), inputTitleStr("Envoy Device?")), required: true, multiple: false, submitOnChange: true
					if(energyDevice) {
						String myUrl = getAppEndpointUrl("deviceTiles")
						String myLUrl = getLocalEndpointUrl("deviceTiles")
						String myStr = """ Graph Url: <a href="${myUrl}" target="_blank">${energyDevice.label}</a>  <a href="${myLUrl}" target="_blank">(local)</a> """
						paragraph imgTitle(getAppImg("graph_icon.png"), paraTitleStr(myStr))
					}
				}
			}
			section(sectionTitleStr("Automation Options:")) {
				input "autoDisabledreq", "bool", title: imgTitle(getAppImg("disable_icon2.png"), inputTitleStr("Disable this Automation?")), required: false, defaultValue: false /* state?.autoDisabled */, submitOnChange: true
				setAutomationStatus()

				input("showDebug", "bool", title: imgTitle(getAppImg("debug_icon.png"), inputTitleStr("Debug Option")), description: "Show ${app?.name} Logs in the IDE?", required: false, defaultValue: false, submitOnChange: true)
				if(showDebug) {
					input("advAppDebug", "bool", title: imgTitle(getAppImg("list_icon.png"), inputTitleStr("Show Verbose Logs?")), required: false, defaultValue: false, submitOnChange: true)
				} else {
					settingUpdate("advAppDebug", "false", "bool")
				}
			}
			section(paraTitleStr("Automation Name:")) {
				def newName = getAutoTypeLabel()
				if(!app?.label) { app?.updateLabel("${newName}") }
				label title: imgTitle(getAppImg("name_tag_icon.png"), inputTitleStr("Label this Automation: Suggested Name: ${newName}")), defaultValue: "${newName}", required: true //, wordWrap: true
				if(!state?.isInstalled) {
					paragraph "Make sure to name it something that you can easily recognize."
				}
			}
		}
}

Boolean isHubitat(){
	return hubUID != null
}

void installed() {
	log.debug "${app.getLabel()} Installed with settings: ${settings}"		// MUST BE log.debug
	if(isHubitat() && !app.id) return
	initialize()
}

void updated() {
	log.debug "${app.getLabel()} Updated...with settings: ${settings}"
	state?.isInstalled = true
	state?.lastUpdatedDt = getDtNow()
	initialize()
}

void uninstalled() {
	log.debug "uninstalled"
	uninstAutomationApp()
}

void initialize() {
	log.debug "${app.label} Initialize..."		// Must be log.debug
	if(!state?.autoTyp) { state.autoTyp = "chart" }
	settingUpdate("showDebug", "true", "bool")
	settingUpdate("advAppDebug", "true", "bool")
	resetVars()
	if(!state?.isInstalled) { state?.isInstalled = true }

	runIn(6, "initAutoApp", [overwrite: true])
}

void resetVars() {
	stateRemove("evalSched")
	stateRemove("autoRunInSchedDt")
	stateRemove("detailEventHistory")
	stateRemove("detailExecutionHistory")
	if(state?.eric) {
		state.eric = false
//		state.powerTable = [[7, 14, 8], [7, 59, 70], [8, 14, 128], [8, 29, 145], [8, 44, 167], [8, 59, 254], [9, 14, 418], [9, 29, 627], [9, 44, 858], [9, 59, 1106], [10, 14, 1355], [10, 29, 1589], [10, 44, 1803], [10, 59, 1996], [11, 14, 2168], [11, 29, 2328], [11, 44, 2451], [11, 59, 2545], [12, 14, 2648], [12, 29, 2736], [12, 44, 2813], [12, 59, 2874], [13, 14, 2942], [13, 29, 3004], [13, 44, 3004], [13, 59, 2902], [14, 14, 2922], [14, 29, 2885], [14, 44, 2762], [14, 59, 2661], [15, 14, 2562], [15, 29, 2444], [15, 44, 2290], [15, 59, 2051], [16, 14, 1647], [16, 29, 1085], [16, 44, 288], [16, 59, 97], [17, 59, 0]]
//		state.powerTableYesterday = [[7, 9, 4], [7, 54, 61], [8, 9, 101], [8, 24, 134], [8, 39, 174], [8, 54, 228], [9, 9, 396], [9, 24, 610], [9, 39, 912], [9, 54, 1114], [10, 9, 1146], [10, 24, 1478], [10, 39, 1644], [10, 54, 1824], [11, 9, 2046], [11, 24, 1933], [11, 39, 2183], [11, 54, 2442], [12, 9, 1908], [12, 24, 2058], [12, 39, 2641], [12, 54, 2732], [13, 9, 2553], [13, 24, 2546], [13, 39, 3114], [13, 54, 2831], [14, 9, 2752], [14, 24, 2432], [14, 39, 2398], [14, 54, 2052], [15, 9, 1311], [15, 24, 1080], [15, 39, 797], [15, 54, 1152], [16, 9, 1793], [16, 24, 1055], [16, 39, 382], [16, 54, 103], [17, 9, 48], [18, 9, 0]]
//		state.energyTable = [[7, 14, 0.001], [7, 59, 0.054], [8, 14, 0.085], [8, 29, 0.121], [8, 44, 0.161], [8, 59, 0.223], [9, 14, 0.324], [9, 29, 0.476], [9, 44, 0.685], [9, 59, 0.951], [10, 14, 1.278], [10, 29, 1.661], [10, 44, 2.096], [10, 59, 2.577], [11, 14, 3.1], [11, 29, 3.661], [11, 44, 4.257], [11, 59, 4.882], [12, 14, 5.533], [12, 29, 6.205], [12, 44, 6.896], [12, 59, 7.602], [13, 14, 8.322], [13, 29, 9.059], [13, 44, 9.798], [13, 59, 10.512], [14, 14, 11.229], [14, 29, 11.939], [14, 44, 12.619], [14, 59, 13.273], [15, 14, 13.902], [15, 29, 14.497], [15, 44, 15.051], [15, 59, 15.547], [16, 14, 15.944], [16, 29, 16.206], [16, 44, 16.276], [16, 59, 16.3], [17, 59, 16.3]]
//		state.energyTableYesterday = [[7, 9, 0.001], [7, 54, 0.045], [8, 9, 0.069], [8, 24, 0.101], [8, 39, 0.144], [8, 54, 0.199], [9, 9, 0.296], [9, 24, 0.444], [9, 39, 0.665], [9, 54, 0.933], [10, 9, 1.21], [10, 24, 1.568], [10, 39, 1.965], [10, 54, 2.405], [11, 9, 2.9], [11, 24, 3.369], [11, 39, 3.898], [11, 54, 4.495], [12, 9, 4.957], [12, 24, 5.456], [12, 39, 6.101], [12, 54, 6.771], [13, 9, 7.395], [13, 24, 8.018], [13, 39, 8.783], [13, 54, 9.475], [14, 9, 10.151], [14, 24, 10.745], [14, 39, 11.329], [14, 54, 11.827], [15, 9, 12.145], [15, 24, 12.406], [15, 39, 12.599], [15, 54, 12.878], [16, 9, 13.31], [16, 24, 13.566], [16, 39, 13.659], [16, 54, 13.684], [17, 9, 13.686], [18, 9, 13.686]]
//		state.today = "09"
//		state.eric = true
	}
}

def initAutoApp() {
	if(settings["chartFlag"]) {
		state.autoTyp = "chart"
	}
	unschedule()
	unsubscribe()
	setAutomationStatus()

	subscribeToEvents()
	scheduler()

	app.updateLabel(getAutoTypeLabel())
	LogAction("Automation Label: ${getAutoTypeLabel()}", "info", true)

	scheduleAutomationEval(10)
	if(showDebug || advAppDebug) { runIn(1800, logsOff) }

	//revokeAccessToken()

	String devTilesUrl = getAppEndpointUrl("deviceTiles")

	Logger("initAutoApp: devTile: ${devTilesUrl}")
}

void subscribeToEvents() {
	if(settings?.energyDevice) {
		subscribe(energyDevice, "energy", automationGenericEvt)
		subscribe(energyDevice, "power", automationGenericEvt)
	}
}

void scheduler() {
}

void uninstAutomationApp() {
}

static String strCapitalize(String str) {
	return str ? str?.capitalize() : (String)null
}

void automationGenericEvt(evt) {
	Long startTime = now()
	Long eventDelay = startTime - evt.date.getTime()
	LogTrace("${evt.name.toUpperCase()} Event | Device: ${evt?.displayName} | Value: (${strCapitalize(evt?.value)}) with a delay of ${eventDelay}ms".toString())

	doTheEvent(evt)
}

void doTheEvent(evt) {
	if(getIsAutomationDisabled()) { return }
	else {
		scheduleAutomationEval()
		storeLastEventData(evt)
	}
}

void storeLastEventData(evt) {
	if(evt) {
		Map newVal = ["name":evt.name, "displayName":evt.displayName, "value":evt.value, "date":formatDt((Date)evt.date), "unit":evt.unit]
		state?.lastEventData = newVal
		//log.debug "LastEvent: ${state?.lastEventData}"

		List list = state?.detailEventHistory ?: []
		Integer listSize = 15
		if(list?.size() < listSize) {
			Boolean a=list.push(newVal)
		}
		else if(list?.size() > listSize) {
			Integer nSz = (list?.size()-listSize) + 1
			List nList = list?.drop(nSz)
			Boolean a=nList?.push(newVal)
			list = nList
		}
		else if(list?.size() == listSize) {
			List nList = list?.drop(1)
			Boolean a=nList?.push(newVal)
			list = nList
		}
		if(list) { state.detailEventHistory = list }
	}
}

def storeExecutionHistory(val, String method = (String)null) {
	//log.debug "storeExecutionHistory($val, $method)"
	if(method) {
		LogAction("${method} Execution Time: (${val} milliseconds)", "trace", false)
	}
	List list = state?.detailExecutionHistory ?: []
	Integer listSize = 30
	list = addToList([val, method, getDtNow()], list, listSize)
	if(list) { state?.detailExecutionHistory = list }
}

static List addToList(val, List list, Integer listSize) {
	if(list?.size() < listSize) {
		Boolean a=list.push(val)
	} else if(list?.size() > listSize) {
		Integer nSz = (list?.size()-listSize) + 1
		List nList = list?.drop(nSz)
		Boolean a=nList?.push(val)
		list = nList
	} else if(list?.size() == listSize) {
		List nList = list?.drop(1)
		Boolean a=nList?.push(val)
		list = nList
	}
	return list
}

void setAutomationStatus(Boolean upd=false) {
	Boolean myDis = (settings?.autoDisabledreq == true)
	if(!getIsAutomationDisabled() && myDis) {
		LogAction("Automation Disabled at (${getDtNow()})", "info", true)
		state?.autoDisabledDt = getDtNow()
	} else if(getIsAutomationDisabled() && !myDis) {
		LogAction("Automation Enabled at (${getDtNow()})", "info", true)
		state?.autoDisabledDt = null
	}
	state?.autoDisabled = myDis
	if(upd) { app.update() }
}

static Integer defaultAutomationTime() {
	return 5
}

void scheduleAutomationEval(Integer schedtime = defaultAutomationTime()) {
	Integer theTime = schedtime
	if(theTime < defaultAutomationTime()) { theTime = defaultAutomationTime() }
	String autoType = getAutoType()
	def random = new Random()
	Integer random_int = random.nextInt(6) // this randomizes a bunch of automations firing at same time off same event
	Boolean waitOverride = false
	switch(autoType) {
		case "chart":
			if(theTime == defaultAutomationTime()) {
				theTime += random_int
			}
			Integer schWaitVal = settings?.schMotWaitVal?.toInteger() ?: 60
			if(schWaitVal > 120) { schWaitVal = 120 }
			Integer t0 = getAutoRunSec()
			if((schWaitVal - t0) >= theTime ) {
				theTime = (schWaitVal - t0)
				waitOverride = true
			}
			//theTime = Math.min( Math.max(theTime,defaultAutomationTime()), 120)
			break
	}
	if(!state.evalSched) {
		runIn(theTime, "runAutomationEval", [overwrite: true])
		state.autoRunInSchedDt = getDtNow()
		state.evalSched = true
		state.evalSchedLastTime = theTime
	} else {
		String str = "scheduleAutomationEval: "
		Integer t0 = state.evalSchedLastTime
		if(t0 == null) { t0 = 0 }
		Integer timeLeftPrev = t0 - getAutoRunInSec()
		if(timeLeftPrev < 0) { timeLeftPrev = 100 }
		String str1 = " Schedule change: from (${timeLeftPrev}sec) to (${theTime}sec)"
		if(timeLeftPrev > (theTime + 5) || waitOverride) {
			if(Math.abs(timeLeftPrev - theTime) > 3) {
				runIn(theTime, "runAutomationEval", [overwrite: true])
				LogTrace(str+'Performing'+str1)
				state.autoRunInSchedDt = getDtNow()
				state.evalSched = true
				state.evalSchedLastTime = theTime
			}
		} else { LogTrace(str+'Skipping'+str1) }
	}
}

Integer getAutoRunSec() { return !state.autoRunDt ? 100000 : GetTimeDiffSeconds((String)state.autoRunDt, null, "getAutoRunSec").toInteger() }

Integer getAutoRunInSec() { return !state.autoRunInSchedDt ? 100000 : GetTimeDiffSeconds((String)state.autoRunInSchedDt, null, "getAutoRunInSec").toInteger() }

void runAutomationEval() {
	LogTrace("runAutomationEval")
	Long execTime = now()
	String autoType = getAutoType()
	state.evalSched = false
	state.evalSchedLastTime = null
	state.autoRunInSchedDt = null
	switch(autoType) {
		case "chart":
			if(settings.energyDevice) {
				getSomeData(settings.energyDevice)
			}

			break
		default:
			LogAction("runAutomationEval: Invalid Option Received ${autoType}", "warn", true)
			break
	}
	storeExecutionHistory((now()-execTime), "runAutomationEval")
}

String getCurAppLbl() { return (String)app.label }

static String appLabel()	{ return "Solar Graphs" }
static String appName()		{ return appLabel() }

String getAutoTypeLabel() {
	//LogTrace("getAutoTypeLabel()")
	String type = state?.autoTyp
	String appLbl = getCurAppLbl()
	String newName = appName() == appLabel() ? "NST Graphs" : appName()
	String typeLabel = ""
	def newLbl
	String dis = getIsAutomationDisabled() ? "\n(Disabled)" : ""

	typeLabel = "Solar Location ${location.name} Graphs"

//Logger("getAutoTypeLabel: ${type} ${appLbl} ${appName()} ${appLabel()} ${typeLabel}")

	if(appLbl != "" && appLbl && appLbl != "Solar Graphs" && appLbl != "${appLabel()}") {
		if(appLbl?.contains("\n(Disabled)")) {
			newLbl = appLbl?.replaceAll('\\\n\\(Disabled\\)', '')
		} else {
			newLbl = appLbl
		}
	} else {
		newLbl = typeLabel
	}
	return newLbl+dis
}

//ERS
void checkCleanups() {
	def inuse = []
	theDev = settings?.energyDevice
	if(theDev) {
		inuse += theDev.id
	}

	def data = []
	def regex1 = /Wtoday/
	["Wtoday"]?.each { oi->
		state?.each { if(it.key.toString().startsWith(oi)) {
				data?.push(it.key.replaceAll(regex1, ""))
			} 
		}
	}
	def regex2 = /thermStor/
	["thermStor"]?.each { oi->
		state?.each { if(it.key.toString().startsWith(oi)) {
				data?.push(it.key.replaceAll(regex2, ""))
			} 
		}
	}

	//Logger("data is ${data}")
	def toDelete = data.findAll { !inuse.contains(it) }
	//Logger("toDelete is ${toDelete}")

	toDelete?.each { item ->
		cleanState(item.toString())
	}
}

void cleanState(id) {
LogTrace("cleanState: ${id}")
	stateRemove("Wtoday${id}")
	stateRemove("WhumTblYest${id}")
	stateRemove("WdewTblYest${id}")
	stateRemove("WtempTblYest${id}")
	stateRemove("WhumTbl${id}")
	stateRemove("WdewTbl${id}")
	stateRemove("WtempTbl${id}")

	stateRemove("today${id}")
	stateRemove("thermStor${id}")
	stateRemove("tempTblYest${id}")
	stateRemove("tempTbl${id}")
	stateRemove("oprStTblYest${id}")
	stateRemove("oprStTbl${id}")
	stateRemove("humTblYest${id}")
	stateRemove("humTbl${id}")
	stateRemove("hspTblYest${id}")
	stateRemove("hspTbl${id}")
	stateRemove("cspTblYest${id}")
	stateRemove("cspTbl${id}")
	stateRemove("fanTblYest${id}")
	stateRemove("fanTbl${id}")
}

static String sectionTitleStr(title)	{ return "<h3>$title</h3>" }
static String inputTitleStr(title)	{ return "<u>$title</u>" }
static String pageTitleStr(title)	 { return "<h1>$title</h1>" }
static String paraTitleStr(title)	 { return "<b>$title</b>" }

static String imgTitle(imgSrc, titleStr, color=null, imgWidth=30, imgHeight=null) {
	def imgStyle = ""
	imgStyle += imgWidth ? "width: ${imgWidth}px !important;" : ""
	imgStyle += imgHeight ? "${imgWidth ? " " : ""}height: ${imgHeight}px !important;" : ""
	if(color) { return """<div style="color: ${color}; font-weight: bold;"><img style="${imgStyle}" src="${imgSrc}"> ${titleStr}</img></div>""" }
	else { return """<img style="${imgStyle}" src="${imgSrc}"> ${titleStr}</img>""" }
}

static String icons(String name, String napp="App") {
	def icon_names = [
		"i_dt": "delay_time",
		"i_not": "notification",
		"i_calf": "cal_filter",
		"i_set": "settings",
		"i_sw": "switch_on",
		"i_mod": "mode",
		"i_hmod": "hvac_mode",
		"i_inst": "instruct",
		"i_err": "error",
		"i_cfg": "configure",
		"i_t": "temperature"

//ERS
	]
	String t0 = icon_names?."${name}"
	//LogAction("t0 ${t0}", "warn", true)
	if(t0) return "https://raw.githubusercontent.com/${gitPath()}/Images/$napp/${t0}_icon.png".toString()
	else return "https://raw.githubusercontent.com/${gitPath()}/Images/$napp/${name}".toString()
}

static String gitRepo()		{ return "tonesto7/nest-manager"}
static String gitBranch()		{ return "master" }
static String gitPath()		{ return "${gitRepo()}/${gitBranch()}".toString()}

String getAppImg(imgName, Boolean on=null) {
	return (!disAppIcons || on) ? icons(imgName) : ""
}

String getDevImg(imgName, Boolean on=null) {
	return (!disAppIcons || on) ? icons(imgName, "Devices") : ""
}

void logsOff() {
	log.warn "debug logging disabled..."
	settingUpdate("showDebug", "false", "bool")
	settingUpdate("advAppDebug", "false", "bool")
}

def getSettingsData() {
	def sets = []
	settings?.sort().each { st ->
		sets << st
	}
	return sets
}

def getSettingVal(String var) {
	return settings[var] ?: null
}

def getStateVal(String var) {
	return state[var] ?: null
}

void settingUpdate(String name, value, String type=null) {
	//LogTrace("settingUpdate($name, $value, $type)...")
	if(name) {
		if(value == "" || value == null || value == []) {
			settingRemove(name)
			return
		}
	}
	if(name && type) { app?.updateSetting("$name", [type: "$type", value: value]) }
	else if (name && type == null) { app?.updateSetting(name.toString(), value) }
}

void settingRemove(String name) {
	//LogTrace("settingRemove($name)...")
	if(name) { app?.clearSetting(name.toString()) }
}

def stateUpdate(String key, value) {
	if(key) { state."${key}" = value; return true }
	//else { LogAction("stateUpdate: null key $key $value", "error", true); return false }
}

void stateRemove(key) {
	//if(state?.containsKey(key)) { state.remove(key?.toString()) }
	state.remove(key?.toString())
}

String getAutomationType() {
	return state?.autoTyp ?: null
}

String getAutoType() { return getAutomationType() }

Boolean getIsAutomationDisabled() {
	Boolean dis = state.autoDisabled
	return (dis != null && dis)
}

def renderDeviceTiles(String type=null, theDev=null) {
		String devHtml = ""
		String navHtml = ""
		String scrStr = ""
		def allDevices = []
		if(theDev) {
			allDevices << theDev
		} else {
			allDevices << settings.energyDevice
		}

		def devices = allDevices
		Integer devNum = 1
		def myType = type ?: "Envoy Device"
		devices.sort {it.getLabel()}.each { dev ->
			def navMap = [:]
			Boolean hasHtml = true // (dev?.hasHtml() == true)
			if( ( (dev?.typeName in ["Enlighten Envoy (local)"]) &&
				(hasHtml && !type) || (hasHtml && type && dev?.typeName == type)) ) {
LogTrace("renderDeviceTiles: ${dev.id} ${dev.name} ${theDev?.name}  ${dev.typeName}")
				navMap = ["key":dev?.getLabel(), "items":[]]
				def navItems = navHtmlBuilder(navMap, devNum)
				String myTile = getSDeviceTile(devNum, dev)
				if(navItems?.html) { navHtml += navItems?.html }
				if(navItems?.js) { scrStr += navItems?.js }

				devHtml += """
				<div class="panel panel-primary" style="max-width: 600px; margin: 30 auto; position: relative;">
					<div id="key-item${devNum}" class="panel-heading">
						<h1 class="panel-title panel-title-text">${dev?.getLabel()}</h1>
					</div>
					<div class="panel-body">
						<div style="margin: auto; position: relative;">
							<div>${myTile}</div>
						</div>
					</div>
				</div>
				"""
				devNum = devNum+1
			}
		}

		String html = """
		<html lang="en">
			<head>
				${getWebHeaderHtml(myType, true, true, true, true)}
				<link rel="stylesheet" href="https://cdn.rawgit.com/tonesto7/nest-manager/master/Documents/css/diagpages_new.css">
				<style>
					h1, h2, h3, h4, h5, h6 {
						padding: 20px;
						margin: 4px;
					}
				</style>
			</head>
			<body>
				<button onclick="topFunction()" id="scrollTopBtn" title="Go to top"><i class="fa fa-arrow-up centerText" aria-hidden="true"></i></button>
				<nav id="menu-page" class="pushy pushy-left" data-focus="#nav-key-item1">
					<div class="nav-home-btn centerText"><button id="goHomeBtn" class="btn-link" title="Go Back to Home Page"><i class="fa fa-home centerText" aria-hidden="true"></i> Go Home</button></div>
					<!--Include your navigation here-->
					${navHtml}
				</nav>
				<!-- Site Overlay -->
				<div class="site-overlay"></div>

				<!-- Your Content -->
				<div id="container">
					<div id="top-hdr" class="navbar navbar-default navbar-fixed-top">
						<div class="centerText">
							<div class="row">
								<div class="col-xs-2">
									<div class="left-head-col pull-left">
										<div class="menu-btn-div">
											<div class="hamburger-wrap">
												<button id="menu-button" class="menu-btn hamburger hamburger--collapse hamburger--accessible" title="Menu" type="button">
													<span class="hamburger-box">
														<span class="hamburger-inner"></span>
													</span>
													<!--<span class="hamburger-label">Menu</span>-->
												</button>
											</div>
										</div>
									</div>
								</div>
								<div class="col-xs-8 centerText">
									<h3 class="title-text"><img class="logoIcn" src="https://raw.githubusercontent.com/ahndee/Envoy-ST/master/devicetypes/aamann/enlighten-envoy-local.src/Solar.png"> ${type ?: "Solar Device"}s</img></h3>
								</div>
								<div class="col-xs-2 right-head-col pull-right">
									<button id="rfrshBtn" type="button" class="btn refresh-btn pull-right" title="Refresh Page Content"><i id="rfrshBtnIcn" class="fa fa-refresh" aria-hidden="true"></i></button>
								</div>
							</div>
						</div>
					</div>
					<!-- Page Content -->
					<div id="page-content-wrapper">
						<div class="container">
							<div id="main" class="panel-body">
								${devHtml}
							</div>
						</div>
					</div>
				</div>
				<script>
					\$("body").flowtype({
						minFont: 7,
						maxFont: 10,
						fontRatio: 30
					});
				</script>
				<script src="https://cdn.rawgit.com/tonesto7/nest-manager/master/Documents/js/diagpages.min.js"></script>
				<script>
					\$(document).ready(function() {
						${scrStr}
					});
					\$("#goHomeBtn").click(function() {
						closeNavMenu();
						toggleMenuBtn();
						window.location.replace('${request?.requestSource == "local" ? getLocalEndpointUrl("deviceTiles") : getAppEndpointUrl("deviceTiles")}');
					});
				</script>
			</body>
		</html>
		"""
		render contentType: "text/html", data: html
}

Map navHtmlBuilder(navMap, idNum) {
	Map res = [:]
	String htmlStr = ""
	String jsStr = ""
	if(navMap?.key) {
		htmlStr += """
			<div class="nav-cont-bord-div nav-menu">
			  <div class="nav-cont-div">
				<li class="nav-key-item"><a id="nav-key-item${idNum}">${navMap?.key}<span class="icon"></span></a></li>"""
		jsStr += navJsBuilder("nav-key-item${idNum}".toString(), "key-item${idNum}".toString())
	}
	if(navMap?.items) {
		def nItems = navMap?.items
		nItems?.each {
			htmlStr += """\n<li class="nav-subkey-item"><a id="nav-subitem${idNum}-${it.toString().toLowerCase()}">${it}<span class="icon"></span></a></li>"""
			jsStr += navJsBuilder("nav-subitem${idNum}-${it.toString().toLowerCase()}".toString(), "item${idNum}-${it.toString().toLowerCase()}".toString())
		}
	}
	htmlStr += """\n		</div>
						</div>"""
	res["html"] = htmlStr
	res["js"] = jsStr
	return res
}

static String navJsBuilder(String btnId, String divId) {
	String res = """
			\$("#${btnId}").click(function() {
				\$("html, body").animate({scrollTop: \$("#${divId}").offset().top - hdrHeight - 20},500);
				closeNavMenu();
				toggleMenuBtn();
			});
	"""
	return "\n${res}"
}



String getSDeviceTile(Integer devNum, dev) {
//Logger("W1")
		String updateAvail = !state.updateAvailable ? "" : """<div class="greenAlertBanner">Device Update Available!</div>"""
		String clientBl = state?.clientBl ? """<div class="brightRedAlertBanner">Your Manager client has been blacklisted!\nPlease contact the Nest Manager developer to get the issue resolved!!!</div>""" : ""
//Logger("W2")
		def energyStr = dev.currentState("energy").value
		def efficiencyToday = dev.currentState("efficiency").value

		def energyYesterday = dev.currentState("energy_yesterday").value
		def efficiencyYesterday = dev.currentState("efficiency_yesterday").value

		def energyLast7Days = dev.currentState("energy_last7days").value
		def efficiencyLast7Days = dev.currentState("efficiency_last7days").value

		def energyLife = dev.currentState("energy_life").value
		def efficiencyLifetime = dev.currentState("efficiency_lifetime")?.value
			//<h4 style="font-size: 22px; font-weight: bold; text-align: center; background: #00a1db; color: #f5f5f5;">Event History</h4>
					//<h4 class="bottomBorder"> ${location.name} </h4>

		String mainHtml = """
			<div class="device">
				<div class="container">
					<h4>Solar Conditions</h4>
					<div class="row">
				<style>
					table, th, td {
						border: 1px solid black;
						border-collapse: collapse;
					}
					th, td {
						text-align: left;
					}
				</style>
						<div>
							<table style="width:100%">
							<tr><td><b>Energy Today:</b></td><td>           ${energyStr} kWh <br> </td></tr>
							<tr><td><b>Efficiency Today:</b></td><td>       ${efficiencyToday} kWh/kW  </td></tr>

							<tr><td><b>Energy Yesterday:</b></td><td>          ${energyYesterday} kWh </td></tr>
							<tr><td><b>Efficiency Yesterday:</b></td><td>   ${efficiencyYesterday} kWh/kW  </td></tr>

							<tr><td><b>Energy Last 7 days:</b></td><td>     ${energyLast7Days} kWh </td></tr>
							<tr><td><b>Efficiency Last 7 days:</b></td><td> ${efficiencyLast7Days} kWh/kW  </td></tr>

							<tr><td><b>Lifetime Energy:</b></td><td>        ${energyLife} MWh </td></tr>
							<tr><td><b>Lifetime Efficiency:</b></td><td>    ${efficiencyLifetime} kWh/kW  </td></tr>
							</table>
						</div>
					</div>

					${historyGraphHtml(devNum,dev)}

				</div>
			</div>

		"""
//		render contentType: "text/html", data: mainHtml, status: 200
}

String historyGraphHtml(Integer devNum, dev) {
//Logger("HistoryG 1")
	String html = ""
	if(true) {
//Logger("HistoryG 2")
			html = """

					<script type="text/javascript">
						google.charts.load('current', {packages: ['corechart']});
						google.charts.setOnLoadCallback(drawGraph);
						function drawGraph() {
							var data = new google.visualization.DataTable();
							data.addColumn('timeofday', 'time');
							data.addColumn('number', 'Energy (Yesterday)');
							data.addColumn('number', 'Power (Yesterday)');
							data.addColumn('number', 'Energy (Today)');
							data.addColumn('number', 'Power (Today)');
							data.addRows([
								${getDataString(1)}
								${getDataString(2)}
								${getDataString(3)}
								${getDataString(4)}
							]);
							var options = {
								fontName: 'San Francisco, Roboto, Arial',
								height: 240,
								hAxis: {
									format: 'H:mm',
									minValue: [${getStartTime("powerTable", "powerTableYesterday")},0,0],
									slantedText: false
								},
								series: {
									0: {targetAxisIndex: 1, color: '#FFC2C2', lineWidth: 1},
									1: {targetAxisIndex: 0, color: '#D1DFFF', lineWidth: 1},
									2: {targetAxisIndex: 1, color: '#FF0000'},
									3: {targetAxisIndex: 0, color: '#004CFF'}
								},
								vAxes: {
									0: {
										title: 'Power (W)',
										format: 'decimal',
										textStyle: {color: '#004CFF'},
										titleTextStyle: {color: '#004CFF'},
										viewWindow: {min: 0}
									},
									1: {
										title: 'Energy (kWh)',
										format: 'decimal',
										textStyle: {color: '#FF0000'},
										titleTextStyle: {color: '#FF0000'},
										viewWindow: {min: 0},
										gridlines: {count: 0}
									}
								},
								legend: {
									position: 'none'
								},
								chartArea: {
									width: '72%',
									height: '80%'
								}
							};
							var chart = new google.visualization.AreaChart(document.getElementById('chart_div${devNum}'));
							chart.draw(data, options);
						}
					</script>
			<h4 style="font-size: 22px; font-weight: bold; text-align: center; background: #00a1db; color: #f5f5f5;">Event History</h4>
			<div id="chart_div${devNum}" style="width: 100%; height: 225px;"></div>
			"""
	}
}


String getWebHeaderHtml(String title, Boolean clipboard=true, Boolean vex=false, Boolean swiper=false, Boolean charts=false) {
	String html = """
		<meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
		<meta name="description" content="Solar Charts">
		<meta name="author" content="E_Sch">
		<meta http-equiv="cleartype" content="on">
		<meta name="MobileOptimized" content="320">
		<meta name="HandheldFriendly" content="True">
		<meta name="apple-mobile-web-app-capable" content="yes">

		<title>Envoy Solar Charts ('${location.name}') - ${title}</title>

		<script src="https://cdnjs.cloudflare.com/ajax/libs/jquery/3.2.1/jquery.min.js"></script>
		<link href="https://fonts.googleapis.com/css?family=Roboto" rel="stylesheet">
		<script src="https://use.fontawesome.com/fbe6a4efc7.js"></script>
		<script src="https://fastcdn.org/FlowType.JS/1.1/flowtype.js"></script>
		<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/normalize/7.0.0/normalize.min.css">
		<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css" integrity="sha384-BVYiiSIFeK1dGmJRAkycuHAHRg32OmUcww7on3RYdg4Va+PmSTsz/K68vbdEjh4u" crossorigin="anonymous">
		<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap-theme.min.css" integrity="sha384-rHyoN1iRsVXV4nD0JutlnGaslCJuC7uwjduW9SVrLvRYooPp2bWYgmgJQIXwl/Sp" crossorigin="anonymous">
		<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/hamburgers/0.9.1/hamburgers.min.css">
		<script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js" integrity="sha384-Tc5IQib027qvyjSMfHjOMaLkfuWVxZxUPnCJA7l2mCWNIpG9mGCD8wGNIcPD7Txa" crossorigin="anonymous"></script>
		<script type="text/javascript">
			const serverUrl = '${request?.requestSource == "local" ? getLocalApiServerUrl() : apiServerUrl()}';
			const cmdUrl = '${request?.requestSource == "local" ? getLocalEndpointUrl('deviceTiles') : getAppEndpointUrl('deviceTiles')}';
		</script>
	"""
	html += clipboard ? """<script src="https://cdnjs.cloudflare.com/ajax/libs/clipboard.js/1.7.1/clipboard.min.js"></script>""" : ""
	html += vex ? """<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/vex-js/3.1.0/js/vex.combined.min.js"></script>""" : ""
	html += swiper ? """<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/Swiper/4.3.3/css/swiper.min.css" />""" : ""
	html += vex ? """<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/vex-js/3.1.0/css/vex.min.css" />""" : ""
	html += vex ? """<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/vex-js/3.1.0/css/vex-theme-top.min.css" />""" : ""
	html += swiper ? """<script src="https://cdnjs.cloudflare.com/ajax/libs/Swiper/4.3.3/js/swiper.min.js"></script>""" : ""
	html += charts ? """<script src="https://www.gstatic.com/charts/loader.js"></script>""" : ""
	html += vex ? """<script>vex.defaultOptions.className = 'vex-theme-default'</script>""" : ""

	return html
}

String hideChartHtml() {
	String data = """
		<div class="swiper-slide">
			<section class="sectionBg" style="min-height: 250px;">
				<h3>Event History</h3>
			<br>
			<div class="centerText">
				<p>Waiting for more data to be collected...</p>
				<p>This may take a few hours</p>
			</div>
			</section>
		</div>
	"""
	return data
}

String getDataString(Integer seriesIndex) {
	String dataString = ""
	List dataTable = []
	switch (seriesIndex) {
		case 1:
			dataTable = state.energyTableYesterday
			break
		case 2:
			dataTable = state.powerTableYesterday
			break
		case 3:
			dataTable = state.energyTable
			break
		case 4:
			dataTable = state.powerTable
			break
	}
	LogTrace("getDataString: ${seriesIndex}, ${dataTable?.size()}")
	dataTable.each() {
		def dataArray = [[it[0],it[1],0],null,null,null,null]
		dataArray[seriesIndex] = it[2]
		dataString += dataArray.toString() + ","
	}
	return dataString
}

void getSomeData(dev, Boolean devpoll = false) {
//	LogTrace("getSomeData ${app} ${dev.label} ${dev.id}")

	def energyToday = dev.currentState("energy").value.toFloat()
/*
	def energyYesterday = dev.currentState("energy_yesterday").value
	def energyLast7Days = dev.currentState("energy_last7days").value
	def energyLife = dev.currentState("energy_life").value
*/
	Integer currentPower = dev.currentState("power").value.toInteger()
/*
	def efficiencyToday = dev.currentState("efficiency").value
	def efficiencyYesterday = dev.currentState("efficiency_yesterday").value
	def efficiencyLast7Days = dev.currentState("efficiency_last7days").value
	def efficiencyLifetime = dev.currentState("efficiency_lifetime")?.value
*/
	Integer numInverters = dev.currentState("numInverters").value.toInteger()
	Integer inverterSize = dev.currentState("inverterSize").value.toInteger()
	Integer panelSize = dev.currentState("panelSize").value.toInteger()

	state.maxPower = numInverters * inverterSize

	Date today = new Date()
	String todayDay = today.format("dd",location.timeZone)
	Integer hr = today.format("H", location.timeZone) as Integer
	Integer mins = today.format("m", location.timeZone) as Integer

//	LogTrace("getSomeData: ${today} ${todayDay} ${dev.id}")

	List powerTable = state?.powerTable
	List energyTable = state?.energyTable

	if (!state.today || state.today != todayDay) {
		state.peakpower = currentPower
		state.today = todayDay
		state.powerTableYesterday = powerTable
		state.energyTableYesterday = energyTable
		powerTable = []
		energyTable = []
		state.powerTable = powerTable
		state.energyTable = energyTable
		state.lastPower = 0
	}

	Integer previousPower = state.lastPower != null ? state.lastPower : currentPower
	Integer powerChange = currentPower - previousPower
	state.lastPower = currentPower

	if (state.peakpower <= currentPower) {
		state.peakpower = currentPower
		state.peakpercentage = (100*state.peakpower/state.maxPower).toFloat()
	}

	// get power data for yesterday and today so we can create a graph
	if (state.powerTableYesterday == null || state.energyTableYesterday == null || powerTable == null || energyTable == null) {
		Date startOfToday = timeToday("00:00", location.timeZone)
		def newValues
		if (state.powerTableYesterday == null || state.energyTableYesterday == null) {
			log.trace "Querying DB for yesterday's data…"
			def dataTable = []
			def powerData = [:] //device.statesBetween("power", startOfToday - 1, startOfToday, [max: 288]) // 24h in 5min intervals should be more than sufficient…
			// work around a bug where the platform would return less than the requested number of events (as of June 2016, only 50 events are returned)
			if (powerData.size()) {
/*
				while ((newValues = [:] //device.statesBetween("power", startOfToday - 1, powerData.last().date, [max: 288])).size()) {
					powerData += newValues
				}
				powerData.reverse().each() {
					dataTable.add([it.date.format("H", location.timeZone),it.date.format("m", location.timeZone),it.integerValue])
				}
*/
			}
			state.powerTableYesterday = dataTable
			dataTable = []
			def energyData = [:] //device.statesBetween("energy", startOfToday - 1, startOfToday, [max: 288])
			if (energyData.size()) {
/*
				while ((newValues = [:] //device.statesBetween("energy", startOfToday - 1, energyData.last().date, [max: 288])).size()) {
					energyData += newValues
				}
				// we drop the first point after midnight (0 energy) in order to have the graph scale correctly
				energyData.reverse().drop(1).each() {
					dataTable.add([it.date.format("H", location.timeZone),it.date.format("m", location.timeZone),it.floatValue])
				}
*/
			}
			state.energyTableYesterday = dataTable
		}
		if (powerTable == null || energyTable == null) {
			log.trace "Querying DB for today's data…"
			powerTable = []
			def powerData = [:] //device.statesSince("power", startOfToday, [max: 288])
			if (powerData.size()) {
/*
				while ((newValues = [:] //device.statesBetween("power", startOfToday, powerData.last().date, [max: 288])).size()) {
					powerData += newValues
				}
				powerData.reverse().each() {
					powerTable.add([it.date.format("H", location.timeZone),it.date.format("m", location.timeZone),it.integerValue])
				}
*/
			}


			energyTable = []
			def energyData = [:] //device.statesSince("energy", startOfToday, [max: 288])
			if (energyData.size()) {
/*
				while ((newValues = [:] //device.statesBetween("energy", startOfToday, energyData.last().date, [max: 288])).size()) {
					energyData += newValues
				}
				energyData.reverse().drop(1).each() {
					energyTable.add([it.date.format("H", location.timeZone),it.date.format("m", location.timeZone),it.floatValue])
				}
*/
			}
		}
	}
	// add latest power & energy readings for the graph
	if (currentPower > 0 || powerTable.size() != 0) {
		state.powerTable =	addValue(powerTable, hr, mins, currentPower)
		state.energyTable =	addValue(energyTable, hr, mins, energyToday)
	}
}

private cast(value, dataType) {
	switch(dataType) {
		case "number":
			if (value == null) return (Integer) 0
			if (value instanceof String) {
				if (value.isInteger())
					return value.toInteger()
				if (value.isFloat())
					return (Integer) Math.floor(value.toFloat())
				if (value in trueStrings)
					return (Integer) 1
			}
			def result = (Integer) 0
			try {
				result = (Integer) value
			} catch(all) {
				result = (Integer) 0
			}
			return result ? result : (Integer) 0
		case "decimal":
			if (value == null) return (Float) 0
			if (value instanceof String) {
				if (value.isFloat())
					return (Float) value.toFloat()
				if (value.isInteger())
					return (Float) value.toInteger()
				if (value in trueStrings)
					return (Float) 1
			}
			def result = (Float) 0
			try {
				result = (Float) value
			} catch(all) {
			}
			return result ? result : (Float) 0
	}
}


//ERS

String getAppEndpointUrl(String subPath) { return "${getFullApiServerUrl()}${subPath ? "/${subPath}" : ""}?access_token=${state.access_token}".toString() }
String getLocalEndpointUrl(String subPath) { return "${getFullLocalApiServerUrl()}${subPath ? "/${subPath}" : ""}?access_token=${state.access_token}".toString() }

Boolean getAccessToken() {
	try {
		if(!state.access_token) { state.access_token = createAccessToken() }
		return true
	}
	catch (ex) {
		String msg = "Error: OAuth is not Enabled for ${app?.name}!."
	//	sendPush(msg)
		log.error "getAccessToken Exception ${ex?.message}"
		LogAction("getAccessToken Exception | $msg", "warn", true)
		return false
	}
}

void enableOauth() {
	def params = [
			uri: "http://localhost:8080/app/edit/update?_action_update=Update&oauthEnabled=true&id=${app.appTypeId}",
			headers: ['Content-Type':'text/html;charset=utf-8']
	]
	try {
		httpPost(params) { resp ->
			//LogTrace("response data: ${resp.data}")
		}
	} catch (e) {
		log.debug "enableOauth something went wrong: ${e}"
	}
}

void resetAppAccessToken() {
	LogAction("Resetting getAppDebugDesc Access Token....", "info", true)
	revokeAccessToken()
	state.access_token = null
	if(getAccessToken()) {
		LogAction("Reset App Access Token... Successful", "info", true)
	//	settingUpdate("resetAppAccessToken", "false", "bool")
	}
}

static List addValue(List table, hr, mins, val) {
	List newTable = table
	if(table?.size() > 2) {
		def last = table.last()[2]
		def secondtolast = table[-2][2]
		if(val == last && val == secondtolast) {
			newTable = table.take(table.size() - 1)
		}
	}
	newTable.add([hr, mins, val])
	return newTable
}

Integer getStartTime(String tbl1, String tbl2) {
	Integer startTime = 24
	LogTrace("tbl1: ${state."tbl1"?.size()} tbl2: ${state."tbl2"?.size()} ")
	if (state."${tbl1}"?.size()) {
		startTime = state."${tbl1}".min{it[0].toInteger()}[0].toInteger()
	}
	if (state."${tbl2}"?.size()) {
		startTime = Math.min(startTime, state."${tbl2}".min{it[0].toInteger()}[0].toInteger())
	}
	return startTime
}

static String hideWeatherHtml() {
	def data = """
		<br></br><br></br>
		<h3 style="font-size: 22px; font-weight: bold; text-align: center; background: #00a1db; color: #f5f5f5;">The Required Weather data is not available yet...</h3>
		<br></br><h3 style="font-size: 22px; font-weight: bold; text-align: center; background: #00a1db; color: #f5f5f5;">Please refresh this page after a couple minutes...</h3>
		<br></br><br></br>"""
//	render contentType: "text/html", data: data, status: 200
}

def getTimeZone() {
	def tz = null
	if(location?.timeZone) { tz = location?.timeZone }
	if(!tz) { LogAction("getTimeZone: Hub or Nest TimeZone not found", "warn", true) }
	return tz
}

String getDtNow() {
	Date now = new Date()
	return formatDt(now)
}

String formatDt(Date dt) {
	def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	else {
		LogAction("HE TimeZone is not set; Please open your location and Press Save", "warn", true)
	}
	return tf.format(dt)
}

Integer GetTimeDiffSeconds(String strtDate, String stpDate=(String)null, String methName=(String)null) {
	//LogTrace("[GetTimeDiffSeconds] StartDate: $strtDate | StopDate: ${stpDate ?: "Not Sent"} | MethodName: ${methName ?: "Not Sent"})")
	if((strtDate && !stpDate) || (strtDate && stpDate)) {
		//if(strtDate?.contains("dtNow")) { return 10000 }
		Date now = new Date()
		String stopVal = stpDate ? stpDate.toString() : formatDt(now)
		Long start = Date.parse("E MMM dd HH:mm:ss z yyyy", strtDate).getTime()
		Long stop = Date.parse("E MMM dd HH:mm:ss z yyyy", stopVal).getTime()
		Integer diff = (Integer) ((stop - start) / 1000L)
		LogTrace("[GetTimeDiffSeconds] Results for '$methName': ($diff seconds)")
		return diff
	} else { return null }
}


/************************************************************************************************
|									LOGGING AND Diagnostic									|
*************************************************************************************************/

void LogTrace(String msg, logSrc=null) {
	Boolean trOn = (showDebug && advAppDebug)
	if(trOn) {
		Logger(msg, "trace")
	}
}

void LogAction(String msg, String type="debug", Boolean showAlways=false, logSrc=null) {
	Boolean isDbg = showDebug
	if(showAlways || isDbg) { Logger(msg, type) }
}

void Logger(String msg, String type="debug", logSrc=null, Boolean noSTlogger=false) {
	if(msg && type) {
		String labelstr = ""
		if(state.dbgAppndName == null) {
			Boolean tval = parent ? parent.getSettingVal("dbgAppndName") : settings?.dbgAppndName
			state.dbgAppndName = (tval || tval == null)
		}
		String t0 = app.label
		if(state.dbgAppndName) { labelstr = app.label+' | ' }
		String themsg = "${labelstr}${msg}".toString()
		//log.debug "Logger remDiagTest: $msg | $type | $logSrc"
		if(!noSTlogger) {
			switch(type) {
				case "debug":
					log.debug themsg
					break
				case "info":
					log.info '| '+themsg
					break
				case "trace":
					log.trace '| '+themsg
					break
				case "error":
					log.error '| '+themsg
					break
				case "warn":
					log.warn '|| '+themsg
					break
				default:
					log.debug themsg
					break
			}
		}
	}
	else { log.error "${labelstr}Logger Error - type: ${type} | msg: ${msg} | logSrc: ${logSrc}".toString() }
}

/**
 *	Enlighten Solar System (Local)
 *
 *	Modified from original by Andreas Amann
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *			http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 */

static String version() {
	return "1.0.2"
}

preferences {
	input("confIpAddr", "string", title:"Envoy Local IP Address",
		required: true, displayDuringSetup: true)
	input("confTcpPort", "number", title:"TCP Port",
		defaultValue:"80", required: true, displayDuringSetup: true)
	input("confNumInverters", "number", title:"Number of Inverters/Panels",
		required: true, displayDuringSetup: true)
	input("pollingInterval", "number", title:"Polling Interval (min)",
		defaultValue:"15", range: "2..59", required: true, displayDuringSetup: true)
	input("confInverterSize", "number", title:"Rated max power for each inverter", description: "Use '225' for M215 and '250' for M250",
		required: true, displayDuringSetup: true)
	input("confPanelSize", "number", title:"Panel size (W)", description: "Rated maximum power in Watts for each panel",
		required: true, displayDuringSetup: true)
	input("debugEnable", "bool", title: "Enable debug logging?", defaultValue: true)
}

metadata {
	definition (name: "Enlighten Envoy (local)", namespace: "E_Sch", author: "Eric, Andreas Amann") {
		capability "Sensor"
		capability "Power Meter"
		capability "Energy Meter"
		capability "Refresh"
		capability "Polling"

		attribute "energy_yesterday", "number"
		attribute "energy_last7days", "number"
		attribute "energy_life", "number"
		attribute "power_details", "string"
		attribute "efficiency", "number"
		attribute "efficiency_yesterday", "number"
		attribute "efficiency_last7days", "number"
		attribute "efficiency_lifetime", "number"

		attribute "installationDate", "string"

		attribute "numInverters", "number"
		attribute "pollingInterval", "number"
		attribute "inverterSize", "number"
		attribute "panelSize", "number"
	}
}

void poll() {
	pullData()
}

void refresh() {
	pullData()
}

void updated() {
	if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 2000) {
		state.updatedLastRanAt = now()
		trace "updated() called with settings: ${settings.inspect()}".toString()
		state.remove('api')
		state.remove('installationDate')
		state.maxPower = settings.confNumInverters * settings.confInverterSize
		state.LastDetailsRanAt = null
		sendEvent(name: "numInverters", value: confNumInverters, displayed:false)
		sendEvent(name: "inverterSize", value: confInverterSize, displayed:false)
		sendEvent(name: "panelSize", value: confPanelSize, displayed:false)
		pullData()
		startPoll()
		if(debugEnable) runIn(1800,logsOff)
	} else {
		trace "updated() ran within the last 2 seconds - skipping"
	}
}

void logsOff() {
	debug "debug logging disabled..."
	device.updateSetting("debugEnable",[value:"false",type:"bool"])
}

void ping() {
	trace "checking device health…"
	pullData()
}

void startPoll() {
	unschedule()
	// Schedule polling based on preference setting
	def sec = Math.round(Math.floor(Math.random() * 60))
	def min = Math.round(Math.floor(Math.random() * settings.pollingInterval.toInteger()))
	String cron = "${sec} ${min}/${settings.pollingInterval.toInteger()} * * * ?" // every N min
	trace "startPoll: schedule('$cron', pullData)".toString()
	schedule(cron, pullData)
}

void updateDNI() {
	if (!state.dni || state.dni != device.deviceNetworkId || (state.mac && state.mac != device.deviceNetworkId)) {
		device.setDeviceNetworkId(createNetworkId(settings.confIpAddr, settings.confTcpPort))
		state.dni = device.deviceNetworkId
	}
}

private String createNetworkId(ipaddr, port) {
	if (state.mac) {
		return state.mac
	}
	def hexIp = ipaddr.tokenize('.').collect {
		String.format('%02X', it.toInteger())
	}.join()
	def hexPort = String.format('%04X', port.toInteger())
	return "${hexIp}:${hexPort}".toString()
}

private String getHostAddress() {
	return "${settings.confIpAddr}:${settings.confTcpPort}"
}

void pullData() {
	updateDNI()
	if (!state.installationDate) {
		debug "requesting installation date from Envoy…".toString()
		sendHubCommand(new hubitat.device.HubAction([
				method: "GET",
				path: "/production?locale=en",
				headers: [HOST:getHostAddress()]
			],
			state.dni,
			[callback: installationDateCallback])
		)
	} else {
		state.lastRequestType = (state.api == "HTML" ? "HTML" : "JSON API")
		debug "requesting latest data from Envoy via ${state.lastRequestType}…".toString()
		updateDNI()
		sendHubCommand(new hubitat.device.HubAction([
				method: "GET",
				path: state.lastRequestType == "HTML" ? "/production?locale=en" : "/api/v1/production",
				headers: [HOST:getHostAddress()]
			],
			state.dni,
			[callback: dataCallback])
		)
	}
}

private Integer retrieveProductionValue(String body, String heading) {
	Integer val = 0
	def patternString = "(?ms).*?${heading}.*?<td>\\s*([\\d\\.]+)\\s*([kM]?W)h?<.*"
	if (body ==~ /${patternString}/) {
		body.replaceFirst(/${patternString}/) {all, num, unit ->
			val = Double.parseDouble(num)
			if (unit == "kW") {
				val *= 1000
			}
			else if (unit == "MW") {
				val *= 1000000
			}
			return true
		}
		return val
	}
	return null
}

private Map parseHTMLProductionData(String body) {
	def data = [:]
	data.wattHoursToday = retrieveProductionValue(body, "Today")
	data.wattHoursSevenDays = retrieveProductionValue(body, "Past Week")
	data.wattHoursLifetime = retrieveProductionValue(body, "Since Installation")
	data.wattsNow = retrieveProductionValue(body, "Currently")
	return data
}

void installationDateCallback(hubitat.device.HubResponse msg) {
	if (!state.mac || state.mac != msg.mac) {
		state.mac = msg.mac
	}
	if (!state.installationDate && !msg.json && msg.body) {
		debug "trying to determine system installation date…"
		def patternString = "(?ms).*?System has been live since.*?>(.*?)<.*"
		if (msg.body ==~ /${patternString}/) {
			msg.body.replaceFirst(/${patternString}/) {all, dateString ->
				try {
					state.installationDate = new Date().parse("E MMM dd, yyyy H:m a z", dateString).getTime()
					debug "system has been live since ${dateString}".toString()
					sendEvent(name: 'installationDate', value: "System live since " + new Date(state.installationDate).format("MMM dd, yyyy"), displayed: false)
				}
				catch (Exception ex) {
					debug "unable to parse installation date '${dateString}' ('${ex}')".toString()
					state.installationDate = -1
				}
			}
		}
		else {
			debug "unable to find installation date on page"
			state.installationDate = -1
		}
	}
	pullData()
}

void dataCallback(hubitat.device.HubResponse msg) {
	if (!state.mac || state.mac != msg.mac) {
		state.mac = msg.mac
	}
	if (!state.api && state.lastRequestType != "HTML" && (msg.status != 200 || !msg.json)) {
		debug "JSON API not available, falling back to HTML interface (Envoy responded with status code ${msg.status})".toString()
		state.api = "HTML"
		return
	}
	else if (!msg.body) {
		log.error "${device.displayName} - no HTTP body found in '${message}'".toString()
		return
	}
	def data = state.api == "HTML" ? parseHTMLProductionData(msg.body) : msg.json

	if (state.lastData && (data.wattHoursToday == state.lastData.wattHoursToday) && (data.wattsNow == state.lastData.wattsNow)) {
		debug "no new data"
		//sendEvent(name: 'lastUpdate', value: new Date(), displayed: false) // dummy event for health check
		return
	}
	state.lastData = data
	debug "new data: ${data}".toString()

	def energyToday = (data.wattHoursToday/1000).toFloat()

	def energyLast7Days = (data.wattHoursSevenDays/1000).toFloat()
	def energyLife = (data.wattHoursLifetime/1000000).toFloat()

	def currentPower = data.wattsNow

	def todayDay = new Date().format("dd",location.timeZone)
	def powerTable = state?.powerTable
	def energyTable = state?.energyTable

	Boolean dayChg = false
	if (!state.today || state.today != todayDay) {
		dayChg = true
		state.peakpower = currentPower
		state.today = todayDay

		state.powerTableYesterday = powerTable
		state.energyTableYesterday = energyTable
		powerTable = powerTable ? [] : null
		energyTable = energyTable ? [] : null

		state.lastPower = 0
		state.lastPower1 = 0
		sendEvent(name: 'energy_yesterday', value: device.currentState("energy")?.value, displayed: false)
		sendEvent(name: 'efficiency_yesterday', value: device.currentState("efficiency")?.value, displayed: false)
	}
	def efficiencyToday = (1000*energyToday/(settings.confNumInverters * settings.confPanelSize)).toFloat()

	if( dayChg || currentPower == 0 || (!state.LastDetailsRanAt || now() >= state.LastDetailsRanAt + (2*60*60*1000)) ) {
		state.LastDetailsRanAt = now()

		def previousPower = state.lastPower != null ? state.lastPower : currentPower
		def powerChange = currentPower - previousPower
		state.lastPower = currentPower

		if (state.peakpower <= currentPower) {
			state.peakpower = currentPower
			state.peakpercentage = (100*state.peakpower/state.maxPower).toFloat()
		}
		sendEvent(name: 'power_details', value: ("(" + String.format("%+,d", powerChange) + "W) — Today's Peak: " + String.format("%,d", state.peakpower) + "W (" + String.format("%.1f", state.peakpercentage) + "%)"), displayed: false)

		sendEvent(name: 'energy_last7days', value: energyLast7Days, displayed: false)

		sendEvent(name: 'energy_life', value: energyLife, displayed: false)

		def efficiencyLifetime = "NA"
		if (state.installationDate && state.installationDate > 0) {
			def systemAgeInDays = (new Date().getTime() - state.installationDate)/(1000*60*60*24)
			efficiencyLifetime = (1000000/systemAgeInDays*energyLife/(settings.confNumInverters * settings.confPanelSize)).toFloat()
		}
		sendEvent(name: 'efficiency_lifetime', value: efficiencyLifetime, displayed: false)
		sendEvent(name: 'efficiency', value: efficiencyToday, displayed: false)

		def efficiencyLast7Days = (1000/7*energyLast7Days/(settings.confNumInverters * settings.confPanelSize)).toFloat()
		sendEvent(name: 'efficiency_last7days', value: efficiencyLast7Days, displayed: false)
	}

	def previousPower = state.lastPower1 != null ? state.lastPower1 : currentPower
	def powerChange = currentPower - previousPower
	state.lastPower1 = currentPower

	sendEvent(name: 'energy', value: energyToday, unit: "kWh", descriptionText: "Energy is " + String.format("%,#.3f", energyToday) + "kWh\n(Efficiency: " + String.format("%#.3f", efficiencyToday) + "kWh/kW)")
	sendEvent(name: 'power', value: currentPower, unit: "W", descriptionText: "Power is " + String.format("%,d", currentPower) + "W (" + String.format("%#.1f", 100*currentPower/state.maxPower) + "%)\n(" + String.format("%+,d", powerChange) + "W since last reading)")



	// get power data for yesterday and today so we can create a graph
	if (state.powerTableYesterday == null || state.energyTableYesterday == null || powerTable == null || energyTable == null) {
		Date startOfToday = timeToday("00:00", location.timeZone)
		def newValues
		if (state.powerTableYesterday == null || state.energyTableYesterday == null) {
			//trace "Querying DB for yesterday's data…"
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
			//trace "Querying DB for today's data…"
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
		def newDate = new Date()
		powerTable.add([newDate.format("H", location.timeZone),newDate.format("m", location.timeZone),currentPower])
		energyTable.add([newDate.format("H", location.timeZone),newDate.format("m", location.timeZone),energyToday])
	}
	state.powerTable = powerTable
	state.energyTable = energyTable
}

void debug(String msg) {
	if(debugEnable) log.debug device.displayName+' - '+msg
}

void trace(String msg) {
	if(debugEnable) log.trace device.displayName+' - '+msg
}

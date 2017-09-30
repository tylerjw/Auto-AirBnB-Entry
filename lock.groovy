/**
 *  Airbnb Lock
 *
 *  Copyright 2017 Tyler Weaver
 *  Copyright 2015 Aaron Parecki
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * Test URL found on stackoverflow:
 * https://www.airbnb.it/calendar/ical/2533404.ics?s=580a83c1bcbc0e8af72cfc62bcc2676d
 */

definition(
    name: "Airbnb Lock",
    namespace: "tylerjw",
    author: "Tyler Weaver",
    description: "Creates an HTTP API to update the codes for a lock",
    category: "",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    oauth: true)

preferences {
  section("Choose a Door Lock") {
    input "doorlock", "capability.lockCodes", required: true
    input "ical", "string", required: true, title: "URL to AirBnB Calendar"
    input "theTime", "time", required: true, title: "Time to check AirBnB Calendar each day"
    input "firstSlot", "number", required: true, defaultValue: 3, title: "First code slot to control, will use the next slot after this one if there are two guests on the same day"
  }

  // notifications
  section("Via a push notification and/or an SMS message"){
    input("recipients", "contact", title: "Send notifications to") {
      input "phone", "phone", title: "Enter a phone number to get SMS", required: false
      paragraph "If outside the US please make sure to enter the proper country code"
      input "pushAndPhone", "enum", title: "Notify me via Push Notification", required: false, options: ["Yes", "No"]
    }
  }
}

def installed() {
  // log.debug "Installed with settings: ${settings}"

  initialize()
}

def updated() {
  // log.debug "Updated with settings: ${settings}"

  unsubscribe()
  unschedule()
  initialize()
}

def initialize() {
  subscribe(doorlock, "codeReport", codeReportEvent)
  subscribe(doorlock, "codeChanged", codeChangedEvent)

  schedule(theTime, doCalenderCheck)
}

def doCalenderCheck() {
  def params = [
    uri: ical
  ]
  def today = new Date()
  def codeIndex = firstSlot
  try {
    httpGet(params) { resp ->
      def data = parseICal(resp.data)

      for (event in data) {
        if (event['phone']) {
          def code = event['phone'].replaceAll(/\D/, '')[-4..-1]
          // log.debug "start: ${event['dtStart']}, end: ${event['dtEnd']}, phone: ${event['phone']}, codeIndex: ${codeIndex}, code: ${code}"
          setCode(codeIndex, code)
          codeIndex++
        } else {
          sendMessage("Warning: Phone number not set for event today! - ${event['record']}")
        }
      }
    }
  } catch (e) {
    log.error "something went wrong: $e"
  }
}

def setCode(number, code) {
  doorlock.setCode(number, code)
}

def codeReportEvent(evt) {
  // log.debug "Got the code report event"
  // log.debug evt.jsonData
  sendMessage(location.name + " door code was set to " + evt.jsonData.code)
}

def codeChangedEvent(evt) {
  // log.debug "Code changed for slot $evt.value"
}

def currentEvent(today, event) {
  return ((event['dtStart'] < today) && (today < (event['dtEnd']+1)))
}

def sendMessage(msg) {
  Map options = [translatable: true, triggerEvent: evt]

  if (location.contactBookEnabled) {
    sendNotificationToContacts(msg, recipients, options)
  } else {
    if (phone) {
      options.phone = phone
      if (pushAndPhone != 'No') {
        // log.debug 'Sending push and SMS'
        options.method = 'both'
      } else {
        // log.debug 'Sending SMS'
        options.method = 'phone'
      }
    } else if (pushAndPhone != 'No') {
      // log.debug 'Sending push'
      options.method = 'push'
    } else {
      // log.debug 'Sending nothing'
      options.method = 'none'
    }
    sendNotification(msg, options)
  }
}

String readLine(ByteArrayInputStream is) {
  int size = is.available();
  if (size <= 0) {
    return null;
  }

  String ret = "";
  byte data = 0;
  char ch;

  while (true) {
    data = is.read();
    if (data == -1) {
      // we are done here
      break;
    }

    ch = (char)(data&0xff);
    if (ch == '\n') {
      break;
    }

    ret += ch;

    if (ret.endsWith("\\n")) {
      ret = ret.replaceAll(/\\n/,"");
      break;
    }
  }

  return ret;
}

def parseICal(ByteArrayInputStream is) {
  def iCalEvents = []
  def iCalEvent = null
  def sincePhone = 100
  def today = new Date()

  while (true) {
    def line = readLine(is)

    if (line == null) {
      break;
    }

    if (line == "BEGIN:VEVENT") {
      iCalEvent = [record:'']
    } else if (line == "END:VEVENT") {
      if (currentEvent(today, iCalEvent) && iCalEvent['summary'] != 'Not available') {
        iCalEvents.push(iCalEvent)
      }
      iCalEvent = null
    } else if (iCalEvent != null) {
      // parse line
      def compoundKey = null
      def subKey = null
      def key = null
      def value = null

      sincePhone++;

      if ( line ==~ /^[A-Z]+[;:].*/ ) {
        // grab everything before the :
        key = line.replaceAll(/:.*/, '')
        // grab everything before the ;
        compoundKey = key.replaceAll(/;.*/, '')
        // grab everything after the ${key}:
        value = line.replaceFirst(key + ':', '').trim()
        // grab everything before the ; in the key
        if (compoundKey != key) {
          // we found a compound date key
          subKey = key.replaceFirst(compoundKey + ';', '').trim()
        }

        if (key == 'DESCRIPTION') {
          // we found the start of the description
          key = value.replaceAll(/:.*/, '')
          value = value.replaceFirst(key + ':', '').trim()
        }

        if (key == 'UID') { iCalEvent.put('uid',value) }
        else if (key == 'CREATED') { iCalEvent.put('created', value) }
        else if (key == 'RRULE') { iCalEvent.put('rRule', value) }
        else if (key == 'RDATE') { iCalEvent.put('rDate', value) }
        else if (key == 'DTSTAMP') { iCalEvent.put('dtStamp', parseDate(value)) }
        else if (key == 'CHECKIN') { iCalEvent.put('checkin', value) }
        else if (key == 'CHECKOUT') { iCalEvent.put('checkout', value) }
        else if (key == 'NIGHTS') { iCalEvent.put('nights', value) }
        else if (key == 'EMAIL') { iCalEvent.put('email', value) }
        else if (key == 'SUMMARY') { iCalEvent.put('summary', value) }
        else if (key == 'LOCATION') { iCalEvent.put('location', value) }
        else if (key == 'PHONE') { sincePhone = 0; }
        else if (compoundKey == 'DTSTART') {
          iCalEvent.put('dtStartString', value)
          iCalEvent.put('dtStart', parseDate(value))
          iCalEvent.put('dtStartTz', subKey)
        } else if (compoundKey == 'DTEND') {
          iCalEvent.put('dtEndString', value)
          iCalEvent.put('dtEnd', parseDate(value)) 
          iCalEvent.put('dtEndTz', subKey)
        }
      }

      if (sincePhone == 1) {
        // phone number
        iCalEvent.put('phone', line)
      }

      if (line) {
        iCalEvent['record'] = iCalEvent['record'] + line + '\n'
      }
    }
  }
  

  return iCalEvents
}

Date parseDate(String value) {
  if ( value ==~ /[0-9]*T[0-9]*Z/ ) {
    Date.parse("yyyyMMdd'T'HHmmss'Z'", value)
  } else if ( value ==~ /[0-9]*T[0-9]*/ ) {
    Date.parse("yyyyMMdd'T'HHmmss", value)
  } else if ( value ==~ /[0-9]*/ ) {
    Date.parse("yyyyMMdd", value)
  } else {
    println "WARNING: unknown date format: ${value}"
    null
  }
}








// test script for decoding ical events

def data = "BEGIN:VCALENDAR\nPRODID;X-RICAL-TZSOURCE=TZINFO:-//Airbnb Inc//Hosting Calendar 0.8.8//EN\nCALSCALE:GREGORIAN\nVERSION:2.0\nBEGIN:VEVENT\nDTEND;VALUE=DATE:20160804\nDTSTART;VALUE=DATE:20160803\nUID:-kc93rr5ktp3n-vo1wr5xncou8@airbnb.com\nDESCRIPTION:CHECKIN: 03-08-2016\nCHECKOUT: 04-08-2016\nNIGHTS: 1\nPHONE: \n+39 340 849 6025\nEMAIL: (nessun indirizzo email alias disponibile)\nPRO\nPERTY: CASA MANI: Castellabate dal mare\n\nSUMMARY:Ileana Forneris (E2E5ME)\nLOCATION:CASA MANI: Castellabate dal mare\nEND:VEVENT\nEND:VCALENDAR"

def parseICal(data) {
  def lines =  data.split(/\n/)

  def iCalEvents = []
  def iCalEvent = null

  for (line in lines) {
    if (line == 'BEGIN:VEVENT') {
      iCalEvent = [record:'']
    } else if (line == 'END:VEVENT') {
      iCalEvents.push(iCalEvent)
      iCalEvent = null
    } else if (iCalEvent != null) {
      // parse line
      def compoundKey = null
      def subKey = null
      def key = null
      def value = null

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

      if ( line ==~ /\+\d+ \d\d\d \d\d\d \d\d\d\d/) {
        // phone number
        iCalEvent.put('phone', line)
      }

      if (line) {
        iCalEvent['record'] += line + '\n'
      }
    }
  }

  return iCalEvents
}

Date parseDate(String value) {
  def longDate = ~/[0-9]*T[0-9]*Z/
  def mediumDate = ~/[0-9]*T[0-9]*/
  def shortDate = ~/[0-9]*/

  if ( longDate.matcher(value).matches() ) {
    Date.parse("yyyyMMdd'T'HHmmss'Z'", value)
  } else if ( mediumDate.matcher(value).matches() ) {
    Date.parse("yyyyMMdd'T'HHmmss", value)
  } else if ( shortDate.matcher(value).matches() ) {
    Date.parse("yyyyMMdd", value)
  } else {
    println "WARNING: unknown date format: ${value}"
    null
  }
}

def iCalEvents = parseICal(data)
println iCalEvents

​

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.util.time;

import java.util.Date;
import java.util.TimeZone;

import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class TimeTest {

    public void testMakeStringExact_secondsAndMillis() {
        check(1, "1ms");
        check(1000, "1s");
        check(1001, "1s 1ms");
        check(1011, "1s 11ms");
        check(3*1000, "3s");
        check(3*1000+1, "3s 1ms");
        check(3*1000+10, "3s 10ms");
        check(3*1000+100, "3s 100ms");
        check(30*1000, "30s");
        check(30*1000+1, "30s 1ms");
        check(30*1000+100, "30s 100ms");
    }
    
    public void testMakeStringRounded_secondsAndMillis() {
        checkR(1, "1ms");
        checkR(1000, "1s");
        checkR(1001, "1.00s");
        checkR(1011, "1.01s");
        checkR(3*1000, "3s");
        checkR(3*1000+1, "3.00s");
        checkR(3*1000+10, "3.01s");
        checkR(3*1000+100, "3.10s");
        checkR(30*1000, "30s");
        checkR(30*1000+1, "30.0s");
        checkR(30*1000+10, "30.0s");
        checkR(30*1000+100, "30.1s");
    }
    
    public void testMakeStringExact_days() {
        check(3*Time.MILLIS_IN_DAY, "3d");
        check(3*Time.MILLIS_IN_DAY + 2*Time.MILLIS_IN_HOUR + 30*Time.MILLIS_IN_MINUTE + 1001, "3d 2h 30m 1s 1ms");
    }

    public void testMakeStringRounded_days() {
        checkR(3*Time.MILLIS_IN_DAY, "3d");
        checkR(3*Time.MILLIS_IN_DAY + 2*Time.MILLIS_IN_HOUR + 30*Time.MILLIS_IN_MINUTE + 1001, "3d 2h");
        checkR(3*Time.MILLIS_IN_DAY + 30*Time.MILLIS_IN_MINUTE + 1001, "3d 30m");
        checkR(3*Time.MILLIS_IN_DAY + 1001, "3d");
        
        checkR(30*Time.MILLIS_IN_MINUTE + 1111, "30m 1s");
    }

    public void testMakeStringExact_nanos() {
        checkN(1001, "1us 1ns");
        checkN(123000+456, "123us 456ns");
        checkN(3*Time.MILLIS_IN_DAY*1000*1000, "3d");
        checkN(3*Time.MILLIS_IN_DAY*1000*1000 + 1, "3d 1ns");
        checkN(3*Time.MILLIS_IN_DAY*1000*1000 + 1001, "3d 1us 1ns");
        checkN((3*Time.MILLIS_IN_DAY + 2*Time.MILLIS_IN_HOUR + 30*Time.MILLIS_IN_MINUTE + 1001)*1000*1000+123000+456, 
                "3d 2h 30m 1s 1ms 123us 456ns");
    }

    public void testMakeStringRounded_nanos() {
        checkRN(3*Time.MILLIS_IN_DAY*1000*1000, "3d");
        checkRN((3*Time.MILLIS_IN_DAY + 2*Time.MILLIS_IN_HOUR + 30*Time.MILLIS_IN_MINUTE + 1001)*1000*1000+1001, "3d 2h");
        checkRN((3*Time.MILLIS_IN_DAY + 30*Time.MILLIS_IN_MINUTE + 1001)*1000*1000+1001, "3d 30m");
        checkRN((3*Time.MILLIS_IN_DAY + 1001)*1000*1000+1001, "3d");
        
        checkRN((30*Time.MILLIS_IN_MINUTE + 1111)*1000*1000+1001, "30m 1s");
        checkRN((30*Time.MILLIS_IN_MINUTE)*1000*1000+1001, "30m");
        checkRN((31000L)*1000*1000, "31s");
        checkRN((31000L)*1000*1000+1001, "31.0s");
        checkRN(1001, "1.001us");
        checkRN(10101, "10.10us");
        checkRN(101001, "101.0us");
        checkRN(123000+456, "123.5us");
    }


    private void check(long millis, String expected) {
        Assert.assertEquals(Time.makeTimeStringExact(millis), expected);
    }
    
    private void checkR(long millis, String expected) {
        Assert.assertEquals(Time.makeTimeStringRounded(millis), expected);
    }

    private void checkN(long nanos, String expected) {
        Assert.assertEquals(Time.makeTimeStringNanoExact(nanos), expected);
    }
    
    private void checkRN(long nanos, String expected) {
        Assert.assertEquals(Time.makeTimeStringNanoRounded(nanos), expected);
    }

    @Test
    public void testDateRounding() {
        long x = System.currentTimeMillis();
        Date d1 = Time.dropMilliseconds(new Date(x));
        Date d2 = new Date(x - (x%1000));
        Date d3 = new Date( (x/1000)*1000 );
        Assert.assertEquals(d1.getTime() % 1000, 0);
        Assert.assertEquals(d1, d2);
        Assert.assertEquals(d1, d3);
    }

    @Test
    public void testDateRoundingNull() {
        Assert.assertNull(Time.dropMilliseconds(null));
    }

    @Test
    public void testMakeStringExactZero() { check(0, "0ms"); }
    @Test
    public void testMakeStringExactNegative() { check(-1, "-1ms"); }
    @Test
    public void testMakeStringRoundedZero() { checkR(0, "0ms"); }
    @Test
    public void testMakeStringRoundedNegative() { checkR(-1, "-1ms"); }

    @Test
    public void testElapsedSince() {
        long aFewSecondsAgo = System.currentTimeMillis() - 7*1000;
        
        Duration aFewSeconds = Time.elapsedSince(aFewSecondsAgo);
        Assert.assertTrue(aFewSeconds.toMilliseconds() > 5*1000);
        Assert.assertTrue(10*1000 > aFewSeconds.toMilliseconds());
        
        Assert.assertTrue(Time.hasElapsedSince(aFewSecondsAgo, Duration.FIVE_SECONDS));
        Assert.assertFalse(Time.hasElapsedSince(aFewSecondsAgo, Duration.TEN_SECONDS));
        Assert.assertTrue(Time.hasElapsedSince(-1, Duration.TEN_SECONDS));
    }
    
    @Test
    public void testMakeDateString() {
        String in1 = "2015-06-15T12:34:56";
        Date d1 = Time.parseDate(in1);
        Assert.assertEquals(Time.makeDateString(d1), in1.replace('T', ' ')+".000");
        
        String in2 = "2015-06-15T12:34:56Z";
        Date d2 = Time.parseDate(in2);
        Assert.assertEquals(Time.makeDateString(d2, Time.DATE_FORMAT_ISO8601, Time.getTimeZone("UTC")), in1+".000+0000");
    }

    @Test(groups="Integration")  //because it depends on TZ's set up and parsing months
    public void testTimeZones() {
        // useful to debug, if new special time zones needed
//        for (String id: TimeZone.getAvailableIDs()) {
//            TimeZone tz = TimeZone.getTimeZone(id);
//            System.out.println(id+": "+tz.getDisplayName()+" "+tz.getDisplayName(true, TimeZone.SHORT)+" "+tz);
//        }
        
        Assert.assertEquals("+0100", Time.getTimeZoneOffsetString("Europe/London", 2015, 6, 4).get());
        
        Assert.assertEquals("-0500", Time.getTimeZoneOffsetString("EST", 2015, 1, 4).get());
        Assert.assertEquals("-0400", Time.getTimeZoneOffsetString("America/New_York", 2015, 6, 4).get());
        Assert.assertEquals("-0500", Time.getTimeZoneOffsetString("America/New_York", 2015, 1, 4).get());
        
        // BST treated as British Time (not Bangladesh)
        Assert.assertEquals("+0000", Time.getTimeZoneOffsetString("BST", 2015, 1, 4).get());
        Assert.assertEquals("+0100", Time.getTimeZoneOffsetString("BST", 2015, 6, 4).get());
        
        // EST treated as EDT not fixed -0500
        Assert.assertEquals("-0400", Time.getTimeZoneOffsetString("EST", 2015, 6, 4).get());
        
        // these normally not recognized
        Assert.assertEquals("-0400", Time.getTimeZoneOffsetString("EDT", 2015, 6, 4).get());
        Assert.assertEquals("-0500", Time.getTimeZoneOffsetString("EDT", 2015, 1, 4).get());
        
        Assert.assertEquals("-0600", Time.getTimeZoneOffsetString("CST", 2015, 1, 4).get());
        Assert.assertEquals("-0700", Time.getTimeZoneOffsetString("MST", 2015, 1, 4).get());
        Assert.assertEquals("-0800", Time.getTimeZoneOffsetString("PST", 2015, 1, 4).get());
        
        Assert.assertEquals("+0530", Time.getTimeZoneOffsetString("IST", 2015, 1, 4).get());
    }
        
    @Test
    public void testParseDate() {
        doTestParseDate(false);
    }
    
    @Test(groups="Integration")  //because it depends on TZ's set up and parsing months
    public void testParseDateIntegration() {
        doTestParseDate(true);
    }
    
    private void doTestParseDate(boolean integration) {
        // explicit TZ inclusion 
        assertDatesParseToEqual("2015.6.4.0000 +0100", "2015-06-04-0000 +0100");
        assertDatesParseToEqual("2015.6.4.0100 +0100", "2015-06-04-0000 +0000");
        assertDatesParseToEqual("2015.6.4.0100 -0100", "2015-06-04-0200 +0000");
        if (integration) assertDatesParseToEqual("20150604 BST", "2015-06-04 +0100");
        
        // no TZ uses server default
        assertDatesParseToEqual("2015.6.4.0000", "2015-06-04-0000 "+Time.getTimeZoneOffsetString(TimeZone.getDefault(), 2015, 6, 4));
        assertDatesParseToEqual("20150604", "2015-06-04-0000");

        // parse TZ
        if (integration) {
            assertDatesParseToEqual("20150604 +BST", "2015-06-04 +0100");
            assertDatesParseToEqual("20150604 - - - BST", "2015-06-04 +0100");
            assertDatesParseToEqual("20150604--BST", "2015-06-04 +0100");
            assertDatesParseToEqual("20150604-//-BST", "2015-06-04 +0100");
        }
        assertDatesParseToEqual("2015.6.4+0100", "2015-06-04-0000+0100");
        assertDatesParseToEqual("20150604-+0100", "2015-06-04 +0100");
        assertDatesParseToEqual("20150604, +0100", "2015-06-04 +0100");
        assertDatesParseToEqual("201506040000, 0100", "2015-06-04 +0100");
        assertDatesParseToEqual("20150604  , 0000  , 0100", "2015-06-04 +0100");
        assertDatesParseToEqual("2015-6-4 +0100", "2015-06-04-0000 +0100");
        assertDatesParseToEqual("2015-6-4 -0100", "2015-06-04-0000 -0100");
        assertDatesParseToEqual("20150604-0000//-0100", "2015-06-04 -0100");
        // ambiguous TZ/hours parse prefers hours
        assertDatesParseToEqual("2015-6-4-0100", "2015-06-04-0100");
        assertDatesParseToEqual("2015-6-4--0100", "2015-06-04-0100");

        // formats without spaces
        assertDatesParseToEqual("20150604080012", "2015-06-04-080012");
        assertDatesParseToEqual("20150604080012 +1000", "2015-06-03-220012 +0000");
        assertDatesParseToEqual("20150604080012 -1000", "2015-06-04-180012 +0000");
        assertDatesParseToEqual("20150604080012.345 +1000", "2015-06-03-220012.345 +0000");
        if (integration) {
            assertDatesParseToEqual("20150604 BST", "2015-06-04 +0100");
            assertDatesParseToEqual("20150604 Europe/London", "2015-06-04 +0100");
        }

        // more misc tests
        assertDatesParseToEqual("20150604 08:00:12.345", "2015-06-04-080012.345");
        assertDatesParseToEqual("20150604-080012.345", "2015-06-04-080012.345");
        assertDatesParseToEqual("2015-12-1", "2015-12-01-0000");
        assertDatesParseToEqual("1066-12-1", "1066-12-01-0000");
        
        assertDatesParseToEqual("20150604T080012.345", "2015-06-04-080012.345");
        assertDatesParseToEqual("20150604T080012.345Z", "2015-06-04-080012.345+0000");
        assertDatesParseToEqual("20150604t080012.345 Z", "2015-06-04-080012.345+0000");

        // millis parse, and zero is epoch, but numbers which look like a date or datetime take priority
        assertDatesParseToEqual("0", "1970-1-1 UTC");
        assertDatesParseToEqual("20150604", "2015-06-04");
        assertDatesParseToEqual(""+Time.parseDate("20150604").getTime(), "2015-06-04");
        assertDatesParseToEqual("20150604080012", "2015-06-04-080012");
        assertDatesParseToEqual("0", "1970-1-1 UTC");

        // leap year
        Assert.assertEquals(Time.parseDate("2012-2-29").getTime(), Time.parseDate("2012-3-1").getTime() - 24*60*60*1000);
        // perverse, but accepted for the time being:
        Assert.assertEquals(Time.parseDate("2013-2-29").getTime(), Time.parseDate("2013-3-1").getTime());

        // accept am and pm
        assertDatesParseToEqual("20150604 08:00:12.345a", "2015-06-04-080012.345");
        assertDatesParseToEqual("20150604 08:00:12.345 PM", "2015-06-04-200012.345");
        if (integration) assertDatesParseToEqual("20150604 08:00:12.345 am BST", "2015-06-04-080012.345 +0100");
        
        // *calendar* parse includes time zone
        Assert.assertEquals(Time.makeDateString(Time.parseCalendar("20150604 08:00:12.345a +0100"),
            Time.DATE_FORMAT_ISO8601), "2015-06-04T08:00:12.345+0100");
        Assert.assertEquals(Time.makeDateString(Time.parseCalendar("20150604 08:00:12.345a "+Time.TIME_ZONE_UTC.getID()),
            Time.DATE_FORMAT_ISO8601), "2015-06-04T08:00:12.345+0000");
        
        // accept month in words
        if (integration) {
            assertDatesParseToEqual("2015-Dec-1", "2015-12-01-0000");
            assertDatesParseToEqual("2015 Dec 1", "2015-12-01-0000");
            assertDatesParseToEqual("2015-DEC-1", "2015-12-01-0000");
            assertDatesParseToEqual("2015 December 1", "2015-12-01-0000");
            assertDatesParseToEqual("2015 December 1", "2015-12-01-0000");
            assertDatesParseToEqual("2015-Mar-1", "2015-03-01-0000");
            assertDatesParseToEqual("2015 Mar 1", "2015-03-01-0000");
            assertDatesParseToEqual("2015-MAR-1", "2015-03-01-0000");
            assertDatesParseToEqual("2015 March 1", "2015-03-01-0000");
            assertDatesParseToEqual("2015 March 1", "2015-03-01-0000");
        }
        
        // for month in words, allow selected other orders also
        if (integration) {
            assertDatesParseToEqual("1-Jun-2015", "2015-06-01-0000");
            assertDatesParseToEqual("Jun 1, 2015", "2015-06-01-0000");
            assertDatesParseToEqual("June 1, 2015, 4pm", "2015-06-01-1600");
        }
    
        // also allow time first if separators are used
        assertDatesParseToEqual("16:00, 2015-12-30", "2015-12-30-1600");
        if (integration) {
            assertDatesParseToEqual("4pm, Dec 1, 2015", "2015-12-01-1600");
            assertDatesParseToEqual("16:00 30-Dec-2015", "2015-12-30-1600");
        }
        
        // and if time comes first, TZ can be before or after date
        assertDatesParseToEqual("4pm +0100, 2015-12-30", "2015-12-30-1600 +0100");
        assertDatesParseToEqual("4pm, 2015-12-30, +0100", "2015-12-30-1600 +0100");
        
        // these ambiguous ones are accepted (maybe we'd rather not), 
        // but they are interpreted sensibly, preferring the more sensible interpretation 
        if (integration) assertDatesParseToEqual("16 Dec 1 2015", "2015-12-01-1600");
        if (integration) assertDatesParseToEqual("16:30 1067 Dec 1 1066", "1067-12-01-1630 +1066");
        assertDatesParseToEqual("1040 1045 12 1", "1045-12-01-1040");
        assertDatesParseToEqual("1040 1045 12 1 +0", "1045-12-01-1040Z");
        if (integration) assertDatesParseToEqual("1045 Dec 1 1040", "1045-12-01-1040");
        if (integration) assertDatesParseToEqual("10:40 Dec 1 1045", "1045-12-01-1040");
        assertDatesParseToEqual("10.11-2020-12.01", "2020-12-01-1011");
        if (integration) assertDatesParseToEqual("Oct.11 1045 12.01", "1045-10-11-1201");
        if (integration) assertDatesParseToEqual("1040 1045 Dec 1 1030", "1045-12-01-1040 +1030");
        assertDatesParseToEqual("1040 +02 2015 12 1", "2015-12-01-1040 +0200");
        assertDatesParseToEqual("10:40:+02 2015 12 1", "2015-12-01-1040 +0200");
    }
    
    @Test
    public void testParseDateToStringWithMillisecond() {
        Date d = new Date();
        // clear seconds, but add a milli - to ensure not just toString formatting but also seconds computation
        d.setTime(d.getTime() - (d.getTime() % 60000) + 1);
        assertDatesParseToEqual(d.toString(), Time.makeDateStampString(d.getTime()));
    }

    private void assertDatesParseToEqual(String input, String expected) {
        Assert.assertEquals(Time.parseDate(input).toString(), Time.parseDate(expected).toString(), "for: "+input+" ("+expected+")");
    }
}

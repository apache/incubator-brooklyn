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
package brooklyn.util.time;

import java.util.Date;

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
    public void testMakeStringExactZero() { check(0, "0"); }
    @Test
    public void testMakeStringExactNegative() { check(-1, "-1ms"); }
    @Test
    public void testMakeStringRoundedZero() { checkR(0, "0"); }
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
}

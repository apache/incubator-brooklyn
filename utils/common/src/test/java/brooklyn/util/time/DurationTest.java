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

import java.util.concurrent.TimeUnit;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class DurationTest {

    public void testMinutes() {
        Assert.assertEquals(3*60*1000, new Duration(3, TimeUnit.MINUTES).toMilliseconds());
    }

    public void testAdd() {
        Assert.assertEquals((((4*60+3)*60)+30)*1000, 
            new Duration(3, TimeUnit.MINUTES).
                add(new Duration(4, TimeUnit.HOURS)).
                add(new Duration(30, TimeUnit.SECONDS)).
            toMilliseconds());
    }

    public void testStatics() {
        Assert.assertEquals((((4*60+3)*60)+30)*1000, 
            Duration.ONE_MINUTE.times(3).
                add(Duration.ONE_HOUR.times(4)).
                add(Duration.THIRTY_SECONDS).
            toMilliseconds());
    }

    public void testParse() {
        Assert.assertEquals((((4*60+3)*60)+30)*1000, 
                Duration.of("4h 3m 30s").toMilliseconds());
    }

    public void testConvesion() {
        Assert.assertEquals(1, Duration.nanos(1).toNanoseconds());
        Assert.assertEquals(1, Duration.nanos(1.1).toNanoseconds());
        Assert.assertEquals(1, Duration.millis(1).toMilliseconds());
        Assert.assertEquals(1, Duration.millis(1.0).toMilliseconds());
        Assert.assertEquals(1, Duration.millis(1.1).toMilliseconds());
        Assert.assertEquals(1100000, Duration.millis(1.1).toNanoseconds());
        Assert.assertEquals(500, Duration.seconds(0.5).toMilliseconds());
    }

    public void testToString() {
        Assert.assertEquals("4h 3m 30s", 
                Duration.of("4h 3m 30s").toString());
    }

    public void testToStringRounded() {
        Assert.assertEquals("4h 3m", 
                Duration.of("4h 3m 30s").toStringRounded());
    }

    public void testParseToString() {
        Assert.assertEquals(Duration.of("4h 3m 30s"), 
                Duration.parse(Duration.of("4h 3m 30s").toString()));
    }

    public void testRoundUp() {
        Assert.assertEquals(Duration.nanos(1).toMillisecondsRoundingUp(), 1); 
    }

    public void testRoundZero() {
        Assert.assertEquals(Duration.ZERO.toMillisecondsRoundingUp(), 0); 
    }

    public void testRoundUpNegative() {
        Assert.assertEquals(Duration.nanos(-1).toMillisecondsRoundingUp(), -1); 
    }

    public void testNotRounding() {
        Assert.assertEquals(Duration.nanos(-1).toMilliseconds(), 0); 
    }

    public void testNotRoundingNegative() {
        Assert.assertEquals(Duration.nanos(-1).toMillisecondsRoundingUp(), -1);
    }

}

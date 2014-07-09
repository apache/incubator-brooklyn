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

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import com.google.common.base.Stopwatch;

import brooklyn.util.exceptions.Exceptions;

@Test
public class CountdownTimerTest {

    // Test failed on jenkins when using 1 second, sleeping for 500ms; 
    // hence relaxing time constraints so is less time-sensitive at the expense of being a slower test.
    @Test(groups="Integration")
    public void testSimpleExpiry() {
        final int TOTAL_TIME_MS = 5*1000;
        final int OVERHEAD_MS = 2000;
        final int EARLY_RETURN_GRACE_MS = 30;
        final int FIRST_SLEEP_TIME_MS = 2500;
        final int SECOND_SLEEP_TIME_MS = TOTAL_TIME_MS - FIRST_SLEEP_TIME_MS + EARLY_RETURN_GRACE_MS*2;
        
        final Duration SIMPLE_DURATION = Duration.millis(TOTAL_TIME_MS);
        
        CountdownTimer timer = SIMPLE_DURATION.countdownTimer();
        assertFalse(timer.isExpired());
        assertTrue(timer.getDurationElapsed().toMilliseconds() <= OVERHEAD_MS, "elapsed="+timer.getDurationElapsed().toMilliseconds());
        assertTrue(timer.getDurationRemaining().toMilliseconds() >= TOTAL_TIME_MS - OVERHEAD_MS, "remaining="+timer.getDurationElapsed().toMilliseconds());
        
        Time.sleep(Duration.millis(FIRST_SLEEP_TIME_MS));
        assertFalse(timer.isExpired());
        assertOrdered(FIRST_SLEEP_TIME_MS - EARLY_RETURN_GRACE_MS, timer.getDurationElapsed().toMilliseconds(), FIRST_SLEEP_TIME_MS + OVERHEAD_MS);
        assertOrdered(TOTAL_TIME_MS - FIRST_SLEEP_TIME_MS - OVERHEAD_MS, timer.getDurationRemaining().toMilliseconds(), TOTAL_TIME_MS - FIRST_SLEEP_TIME_MS + EARLY_RETURN_GRACE_MS);
        
        Time.sleep(Duration.millis(SECOND_SLEEP_TIME_MS));
        assertTrue(timer.isExpired());
    }
    
    public void testNotify() throws InterruptedException {
        CountdownTimer timer = Duration.FIVE_SECONDS.countdownTimer();
        final Object mutex = new Object();
        final Semaphore gun = new Semaphore(0);
        Stopwatch watch = Stopwatch.createStarted();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try { gun.acquire(); } catch (Exception e) { throw Exceptions.propagate(e); }
                synchronized (mutex) {
                    mutex.notifyAll();
                }
            }
        }).start();
        synchronized (mutex) {
            gun.release();
            assertTrue(timer.waitOnForExpiry(mutex));
        }
        assertTrue(watch.elapsed(TimeUnit.MILLISECONDS) < 3000, "took too long: "+watch);
    }
    
    private void assertOrdered(long... vals) {
        String errmsg = "vals="+Arrays.toString(vals);
        long prevVal = Long.MIN_VALUE;
        for (long val : vals) {
            assertTrue(val >= prevVal, errmsg);
            prevVal = val;
        }
    }
    
}

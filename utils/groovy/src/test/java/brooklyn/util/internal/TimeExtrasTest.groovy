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
package brooklyn.util.internal;

import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import groovy.time.TimeDuration

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

/**
 * Test the operation of the {@link TimeExtras} class.
 * 
 * TODO clarify test purpose
 */
public class TimeExtrasTest {
    @BeforeMethod
    public void setUp() throws Exception {
        TimeExtras.init();
    }

    @Test
    public void testMultiplyTimeDurations() {
        assertEquals(new TimeDuration(6).toMilliseconds(), (new TimeDuration(3)*2).toMilliseconds());
    }

    @Test
    public void testAddTimeDurations() {
        assertEquals(new TimeDuration(0,2,5,0).toMilliseconds(), (5*SECONDS + 2*MINUTES).toMilliseconds());
    }
}

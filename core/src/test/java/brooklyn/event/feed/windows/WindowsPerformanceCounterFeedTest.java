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
package brooklyn.event.feed.windows;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.Iterator;

import static org.testng.Assert.*;

public class WindowsPerformanceCounterFeedTest {

    private static final Logger log = LoggerFactory.getLogger(WindowsPerformanceCounterFeedTest.class);

    @Test
    public void testIteratorWithSingleValue() {
        Iterator iterator = new WindowsPerformanceCounterFeed
                .PerfCounterValueIterator("\"10/14/2013 15:28:24.406\",\"0.000000\"");
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), "0.000000");
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testIteratorWithMultipleValues() {
        Iterator iterator = new WindowsPerformanceCounterFeed
                .PerfCounterValueIterator("\"10/14/2013 15:35:50.582\",\"8803.000000\",\"405622.000000\"");
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), "8803.000000");
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), "405622.000000");
        assertFalse(iterator.hasNext());
    }

}

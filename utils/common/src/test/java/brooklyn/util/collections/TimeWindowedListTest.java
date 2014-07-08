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
package brooklyn.util.collections;

import static brooklyn.util.time.Duration.ONE_MILLISECOND;
import static org.testng.Assert.assertEquals;

import java.util.List;

import org.testng.annotations.Test;

import brooklyn.util.time.Duration;

import com.google.common.collect.Lists;

@SuppressWarnings({"rawtypes","unchecked"})
public class TimeWindowedListTest {

    private static final Duration TEN_MILLISECONDS = Duration.millis(10);
    private static final Duration HUNDRED_MILLISECONDS = Duration.millis(100);
    private static final Duration TWO_MILLISECONDS = Duration.millis(2);

    @Test
    public void testKeepsMinVals() {
        TimeWindowedList list = new TimeWindowedList<Object>(MutableMap.of("timePeriod", 1L, "minVals", 2));
        assertEquals(list.getValues(2L), timestampedValues());
        
        list.add("a", 0L);
        assertEquals(list.getValues(2L), timestampedValues("a", 0L));
        
        list.add("b", 100L);
        assertEquals(list.getValues(102L), timestampedValues("a", 0L, "b", 100L));
        
        list.add("c", 200L);
        assertEquals(list.getValues(202L), timestampedValues("b", 100L, "c", 200L));
    }
    
    @Test
    public void testKeepsOnlyRecentVals() {
        TimeWindowedList list = new TimeWindowedList<Object>(MutableMap.of("timePeriod", 1000L));
        
        list.add("a", 0L);
        list.add("b", 100L);
        assertEquals(list.getValues(1000L), timestampedValues("a", 0L, "b", 100L));
        assertEquals(list.getValues(1100L), timestampedValues("b", 100L));
        assertEquals(list.getValues(1101L), Lists.newArrayList());
    }
    
    @Test
    public void testKeepsMinExpiredVals() {
        TimeWindowedList list = new TimeWindowedList<Object>(MutableMap.of("timePeriod", 1000L, "minExpiredVals", 1));
        
        list.add("a", 0L);
        list.add("b", 100L);
        assertEquals(list.getValues(1001L), timestampedValues("a", 0L, "b", 100L));
        assertEquals(list.getValues(1101L), timestampedValues("b", 100L));
    }
    
    @Test
    public void testGetsSubSetOfRecentVals() {
        TimeWindowedList list = new TimeWindowedList<Object>(Duration.ONE_SECOND);
        
        list.add("a", 0L);
        list.add("b", 100L);
        assertEquals(list.getValuesInWindow(100L, HUNDRED_MILLISECONDS), timestampedValues("a", 0L, "b", 100L));
        assertEquals(list.getValuesInWindow(101L, ONE_MILLISECOND), timestampedValues("b", 100L));
    }
    
    @Test
    public void testGetsSubSetOfValsIncludingOneMinExpiredVal() {
        TimeWindowedList list = new TimeWindowedList<Object>(MutableMap.of("timePeriod", 1000L, "minExpiredVals", 1));
        
        list.add("a", 0L);
        list.add("b", 100L);
        assertEquals(list.getValuesInWindow(100L, HUNDRED_MILLISECONDS), timestampedValues("a", 0L, "b", 100L));
        assertEquals(list.getValuesInWindow(101L, TWO_MILLISECONDS), timestampedValues("a", 0L, "b", 100L));
        assertEquals(list.getValuesInWindow(102L, ONE_MILLISECOND), timestampedValues("b", 100L));
        assertEquals(list.getValuesInWindow(1001L, ONE_MILLISECOND), timestampedValues("b", 100L));
    }
    
    @Test
    public void testGetsWindowWithMinWhenEmpty() {
        TimeWindowedList list = new TimeWindowedList<Object>(MutableMap.of("timePeriod", 1L, "minVals", 1));
        assertEquals(list.getValuesInWindow(1000L, TEN_MILLISECONDS), timestampedValues());
    }

    @Test
    public void testGetsWindowWithMinExpiredWhenEmpty() {
        TimeWindowedList list = new TimeWindowedList<Object>(MutableMap.of("timePeriod", 1L, "minExpiredVals", 1));
        assertEquals(list.getValuesInWindow(1000L, TEN_MILLISECONDS), timestampedValues());
    }

    @Test
    public void testGetsWindowWithMinValsWhenExpired() {
        TimeWindowedList list = new TimeWindowedList<Object>(MutableMap.of("timePeriod", 1L, "minVals", 1));
        list.add("a", 0L);
        list.add("b", 1L);
        
        assertEquals(list.getValuesInWindow(1000L, TEN_MILLISECONDS), timestampedValues("b", 1L));
    }

    @Test
    public void testZeroSizeWindowWithOneExpiredContainsOnlyMostRecentValue() {
        TimeWindowedList list = new TimeWindowedList<Object>(MutableMap.of("timePeriod", 0L, "minExpiredVals", 1));
        
        list.add("a", 0L);
        assertEquals(list.getValuesInWindow(0L, HUNDRED_MILLISECONDS), timestampedValues("a", 0L));
        assertEquals(list.getValuesInWindow(2L, ONE_MILLISECOND), timestampedValues("a", 0L));
        
        list.add("b", 100L);
        assertEquals(list.getValuesInWindow(100L, ONE_MILLISECOND), timestampedValues("b", 100L));
        assertEquals(list.getValuesInWindow(102L, ONE_MILLISECOND), timestampedValues("b", 100L));
    }
    
    private <T> List<TimestampedValue<T>> timestampedValues() {
        return Lists.newArrayList();
    }
    
    private <T> List<TimestampedValue<T>> timestampedValues(T v1, long t1) {
        return Lists.newArrayList(new TimestampedValue<T>(v1, t1));
    }
    
    private <T> List<TimestampedValue<T>> timestampedValues(T v1, long t1, T v2, long t2) {
        return Lists.newArrayList(new TimestampedValue<T>(v1, t1), new TimestampedValue<T>(v2, t2));
    }
}

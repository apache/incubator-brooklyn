package brooklyn.util;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

class TimeWindowedListTest {

    @Test
    public void testKeepsMinVals() {
        TimeWindowedList list = new TimeWindowedList<Object>([timePeriod:1L, minVals:2]);
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
        TimeWindowedList list = new TimeWindowedList<Object>([timePeriod:1000L]);
        
        list.add("a", 0L);
        list.add("b", 100L);
        assertEquals(list.getValues(1000L), timestampedValues("a", 0L, "b", 100L));
        assertEquals(list.getValues(1100L), timestampedValues("b", 100L));
        assertEquals(list.getValues(1101L), []);
    }
    
    @Test
    public void testKeepsMinExpiredVals() {
        TimeWindowedList list = new TimeWindowedList<Object>([timePeriod:1000L, minExpiredVals:1]);
        
        list.add("a", 0L);
        list.add("b", 100L);
        assertEquals(list.getValues(1001L), timestampedValues("a", 0L, "b", 100L));
        assertEquals(list.getValues(1101L), timestampedValues("b", 100L));
    }
    
    @Test
    public void testGetsSubSetOfRecentVals() {
        TimeWindowedList list = new TimeWindowedList<Object>(1000L);
        
        list.add("a", 0L);
        list.add("b", 100L);
        assertEquals(list.getValuesInWindow(100L, 100L), timestampedValues("a", 0L, "b", 100L));
        assertEquals(list.getValuesInWindow(101L, 1L), timestampedValues("b", 100L));
    }
    
    @Test
    public void testGetsSubSetOfValsIncludingOneMinExpiredVal() {
        TimeWindowedList list = new TimeWindowedList<Object>([timePeriod:1000L, minExpiredVals:1]);
        
        list.add("a", 0L);
        list.add("b", 100L);
        assertEquals(list.getValuesInWindow(100L, 100L), timestampedValues("a", 0L, "b", 100L));
        assertEquals(list.getValuesInWindow(101L, 2L), timestampedValues("a", 0L, "b", 100L));
        assertEquals(list.getValuesInWindow(102L, 1L), timestampedValues("b", 100L));
        assertEquals(list.getValuesInWindow(1001L, 1L), timestampedValues("b", 100L));
    }
    
    @Test
    public void testGetsWindowWithMinWhenEmpty() {
        TimeWindowedList list = new TimeWindowedList<Object>([timePeriod:1L, minVals:1]);
        assertEquals(list.getValuesInWindow(1000L, 10L), timestampedValues());
    }

    @Test
    public void testGetsWindowWithMinExpiredWhenEmpty() {
        TimeWindowedList list = new TimeWindowedList<Object>([timePeriod:1L, minExpiredVals:1]);
        assertEquals(list.getValuesInWindow(1000L, 10L), timestampedValues());
    }

    @Test
    public void testGetsWindowWithMinValsWhenExpired() {
        TimeWindowedList list = new TimeWindowedList<Object>([timePeriod:1L, minVals:1]);
        list.add("a", 0L);
        list.add("b", 1L);
        
        assertEquals(list.getValuesInWindow(1000L, 10L), timestampedValues("b", 1L));
    }

    @Test
    public void testZeroSizeWindowWithOneExpiredContainsOnlyMostRecentValue() {
        TimeWindowedList list = new TimeWindowedList<Object>([timePeriod:0L, minExpiredVals:1]);
        
        list.add("a", 0L);
        assertEquals(list.getValuesInWindow(0L, 100L), timestampedValues("a", 0L));
        assertEquals(list.getValuesInWindow(2L, 1L), timestampedValues("a", 0L));
        
        list.add("b", 100L);
        assertEquals(list.getValuesInWindow(100L, 1L), timestampedValues("b", 100L));
        assertEquals(list.getValuesInWindow(102L, 1L), timestampedValues("b", 100L));
    }
    
    private <T> List<TimestampedValue<T>> timestampedValues() {
        return [];
    }
    
    private <T> List<TimestampedValue<T>> timestampedValues(T v1, long t1) {
        return [new TimestampedValue(v1, t1)];
    }
    
    private <T> List<TimestampedValue<T>> timestampedValues(T v1, long t1, T v2, long t2) {
        return [new TimestampedValue(v1, t1), new TimestampedValue(v2, t2)];
    }
}

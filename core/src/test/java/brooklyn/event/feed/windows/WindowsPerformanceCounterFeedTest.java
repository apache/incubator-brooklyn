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

package brooklyn.util.internal;

import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import groovy.time.TimeDuration

import org.testng.annotations.BeforeTest
import org.testng.annotations.Test

/**
 * Test the operation of the {@link TimeExtras} class.
 * 
 * TODO clarify test purpose
 */
public class TimeExtrasTest {
    @BeforeTest
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

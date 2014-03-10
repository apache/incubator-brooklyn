package brooklyn.util.time;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;

import org.testng.annotations.Test;

@Test
public class CountdownTimerIntegrationTest {

    // Test failed on jenkins when using 1 second, sleeping for 500ms; 
    // hence relaxing time constraints so is less time-sensitive at the expense of being a slower test.
    @Test(groups="Integration")
    public void testSimple() {
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
    
    private void assertOrdered(long... vals) {
        String errmsg = "vals="+Arrays.toString(vals);
        long prevVal = Long.MIN_VALUE;
        for (long val : vals) {
            assertTrue(val >= prevVal, errmsg);
            prevVal = val;
        }
    }
    
}

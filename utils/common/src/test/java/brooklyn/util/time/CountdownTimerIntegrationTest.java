package brooklyn.util.time;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test(groups="Integration")
public class CountdownTimerIntegrationTest {

    @Test
    public void testSimple() {
        final Duration SIMPLE_DURATION = Duration.seconds(1);
        
        CountdownTimer timer = SIMPLE_DURATION.countdownTimer();
        Assert.assertFalse(timer.isExpired());
        Assert.assertTrue(timer.getDurationElapsed().toMilliseconds() < SIMPLE_DURATION.half().toMilliseconds());
        Assert.assertTrue(timer.getDurationRemaining().toMilliseconds() > SIMPLE_DURATION.half().toMilliseconds());
        Time.sleep(SIMPLE_DURATION.half());
        Assert.assertFalse(timer.isExpired());
        Assert.assertTrue(timer.getDurationElapsed().toMilliseconds() > SIMPLE_DURATION.half().toMilliseconds());
        Assert.assertTrue(timer.getDurationRemaining().toMilliseconds() < SIMPLE_DURATION.half().toMilliseconds());
        Time.sleep(SIMPLE_DURATION.half().add(Duration.millis(1)));
        Assert.assertTrue(timer.isExpired());
    }
    
}

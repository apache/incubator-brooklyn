package brooklyn.util.internal;

import static java.util.concurrent.TimeUnit.*
import groovy.time.TimeDuration

import org.junit.Assert
import org.junit.Before
import org.junit.Test

public class TimeExtrasTest {
    @Before
    public void setUp() throws Exception {
        TimeExtras.init();
    }

    @Test
    public void testMultiplyTimeDurations() {
        Assert.assertEquals(new TimeDuration(6).toMilliseconds(), (new TimeDuration(3)*2).toMilliseconds());
    }

    @Test
    public void testAddTimeDurations() {
        Assert.assertEquals(new TimeDuration(0,2,5,0).toMilliseconds(), (5*SECONDS + 2*MINUTES).toMilliseconds());
    }
}

package brooklyn.util.time;

import java.util.concurrent.TimeUnit;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class DurationTest {

    public void testMinutes() {
        Assert.assertEquals(3*60*1000, new Duration(3, TimeUnit.MINUTES).toMilliseconds());
    }

    public void testAdd() {
        Assert.assertEquals((((4*60+3)*60)+30)*1000, 
            new Duration(3, TimeUnit.MINUTES).
                add(new Duration(4, TimeUnit.HOURS)).
                add(new Duration(30, TimeUnit.SECONDS)).
            toMilliseconds());
    }

    public void testStatics() {
        Assert.assertEquals((((4*60+3)*60)+30)*1000, 
            Duration.ONE_MINUTE.times(3).
                add(Duration.ONE_HOUR.times(4)).
                add(Duration.THIRTY_SECONDS).
            toMilliseconds());
    }

    public void testParse() {
        Assert.assertEquals((((4*60+3)*60)+30)*1000, 
                Duration.of("4h 3m 30s").toMilliseconds());
    }

    public void testToString() {
        Assert.assertEquals("4h 3m 30s", 
                Duration.of("4h 3m 30s").toString());
    }

    public void testToStringRounded() {
        Assert.assertEquals("4h 3m", 
                Duration.of("4h 3m 30s").toStringRounded());
    }

    public void testParseToString() {
        Assert.assertEquals(Duration.of("4h 3m 30s"), 
                Duration.parse(Duration.of("4h 3m 30s").toString()));
    }

    public void testRoundUp() {
        Assert.assertEquals(Duration.nanos(1).toMillisecondsRoundingUp(), 1); 
    }

    public void testRoundZero() {
        Assert.assertEquals(Duration.ZERO.toMillisecondsRoundingUp(), 0); 
    }

    public void testRoundUpNegative() {
        Assert.assertEquals(Duration.nanos(-1).toMillisecondsRoundingUp(), -1); 
    }

    public void testNotRounding() {
        Assert.assertEquals(Duration.nanos(-1).toMilliseconds(), 0); 
    }

    public void testNotRoundingNegative() {
        Assert.assertEquals(Duration.nanos(-1).toMillisecondsRoundingUp(), -1);
    }

}

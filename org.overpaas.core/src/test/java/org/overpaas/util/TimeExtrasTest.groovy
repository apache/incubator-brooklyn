package org.overpaas.util;

import static java.util.concurrent.TimeUnit.MINUTES
import static java.util.concurrent.TimeUnit.SECONDS
import groovy.time.TimeDuration
import groovy.util.GroovyTestCase
import junit.framework.Assert

import org.junit.Before
import org.junit.Test

public class TimeExtrasTest extends GroovyTestCase {

    public void testX() {}
    
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

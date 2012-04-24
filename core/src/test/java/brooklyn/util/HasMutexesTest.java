package brooklyn.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.HasMutexes.MutexSupport;
import brooklyn.util.HasMutexes.SemaphoreWithOwners;

public class HasMutexesTest {

    @Test
    public void testOneAcquisitionAndRelease() throws InterruptedException {
        MutexSupport m = new MutexSupport();
        Map<String, SemaphoreWithOwners> sems;
        SemaphoreWithOwners s;
        try {
            m.acquireMutex("foo", "something foo");
            sems = m.getAllSemaphores();
            Assert.assertEquals(sems.size(), 1);
            s = sems.get("foo");
            Assert.assertEquals(s.getDescription(), "something foo");
            Assert.assertEquals(s.getOwningThreads(), Arrays.asList(Thread.currentThread()));
            Assert.assertEquals(s.getRequestingThreads(), Collections.emptyList());
            Assert.assertTrue(s.isInUse());
            Assert.assertTrue(s.isCallingThreadAnOwner());
        } finally {
            m.releaseMutex("foo");
        }
        Assert.assertFalse(s.isInUse());
        Assert.assertFalse(s.isCallingThreadAnOwner());
        Assert.assertEquals(s.getDescription(), "something foo");
        Assert.assertEquals(s.getOwningThreads(), Collections.emptyList());
        Assert.assertEquals(s.getRequestingThreads(), Collections.emptyList());
        
        sems = m.getAllSemaphores();
        Assert.assertEquals(sems, Collections.emptyMap());
    }

    @Test(groups = "Integration")  //just because it takes a wee while
    public void testBlockingAcquisition() throws InterruptedException {
        final MutexSupport m = new MutexSupport();
        m.acquireMutex("foo", "something foo");
        
        Assert.assertFalse(m.tryAcquireMutex("foo", "something else"));

        Thread t = new Thread() {
            public void run() {
                try {
                    m.acquireMutex("foo", "thread 2 foo");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                m.releaseMutex("foo");
            }
        };
        t.start();
        
        t.join(500);
        Assert.assertTrue(t.isAlive());
        Assert.assertEquals(m.getSemaphore("foo").getRequestingThreads(), Arrays.asList(t));

        m.releaseMutex("foo");
        
        t.join(1000);
        Assert.assertFalse(t.isAlive());

        Assert.assertEquals(m.getAllSemaphores(), Collections.emptyMap());
    }

}

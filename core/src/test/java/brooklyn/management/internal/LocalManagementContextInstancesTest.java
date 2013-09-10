package brooklyn.management.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;

/**
 * Tests the {@link LocalManagementContext#terminateAll()} and {@link LocalManagementContext#getInstances()} behaviour.
 * Note this test must NEVER be run in parallel with other tests, as it will terminate the ManagementContext of those
 * other tests.
 * 
 * @author pveentjer
 */
public class LocalManagementContextInstancesTest {

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
         LocalManagementContext.terminateAll();
    }

    /** WIP group because other threads may be running in background, 
     * creating management contexts at the same time as us (slim chance, but observed once);
     * they shouldn't be, but cleaning that up is another matter! */
    @Test(groups="WIP")
    public void testGetInstances(){
        LocalManagementContext.terminateAll();
        LocalManagementContext context1 = new LocalManagementContext();
        LocalManagementContext context2 = new LocalManagementContext();
        LocalManagementContext context3 = new LocalManagementContext();

        assertEquals(LocalManagementContext.getInstances(), ImmutableSet.of(context1, context2, context3));
    }

    /** WIP group because other threads may be running in background;
     * they shouldn't be, but cleaning that up is another matter! */
    @Test
    public void terminateAll(){
        LocalManagementContext.terminateAll();
        
        LocalManagementContext context1 = new LocalManagementContext();
        LocalManagementContext context2 = new LocalManagementContext();

        LocalManagementContext.terminateAll();

        assertTrue(LocalManagementContext.getInstances().isEmpty());
        assertFalse(context1.isRunning());
        assertFalse(context2.isRunning());
    }

    @Test
    public void terminateExplicitContext(){
        LocalManagementContext context1 = new LocalManagementContext();
        LocalManagementContext context2 = new LocalManagementContext();
        LocalManagementContext context3 = new LocalManagementContext();

        context2.terminate();

        Assert.assertFalse(LocalManagementContext.getInstances().contains(context2));
        Assert.assertTrue(LocalManagementContext.getInstances().contains(context1));
        Assert.assertTrue(LocalManagementContext.getInstances().contains(context3));
    }
}

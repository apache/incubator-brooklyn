package brooklyn.management.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

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
    public void setUp() {
        LocalManagementContext.terminateAll();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
         LocalManagementContext.terminateAll();
    }

    @Test
    public void testGetInstances(){
        LocalManagementContext context1 = new LocalManagementContext();
        LocalManagementContext context2 = new LocalManagementContext();
        LocalManagementContext context3 = new LocalManagementContext();

        assertEquals(LocalManagementContext.getInstances(), ImmutableSet.of(context1, context2, context3));
    }

    @Test
    public void terminateAll(){
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

        assertEquals(LocalManagementContext.getInstances(), ImmutableSet.of(context1, context3));
    }
}

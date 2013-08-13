package brooklyn.management.internal;

import brooklyn.util.collections.MutableList;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class LocalManagementContextInstancesTest {

    @AfterMethod(alwaysRun = true)
    public void beforeMethod(){
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

        assertEquals(LocalManagementContext.getInstances(), MutableList.of(context1, context2, context3));
    }

    @Test
    public void terminateAll(){
        LocalManagementContext context1 = new LocalManagementContext();
        LocalManagementContext context2 = new LocalManagementContext();
        LocalManagementContext context3 = new LocalManagementContext();

        LocalManagementContext.terminateAll();

        assertTrue(LocalManagementContext.getInstances().isEmpty());
    }

    @Test
    public void terminateExplicitContext(){
        LocalManagementContext context1 = new LocalManagementContext();
        LocalManagementContext context2 = new LocalManagementContext();
        LocalManagementContext context3 = new LocalManagementContext();

        context2.terminate();

        assertEquals(LocalManagementContext.getInstances(), MutableList.of(context1, context3));
    }
}

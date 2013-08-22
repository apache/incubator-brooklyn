package brooklyn.util.task.system;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.task.ssh.SshTasks;

import com.google.common.io.Files;

/**
 * Some tests for {@link SystemTasks}. See {@link SshTasks}.
 */
public class SystemTasksTest {

    ManagementContext mgmt;
    File tempDir;
    
    boolean failureExpected;

    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        mgmt = new LocalManagementContext();
        
        clearExpectedFailure();
        tempDir = Files.createTempDir();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
        mgmt = null;
        FileUtils.deleteDirectory(tempDir);
        checkExpectedFailure();
    }

    protected void checkExpectedFailure() {
        if (failureExpected) {
            clearExpectedFailure();
            Assert.fail("Test should have thrown an exception but it did not.");
        }
    }
    
    protected void clearExpectedFailure() {
        failureExpected = false;
    }

    protected void setExpectingFailure() {
        failureExpected = true;
    }


    protected <T> ProcessTaskWrapper<T> submit(final ProcessTaskFactory<T> tf) {
        ProcessTaskWrapper<T> t = tf.newTask();
        mgmt.getExecutionManager().submit(t);
        return t;
    }

    @Test(groups="Integration")
    public void testExecEchoHello() {
        ProcessTaskWrapper<Integer> t = submit(SystemTasks.exec("sleep 1 ; echo hello world"));
        Assert.assertFalse(t.isDone());
        Assert.assertEquals(t.get(), (Integer)0);
        Assert.assertEquals(t.getTask().getUnchecked(), (Integer)0);
        Assert.assertEquals(t.getStdout().trim(), "hello world");
    }

}

package brooklyn.util.task.ssh;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.software.SoftwareEffectorTest;
import brooklyn.entity.software.SshEffectorTasksTest;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.net.Urls;
import brooklyn.util.ssh.BashCommandsIntegrationTest;

import com.google.common.io.Files;

/**
 * Some tests for {@link SshTasks}. Note more tests in {@link BashCommandsIntegrationTest}, 
 * {@link SshEffectorTasksTest}, and {@link SoftwareEffectorTest}.
 */
public class SshTasksTest {

    private static final Logger log = LoggerFactory.getLogger(SshTasksTest.class);
    
    ManagementContext mgmt;
    SshMachineLocation host;
    File tempDir;
    
    boolean failureExpected;

    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        mgmt = new LocalManagementContext();
        
        LocalhostMachineProvisioningLocation lhc = mgmt.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        host = lhc.obtain();
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


    protected <T> SshTaskWrapper<T> submit(final SshTaskFactory<T> tf) {
        tf.machine(host);
        SshTaskWrapper<T> t = tf.newTask();
        mgmt.getExecutionManager().submit(t);
        return t;
    }

    protected SshPutTaskWrapper submit(final SshPutTaskFactory tf) {
        SshPutTaskWrapper t = tf.newTask();
        mgmt.getExecutionManager().submit(t);
        return t;
    }

    @Test(groups="Integration")
    public void testSshEchoHello() {
        SshTaskWrapper<Integer> t = submit(SshTasks.newSshTaskFactory(host, "sleep 1 ; echo hello world"));
        Assert.assertFalse(t.isDone());
        Assert.assertEquals(t.get(), (Integer)0);
        Assert.assertEquals(t.getTask().getUnchecked(), (Integer)0);
        Assert.assertEquals(t.getStdout().trim(), "hello world");
    }

    @Test(groups="Integration")
    public void testCopyTo() throws IOException {
        String fn = Urls.mergePaths(tempDir.getPath(), "f1");
        SshPutTaskWrapper t = submit(SshTasks.newSshPutTaskFactory(host, fn).contents("hello world"));
        t.block();
        Assert.assertEquals(FileUtils.readFileToString(new File(fn)), "hello world");
        // and make sure this doesn't throw
        Assert.assertTrue(t.isDone());
        Assert.assertTrue(t.isSuccessful());
        Assert.assertEquals(t.get(), null);
        Assert.assertEquals(t.getExitCode(), (Integer)0);
    }
    
    @Test(groups="Integration")
    public void testCopyToFailBadSubdir() throws IOException {
        String fn = Urls.mergePaths(tempDir.getPath(), "non-existent-subdir/file");
        SshPutTaskWrapper t = submit(SshTasks.newSshPutTaskFactory(host, fn).contents("hello world"));
        //this doesn't fail
        t.block();        
        Assert.assertTrue(t.isDone());
        setExpectingFailure();
        try {
            // but this does
            t.get();
        } catch (Exception e) {
            log.info("The error if file cannot be written is: "+e);
            clearExpectedFailure();
        }
        checkExpectedFailure();
        // and the results indicate failure
        Assert.assertFalse(t.isSuccessful());
        Assert.assertNotNull(t.getException());
        Assert.assertNotEquals(t.getExitCode(), (Integer)0);
    }

    @Test(groups="Integration")
    public void testCopyToFailBadSubdirAllow() throws IOException {
        String fn = Urls.mergePaths(tempDir.getPath(), "non-existent-subdir/file");
        SshPutTaskWrapper t = submit(SshTasks.newSshPutTaskFactory(host, fn).contents("hello world").allowFailure());
        //this doesn't fail
        t.block();        
        Assert.assertTrue(t.isDone());
        // and this doesn't fail either
        Assert.assertEquals(t.get(), null);
        // but it's not successful
        Assert.assertNotNull(t.getException());
        Assert.assertFalse(t.isSuccessful());
        // exit code probably null, but won't be zero
        Assert.assertNotEquals(t.getExitCode(), (Integer)0);
    }

    @Test(groups="Integration")
    public void testCopyToFailBadSubdirCreate() throws IOException {
        String fn = Urls.mergePaths(tempDir.getPath(), "non-existent-subdir-to-create/file");
        SshPutTaskWrapper t = submit(SshTasks.newSshPutTaskFactory(host, fn).contents("hello world").createDirectory());
        t.block();
        // directory should be created, and file readable now
        Assert.assertEquals(FileUtils.readFileToString(new File(fn)), "hello world");
        Assert.assertEquals(t.getExitCode(), (Integer)0);
    }

    @Test(groups="Integration")
    public void testSshFetch() throws IOException {
        String fn = Urls.mergePaths(tempDir.getPath(), "f2");
        FileUtils.write(new File(fn), "hello fetched world");
        
        SshFetchTaskFactory tf = SshTasks.newSshFetchTaskFactory(host, fn);
        SshFetchTaskWrapper t = tf.newTask();
        mgmt.getExecutionManager().submit(t);

        t.block();
        Assert.assertTrue(t.isDone());
        Assert.assertEquals(t.get(), "hello fetched world");
        Assert.assertEquals(t.getBytes(), "hello fetched world".getBytes());
    }

}

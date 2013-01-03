package brooklyn.event.feed.shell;

import static org.testng.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.feed.function.FunctionFeedTest;
import brooklyn.event.feed.ssh.SshValueFunctions;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closeables;

public class ShellFeedIntegrationTest {

    final static BasicAttributeSensor<String> SENSOR_STRING = new BasicAttributeSensor<String>(String.class, "aString", "");
    final static BasicAttributeSensor<Integer> SENSOR_INT = new BasicAttributeSensor<Integer>(Integer.class, "aLong", "");

    private LocalhostMachineProvisioningLocation loc;
    private TestApplication app;
    private EntityLocal entity;
    private ShellFeed feed;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        loc = new LocalhostMachineProvisioningLocation();
        app = new TestApplication();        
        entity = new TestEntity(app);
        Entities.startManagement(app);
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (feed != null) feed.stop();
        if (app != null) Entities.destroy(app);
        if (loc != null) Closeables.closeQuietly(loc);
    }
    
    @Test(groups="Integration")
    public void testReturnsShellExitStatus() throws Exception {
        feed = ShellFeed.builder()
                .entity(entity)
                .poll(new ShellPollConfig<Integer>(SENSOR_INT)
                        .command("exit 123")
                        .onSuccess(SshValueFunctions.exitStatus()))
                .build();

        EntityTestUtils.assertAttributeEqualsEventually(entity, SENSOR_INT, 123);
    }
    
    @Test(groups="Integration")
    public void testShellTimesOut() throws Exception {
        feed = ShellFeed.builder()
                .entity(entity)
                .poll(new ShellPollConfig<String>(SENSOR_STRING)
                        .command("sleep 10")
                        .timeout(1, TimeUnit.MILLISECONDS)
                        .onError(new FunctionFeedTest.ToStringFunction()))
                .build();

        TestUtils.executeUntilSucceeds(MutableMap.of(), new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("timed out after 1ms"), "val="+val);
            }});
    }
    
    @Test(groups="Integration")
    public void testShellUsesEnv() throws Exception {
        feed = ShellFeed.builder()
                .entity(entity)
                .poll(new ShellPollConfig<String>(SENSOR_STRING)
                        .env(ImmutableMap.of("MYENV", "MYVAL"))
                        .command("echo hello $MYENV")
                        .onSuccess(SshValueFunctions.stdout()))
                .build();
        
        TestUtils.executeUntilSucceeds(MutableMap.of(), new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("hello MYVAL"), "val="+val);
            }});
    }
    
    @Test(groups="Integration")
    public void testReturnsShellStdout() throws Exception {
        feed = ShellFeed.builder()
                .entity(entity)
                .poll(new ShellPollConfig<String>(SENSOR_STRING)
                        .command("echo hello")
                        .onSuccess(SshValueFunctions.stdout()))
                .build();
        
        TestUtils.executeUntilSucceeds(MutableMap.of(), new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("hello"), "val="+val);
            }});
    }

    @Test(groups="Integration")
    public void testReturnsShellStderr() throws Exception {
        final String cmd = "thiscommanddoesnotexist";
        
        feed = ShellFeed.builder()
                .entity(entity)
                .poll(new ShellPollConfig<String>(SENSOR_STRING)
                        .command(cmd)
                        .onSuccess(SshValueFunctions.stderr()))
                .build();
        
        TestUtils.executeUntilSucceeds(MutableMap.of(), new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains(cmd), "val="+val);
            }});
    }
    
    @Test(groups="Integration")
    public void testFailsOnNonZeroWhenConfigured() throws Exception {
        feed = ShellFeed.builder()
                .entity(entity)
                .poll(new ShellPollConfig<String>(SENSOR_STRING)
                        .command("exit 123")
                        .failOnNonZeroResultCode(true)
                        .onError(new FunctionFeedTest.ToStringFunction()))
                .build();
        
        TestUtils.executeUntilSucceeds(MutableMap.of(), new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("Exit status 123"), "val="+val);
            }});
    }
}

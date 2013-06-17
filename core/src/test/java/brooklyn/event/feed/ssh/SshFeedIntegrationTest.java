package brooklyn.event.feed.ssh;

import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.event.feed.function.FunctionFeedTest;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.Asserts;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;

public class SshFeedIntegrationTest {

    final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString", "");
    final static AttributeSensor<Integer> SENSOR_INT = Sensors.newIntegerSensor("aLong", "");

    private LocalhostMachineProvisioningLocation loc;
    private SshMachineLocation machine;
    private TestApplication app;
    private EntityLocal entity;
    private SshFeed feed;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        loc = new LocalhostMachineProvisioningLocation();
        machine = loc.obtain();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (feed != null) feed.stop();
        if (app != null) Entities.destroyAll(app);
        if (loc != null) Closeables.closeQuietly(loc);
    }
    
    @Test(groups="Integration")
    public void testReturnsSshExitStatus() throws Exception {
        feed = SshFeed.builder()
                .entity(entity)
                .machine(machine)
                .poll(new SshPollConfig<Integer>(SENSOR_INT)
                        .command("exit 123")
                        .onSuccess(SshValueFunctions.exitStatus()))
                .build();

        EntityTestUtils.assertAttributeEqualsEventually(entity, SENSOR_INT, 123);
    }
    
    @Test(groups="Integration")
    public void testReturnsSshStdout() throws Exception {
        feed = SshFeed.builder()
                .entity(entity)
                .machine(machine)
                .poll(new SshPollConfig<String>(SENSOR_STRING)
                        .command("echo hello")
                        .onSuccess(SshValueFunctions.stdout()))
                .build();
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("hello"), "val="+val);
            }});
    }

    @Test(groups="Integration")
    public void testReturnsSshStderr() throws Exception {
        final String cmd = "thiscommanddoesnotexist";
        
        feed = SshFeed.builder()
                .entity(entity)
                .machine(machine)
                .poll(new SshPollConfig<String>(SENSOR_STRING)
                        .command(cmd)
                        .onSuccess(SshValueFunctions.stderr()))
                .build();
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains(cmd), "val="+val);
            }});
    }
    
    @Test(groups="Integration")
    public void testFailsOnNonZeroWhenConfigured() throws Exception {
        feed = SshFeed.builder()
                .entity(entity)
                .machine(machine)
                .poll(new SshPollConfig<String>(SENSOR_STRING)
                        .command("exit 123")
                        .failOnNonZeroResultCode(true)
                        .onFailure(new FunctionFeedTest.ToStringFunction()))
                .build();
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("Exit status 123"), "val=" + val);
            }});
    }
}

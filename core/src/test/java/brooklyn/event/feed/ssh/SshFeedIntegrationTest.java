package brooklyn.event.feed.ssh;

import static org.testng.Assert.assertTrue;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntityInitializer;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.stream.Streams;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

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
        app = ApplicationBuilder.newManagedApp(TestApplication.class, new LocalManagementContextForTests());
        loc = app.newLocalhostProvisioningLocation();
        machine = loc.obtain();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (feed != null) feed.stop();
        if (app != null) Entities.destroyAll(app.getManagementContext());
        if (loc != null) Streams.closeQuietly(loc);
    }
    
    /** this is one of the most common pattern */
    @Test(groups="Integration")
    public void testReturnsSshStdoutAndInfersMachine() throws Exception {
        final TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class)
            // inject the machine location, because the app was started with a provisioning location
            // and TestEntity doesn't provision
            .location(machine));
        
        feed = SshFeed.builder()
                .entity(entity2)
                .poll(new SshPollConfig<String>(SENSOR_STRING)
                        .command("echo hello")
                        .onSuccess(SshValueFunctions.stdout()))
                .build();
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity2.getAttribute(SENSOR_STRING);
                assertTrue(val != null);
            }});
        
        String val = entity2.getAttribute(SENSOR_STRING);
        Assert.assertTrue(val.contains("hello"), "val="+val);
        Assert.assertEquals(val.trim(), "hello");
    }

    @Test(groups="Integration")
    public void testReturnsSshExitStatus() throws Exception {
        feed = SshFeed.builder()
                .entity(entity)
                .machine(machine)
                .poll(new SshPollConfig<Integer>(SENSOR_INT)
                        .command("exit 123")
                        .checkSuccess(Predicates.alwaysTrue())
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
                        .onFailure(SshValueFunctions.stderr()))
                .build();
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains(cmd), "val="+val);
            }});
    }
    
    @Test(groups="Integration")
    public void testFailsOnNonZero() throws Exception {
        feed = SshFeed.builder()
                .entity(entity)
                .machine(machine)
                .poll(new SshPollConfig<String>(SENSOR_STRING)
                        .command("exit 123")
                        .onFailure(new Function<SshPollValue, String>() {
                            @Override
                            public String apply(SshPollValue input) {
                                return "Exit status " + input.getExitStatus();
                            }}))
                .build();
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("Exit status 123"), "val=" + val);
            }});
    }
    
    @Test(groups="Integration")
    public void testAddedEarly() throws Exception {
        final TestEntity entity2 = app.addChild(EntitySpec.create(TestEntity.class)
            .location(machine)
            .addInitializer(new EntityInitializer() {
                @Override
                public void apply(EntityLocal entity) {
                    SshFeed.builder()
                        .entity(entity)
                        .onlyIfServiceUp()
                        .poll(new SshPollConfig<String>(SENSOR_STRING)
                            .command("echo hello")
                            .onSuccess(SshValueFunctions.stdout()))
                        .build();
                }
            }));
        Time.sleep(Duration.seconds(2));
        // would be nice to hook in and assert no errors
        Assert.assertEquals(entity2.getAttribute(SENSOR_STRING), null);
        Entities.manage(entity2);
        Time.sleep(Duration.seconds(2));
        Assert.assertEquals(entity2.getAttribute(SENSOR_STRING), null);
        entity2.setAttribute(Attributes.SERVICE_UP, true);
    
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity2.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("hello"), "val="+val);
            }});
    }

}

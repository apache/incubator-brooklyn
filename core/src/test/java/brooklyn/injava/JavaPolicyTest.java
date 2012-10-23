package brooklyn.injava;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.event.SensorEvent;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.MutableMap;

import com.google.common.collect.ImmutableList;

public class JavaPolicyTest {

    TestApplication app;
    ExampleJavaEntity entity;

    @BeforeMethod
    public void setUp() throws Exception {
        app = new TestApplication();
        entity = new ExampleJavaEntity(app);
        app.startManagement();
        app.start(ImmutableList.of(new SimulatedLocation()));
    }
    
    @AfterMethod
    public void tearDown() throws Exception {
        app.stop();
    }
    
    @Test
    public void testPolicySubscribesToEvents() {
        final ExampleJavaPolicy policy = new ExampleJavaPolicy();
        entity.addPolicy(policy);
        
        entity.setAttribute(ExampleJavaEntity.MY_SENSOR1, "val1");
        
        TestUtils.executeUntilSucceeds(new Runnable() {
            @Override public void run() {
                SensorEvent<String> expected = new BasicSensorEvent<String>(ExampleJavaEntity.MY_SENSOR1, entity, "val1");
                assertEquals(policy.eventsReceived, ImmutableList.of(expected));
            }
        });
    }
    
    @Test
    public void testCanSetConfig() {
        final ExampleJavaPolicy policy = new ExampleJavaPolicy(MutableMap.of("displayName", "myName", "myConfig1", "myVal1"));
        entity.addPolicy(policy);
        
        assertEquals(policy.getName(), "myName");
        assertEquals(policy.myConfig1, "myVal1");
    }

    @Test
    public void testCanSetName() {
        final ExampleJavaPolicy policy = new ExampleJavaPolicy(MutableMap.of("name", "myName"));
        entity.addPolicy(policy);
        assertEquals(policy.getName(), "myName");
    }
    
    @Test
    public void testCanSetId() {
        final ExampleJavaPolicy policy = new ExampleJavaPolicy(MutableMap.of("id", "myid"));
        entity.addPolicy(policy);
        assertEquals(policy.getId(), "myid");
    }
}

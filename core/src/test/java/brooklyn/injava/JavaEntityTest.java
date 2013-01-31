package brooklyn.injava;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.event.SensorEvent;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.Task;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestApplicationImpl;
import brooklyn.util.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class JavaEntityTest {

    TestApplication app;
    ExampleJavaEntity entity;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = new TestApplicationImpl();
        Entities.startManagement(app);
        app.start(ImmutableList.of(new SimulatedLocation()));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }
    
    @Test
    public void testPolicySubscribesToEvents() {
        final ExampleJavaPolicy policy = new ExampleJavaPolicy();
        entity = new ExampleJavaEntity(MutableMap.of("displayName", "myName", "myConfig1", "myVal1"), app);
        entity.addPolicy(policy);
        Entities.manage(entity);
        
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
        entity = new ExampleJavaEntity(MutableMap.of("displayName", "myName", "myConfig1", "myVal1"), app);
        app.manage(entity);
        
        assertEquals(entity.getDisplayName(), "myName");
        assertEquals(entity.getConfig(ExampleJavaEntity.MY_CONFIG1), "myVal1");
    }

    @Test
    public void testCanSetAttribute() {
        entity = new ExampleJavaEntity(app);
        app.manage(entity);
        
        entity.setAttribute(ExampleJavaEntity.MY_SENSOR1, "myval");
        assertEquals(entity.getAttribute(ExampleJavaEntity.MY_SENSOR1), "myval");
    }
    
    @Test
    public void testCanCallEffector() {
        entity = new ExampleJavaEntity(app);
        app.manage(entity);
        
        entity.effector1("val1");
        assertEquals(entity.effectorInvocations, ImmutableList.of("val1"));
        
        Task<Void> task = entity.invoke(ExampleJavaEntity.EFFECTOR1, ImmutableMap.of("arg0", "val2"));
        task.blockUntilEnded();
        assertEquals(entity.effectorInvocations, ImmutableList.of("val1", "val2"));
    }
}

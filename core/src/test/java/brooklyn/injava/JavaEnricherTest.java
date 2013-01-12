package brooklyn.injava;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.event.SensorEvent;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestApplicationImpl;
import brooklyn.util.MutableMap;

import com.google.common.collect.ImmutableList;

public class JavaEnricherTest {

    TestApplication app;
    ExampleJavaEntity entity;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = new TestApplicationImpl();
        entity = new ExampleJavaEntity(app);
        app.startManagement();
        app.start(ImmutableList.of(new SimulatedLocation()));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }
    
    @Test
    public void testEnricherSubscribesToEvents() {
        final ExampleJavaEnricher enricher = new ExampleJavaEnricher();
        entity.addEnricher(enricher);
        
        entity.setAttribute(ExampleJavaEntity.MY_SENSOR1, "val1");
        
        TestUtils.executeUntilSucceeds(new Runnable() {
            @Override public void run() {
                SensorEvent<String> expected = new BasicSensorEvent<String>(ExampleJavaEntity.MY_SENSOR1, entity, "val1");
                assertEquals(enricher.eventsReceived, ImmutableList.of(expected));
            }
        });
    }
    
    @Test
    public void testCanSetConfig() {
        final ExampleJavaEnricher enricher = new ExampleJavaEnricher(MutableMap.of("displayName", "myName", "myConfig1", "myVal1"));
        entity.addEnricher(enricher);
        
        assertEquals(enricher.getName(), "myName");
        assertEquals(enricher.myConfig1, "myVal1");
    }

    @Test
    public void testCanSetName() {
        final ExampleJavaEnricher enricher = new ExampleJavaEnricher(MutableMap.of("name", "myName"));
        entity.addEnricher(enricher);
        assertEquals(enricher.getName(), "myName");
    }
    
    @Test
    public void testCanSetId() {
        final ExampleJavaEnricher enricher = new ExampleJavaEnricher(MutableMap.of("id", "myid"));
        entity.addEnricher(enricher);
        assertEquals(enricher.getId(), "myid");
    }
}

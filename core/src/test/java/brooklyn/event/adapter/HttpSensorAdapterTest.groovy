package brooklyn.event.adapter;

import static brooklyn.event.adapter.HttpResponseContextTest.*
import static org.codehaus.groovy.runtime.DefaultGroovyMethods.with
import static org.testng.Assert.*

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.test.TestUtils
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

public class HttpSensorAdapterTest {

	final static BasicAttributeSensor SENSOR_STRING = [String.class, "aString", ""];
	final static BasicAttributeSensor SENSOR_LONG = [Long.class, "aLong", ""];
	final static BasicAttributeSensor SENSOR_BOOLEAN = [Boolean.class, "aBool", ""];

    TestApplication app;
	EntityLocal entity;
	HttpSensorAdapter adapter;
	SensorRegistry registry;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = new TestApplication();
        entity = new TestEntity(app);
        registry = new SensorRegistry(entity);
        adapter = registry.register(new HttpSensorAdapter("http://bogus.url.is.definitely.wrong.efaege3"))
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (registry != null) registry.close();
        if (app != null) Entities.destroy(app);
    }

	@Test
	public void testContentEvaluation() {
		assertEquals(adapter.poller.evaluateSensorOnResponse(SENSOR_STRING, { content }, SIMPLE_RESPONSE), "A TEST")
	}

	@Test
	public void testLongConversion() {
		//string in header should be be automatically converted to long
		assertEquals(adapter.poller.evaluateSensorOnResponse(SENSOR_LONG, { headers.bar }, SIMPLE_RESPONSE), 2);
	}

	@Test
	public void testJson() {
		assertEquals(adapter.poller.evaluateSensorOnResponse(SENSOR_LONG, { json.anInt }, JSON_RESPONSE), 10);
		//and conversion
		assertEquals(adapter.poller.evaluateSensorOnResponse(SENSOR_LONG, { json.aStr }, JSON_RESPONSE), 9);
	}

	@Test
	public void testJsonPollAndLongConversion() {
		with(adapter) {
			poll(SENSOR_LONG) { json.bStr }
		}
		adapter.poller.evaluateSensorsOnResponse(JSON_RESPONSE);

		//string in header should be be automatically converted to long
		assertEquals entity.getAttribute(SENSOR_LONG), 8
	}
    
	// Relies on URL above being bogus
    @Test(groups="Integration")
    public void testReportsErrorOnFailedConnection() {
        BasicAttributeSensor<Exception> exceptionSensor = new BasicAttributeSensor(Exception.class, "test.exception", "mydescr");
        try {
            adapter.poll(exceptionSensor, {error});
            registry.activateAdapters();
            
            TestUtils.executeUntilSucceeds {
                Object val = entity.getAttribute(exceptionSensor);
                assertNotNull(val);
                assertTrue(val instanceof Exception, "val="+val);
            }
        } finally {
            registry.close();
        }
    }
}

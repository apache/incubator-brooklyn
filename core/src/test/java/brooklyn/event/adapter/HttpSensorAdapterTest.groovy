package brooklyn.event.adapter;

import static brooklyn.event.adapter.HttpResponseContextTest.*
import static org.codehaus.groovy.runtime.DefaultGroovyMethods.with
import static org.testng.Assert.*

import org.testng.annotations.Test

import brooklyn.entity.basic.EntityLocal
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.test.entity.TestEntity

public class HttpSensorAdapterTest {

	final static BasicAttributeSensor SENSOR_STRING = [String.class, "aString", ""];
	final static BasicAttributeSensor SENSOR_LONG = [Long.class, "aLong", ""];
	final static BasicAttributeSensor SENSOR_BOOLEAN = [Boolean.class, "aBool", ""];

	final EntityLocal entity = new TestEntity();
	final HttpSensorAdapter adapter = [ "BOGUS URL" ]
	final SensorRegistry registry = new SensorRegistry(entity);

	{
		def ad2 = registry.register(adapter)
		assertEquals(ad2, adapter)
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
	
}

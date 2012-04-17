/*
 * TODO license
 */
package brooklyn.event.adapter

import static brooklyn.event.adapter.SshResultContextTest.*
import static org.codehaus.groovy.runtime.DefaultGroovyMethods.with
import static org.testng.Assert.*

import org.testng.annotations.BeforeClass
import org.testng.annotations.Test

import brooklyn.entity.basic.EntityLocal
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.basic.LocalhostMachineProvisioningLocation.LocalhostMachine
import brooklyn.test.entity.TestEntity

public class SshSensorAdapterTest {

	final static BasicAttributeSensor SENSOR_STRING = [ String.class, "name.string", "String" ]
	final static BasicAttributeSensor SENSOR_LONG = [ Long.class, "name.long", "Long" ]
	final static BasicAttributeSensor SENSOR_BOOLEAN = [ Boolean.class, "name.bool", "Boolean" ]

	final EntityLocal entity = new TestEntity();
	final SshSensorAdapter adapter = [ null ]
	final SensorRegistry registry = new SensorRegistry(entity)

    @BeforeClass
    public void registerAdapter() {
		def ad2 = registry.register(adapter)
		assertEquals(ad2, adapter)
	}

	@Test
	public void testContentEvaluation() {
		assertEquals(adapter.poller.evaluateSensorOnResponse(SENSOR_STRING, { stdout }, SIMPLE_RESPONSE), "output")
	}

	/** String should be be automatically converted to long */
	@Test
	public void testLongConversion() {
		assertEquals(adapter.poller.evaluateSensorOnResponse(SENSOR_LONG, { stdout }, NUMERIC_RESPONSE), 31337L);
	}

	/** String should be be automatically converted to long while polling */
	@Test
	public void testPollAndLongConversion() {
		with(adapter) {
			poll(SENSOR_LONG) { stdout }
		}
		adapter.poller.evaluateSensorsOnResponse(NUMERIC_RESPONSE);

		assertEquals entity.getAttribute(SENSOR_LONG), 31337L
	}
	
}

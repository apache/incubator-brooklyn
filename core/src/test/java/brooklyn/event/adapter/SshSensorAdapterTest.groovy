package brooklyn.event.adapter

import static brooklyn.event.adapter.SshResultContextTest.*
import static org.codehaus.groovy.runtime.DefaultGroovyMethods.with
import static org.testng.Assert.*

import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

public class SshSensorAdapterTest {

    final static LocalhostMachineProvisioningLocation location = [ count:1 ]
    final static SshMachineLocation machine = location.obtain()

	final static BasicAttributeSensor SENSOR_STRING = [ String.class, "name.string", "String" ]
	final static BasicAttributeSensor SENSOR_LONG = [ Long.class, "name.long", "Long" ]
	final static BasicAttributeSensor SENSOR_BOOLEAN = [ Boolean.class, "name.bool", "Boolean" ]

    final Application app = new TestApplication();
	final EntityLocal entity = new TestEntity(app);
	final SshSensorAdapter adapter = [ machine ]
	final SensorRegistry registry = new SensorRegistry(entity)

    @BeforeClass(alwaysRun=true)
    public void registerAdapter() {
        Entities.startManagement(app);
		def ad2 = registry.register(adapter)
		assertEquals(ad2, adapter)
	}

    @AfterClass(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app);
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
		with(adapter.command("true")) {
			poll(SENSOR_LONG) { stdout }
		}
		adapter.poller.evaluateSensorsOnResponse(NUMERIC_RESPONSE);

		assertEquals entity.getAttribute(SENSOR_LONG), 31337L
	}
	
}

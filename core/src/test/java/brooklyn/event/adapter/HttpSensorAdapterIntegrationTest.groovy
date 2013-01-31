package brooklyn.event.adapter;

//import static brooklyn.event.adapter.HttpResponseContextTest.JSON_RESPONSE;
//import static brooklyn.event.adapter.HttpResponseContextTest.SIMPLE_RESPONSE;
import static brooklyn.test.TestUtils.*
import static org.testng.Assert.assertEquals

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.EntityLocal
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestApplicationImpl
import brooklyn.test.entity.TestEntityImpl

import com.google.mockwebserver.MockResponse
import com.google.mockwebserver.MockWebServer

@Deprecated // Class under test is deprecated
public class HttpSensorAdapterIntegrationTest {

	final static BasicAttributeSensor<String> SENSOR_STRING = new BasicAttributeSensor<String>(String.class, "aString", "");
	final static BasicAttributeSensor<Integer> SENSOR_INT = new BasicAttributeSensor<Integer>(Integer.class, "aLong", "");
	final static BasicAttributeSensor<Boolean> SENSOR_BOOLEAN = new BasicAttributeSensor<Boolean>(Boolean.class, "aBool", "");

    private MockWebServer server;
    private URL baseUrl;
    
    private Location loc;
    private TestApplication app;
    private EntityLocal entity;
	private HttpSensorAdapter adapter;
	private SensorRegistry registry;

    @BeforeMethod
    public void setUp() throws Exception {
        server = new MockWebServer();
        for (int i = 0; i < 100; i++) {
            server.enqueue(new MockResponse().setResponseCode(200).addHeader("content-type: application/json").setBody("{\"foo\":\"myfoo\"}"));
        }
        server.play();
        baseUrl = server.getUrl("/");

        loc = new LocalhostMachineProvisioningLocation();
        app = new TestApplicationImpl();        
        entity = new TestEntityImpl(app);
        app.start([loc]);
        
        registry = new SensorRegistry(entity);
        adapter = registry.register(new HttpSensorAdapter(baseUrl.toString()));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (server != null) server.shutdown();
        if (app != null) app.stop();
    }
    
	@Test
	public void testPollsAndParsesHttpResponse() throws Exception {
        adapter.with {
            poll(SENSOR_INT, { responseCode });
            poll(SENSOR_STRING, { json.foo });
        }
                
        registry.activateAdapters();
        
        executeUntilSucceeds(timeout:10*1000) {
            assertEquals(entity.getAttribute(SENSOR_INT), 200);
            assertEquals(entity.getAttribute(SENSOR_STRING), "myfoo");
        }
	}
}

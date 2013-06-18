package brooklyn.event.feed.http;

import static brooklyn.test.TestUtils.executeUntilSucceeds;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URI;
import java.util.concurrent.Callable;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.PortRanges;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.HttpService;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;

public class HttpFeedSslIntegrationTest {

    final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString", "");
    final static AttributeSensor<Integer> SENSOR_INT = Sensors.newIntegerSensor("aLong", "");

    private static final long TIMEOUT_MS = 10*1000;
    
    private HttpService httpService;
    private URI baseUrl;
    
    private Location loc;
    private TestApplication app;
    private EntityLocal entity;
    private HttpFeed feed;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        httpService = new HttpService(PortRanges.fromString("9000+"), true);
        baseUrl = new URI(httpService.getUrl());

        loc = new LocalhostMachineProvisioningLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (feed != null) feed.stop();
        if (httpService != null) httpService.shutdown();
        if (app != null) Entities.destroyAll(app);
    }
    
    @Test
    public void testPollsAndParsesHttpGetResponse() throws Exception {
        assertEquals(baseUrl.getScheme(), "https", "baseUrl="+baseUrl);
        
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUri(baseUrl)
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction()))
                .build();
        
        EntityTestUtils.assertAttributeEqualsEventually(entity, SENSOR_INT, 200);
        executeUntilSucceeds(new Callable<Void>() {
            public Void call() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("Hello, World"), "val="+val);
                return null;
            }});
    }
}

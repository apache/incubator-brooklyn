package brooklyn.event.feed.http;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URI;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.PortRanges;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.HttpService;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;

public class HttpFeedIntegrationTest {

    final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString", "");
    final static AttributeSensor<Integer> SENSOR_INT = Sensors.newIntegerSensor("aLong", "");

    private HttpService httpService;

    private Location loc;
    private TestApplication app;
    private EntityLocal entity;
    private HttpFeed feed;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        loc = new LocalhostMachineProvisioningLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (feed != null) feed.stop();
        if (httpService != null) httpService.shutdown();
        if (app != null) Entities.destroyAll(app.getManagementContext());
        LocalManagementContext.terminateAll();
    }

    @Test(groups = {"Integration"})
    public void testPollsAndParsesHttpGetResponseWithSsl() throws Exception {
        httpService = new HttpService(PortRanges.fromString("9000+"), true).start();
        URI baseUrl = new URI(httpService.getUrl());

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
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("Hello, World"), "val="+val);
            }});
    }

    @Test(groups = {"Integration"})
    public void testPollsAndParsesHttpGetResponseWithBasicAuthentication() throws Exception {
        final String username = "brooklyn";
        final String password = "hunter2";
        httpService = new HttpService(PortRanges.fromString("9000+"))
                .basicAuthentication(username, password)
                .start();
        URI baseUrl = new URI(httpService.getUrl());
        assertEquals(baseUrl.getScheme(), "http", "baseUrl="+baseUrl);

        feed = HttpFeed.builder()
                .entity(entity)
                .baseUri(baseUrl)
                .credentials(username, password)
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction()))
                .build();

        EntityTestUtils.assertAttributeEqualsEventually(entity, SENSOR_INT, 200);
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.contains("Hello, World"), "val="+val);
            }});
    }

    @Test(groups = {"Integration"})
    public void testPollWithInvalidCredentialsFails() throws Exception {
        httpService = new HttpService(PortRanges.fromString("9000+"))
                .basicAuthentication("brooklyn", "hunter2")
                .start();

        feed = HttpFeed.builder()
                .entity(entity)
                .baseUri(httpService.getUrl())
                .credentials("brooklyn", "9876543210")
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode())
                        .onFailure(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction())
                        .onException(Functions.constant("Failed!")))
                .build();

        EntityTestUtils.assertAttributeEqualsEventually(entity, SENSOR_INT, 401);
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String val = entity.getAttribute(SENSOR_STRING);
                assertTrue(val != null && val.equals("Failed!"), "val=" + val);
            }
        });
    }
}

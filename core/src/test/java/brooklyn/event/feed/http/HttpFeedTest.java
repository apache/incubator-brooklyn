package brooklyn.event.feed.http;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URL;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
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
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

public class HttpFeedTest {

    private static final Logger log = LoggerFactory.getLogger(HttpFeedTest.class);
    
    final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString", "");
    final static AttributeSensor<Integer> SENSOR_INT = Sensors.newIntegerSensor( "aLong", "");

    private static final long TIMEOUT_MS = 10*1000;
    
    private MockWebServer server;
    private URL baseUrl;
    
    private Location loc;
    private TestApplication app;
    private EntityLocal entity;
    private HttpFeed feed;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        server = new MockWebServer();
        for (int i = 0; i < 100; i++) {
            server.enqueue(new MockResponse().setResponseCode(200).addHeader("content-type: application/json").setBody("{\"foo\":\"myfoo\"}"));
        }
        server.play();
        baseUrl = server.getUrl("/");

        loc = new LocalhostMachineProvisioningLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (feed != null) feed.stop();
        if (server != null) server.shutdown();
        if (app != null) Entities.destroyAll(app.getManagementContext());
        feed = null;
    }
    
    @Test
    public void testPollsAndParsesHttpGetResponse() throws Exception {
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUrl(baseUrl)
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction()))
                .build();
        
        assertSensorEventually(SENSOR_INT, (Integer)200, TIMEOUT_MS);
        assertSensorEventually(SENSOR_STRING, "{\"foo\":\"myfoo\"}", TIMEOUT_MS);
    }
    
    @Test
    public void testPollsAndParsesHttpPostResponse() throws Exception {
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUrl(baseUrl)
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .method("post")
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .method("post")
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction()))
                .build();
        
        assertSensorEventually(SENSOR_INT, (Integer)200, TIMEOUT_MS);
        assertSensorEventually(SENSOR_STRING, "{\"foo\":\"myfoo\"}", TIMEOUT_MS);
    }

    @Test
    public void testUsesFailureHandlerOn4xx() throws Exception {
        server = new MockWebServer();
        for (int i = 0; i < 100; i++) {
            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setBody("Unauthorised"));
        }
        server.play();
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUrl(server.getUrl("/"))
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode())
                        .onFailure(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction())
                        .onFailure(Functions.constant("Failed")))
                .build();

        assertSensorEventually(SENSOR_INT, 401, TIMEOUT_MS);
        assertSensorEventually(SENSOR_STRING, "Failed", TIMEOUT_MS);

        server.shutdown();
    }

    @Test
    public void testUsesExceptionHandlerOn4xxAndNoFailureHandler() throws Exception {
        server = new MockWebServer();
        for (int i = 0; i < 100; i++) {
            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setBody("Unauthorised"));
        }
        server.play();
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUrl(server.getUrl("/"))
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode())
                        .onException(Functions.constant(-1)))
                .build();

        assertSensorEventually(SENSOR_INT, -1, TIMEOUT_MS);

        server.shutdown();
    }

    @Test(groups="Integration")
    // marked integration as it takes a wee while
    public void testSuspendResume() throws Exception {
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUrl(baseUrl)
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction()))
                .build();
        assertSensorEventually(SENSOR_INT, (Integer)200, TIMEOUT_MS);
        feed.suspend();
        final int countWhenSuspended = server.getRequestCount();
        
        Thread.sleep(500);
        if (server.getRequestCount() > countWhenSuspended+1)
            Assert.fail("Request count continued to increment while feed was suspended, from "+countWhenSuspended+" to "+server.getRequestCount());
        
        feed.resume();
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertTrue(server.getRequestCount() > countWhenSuspended + 1,
                        "Request count failed to increment when feed was resumed, from " + countWhenSuspended + ", still at " + server.getRequestCount());
            }
        });
    }

    @Test(groups="Integration")
    // marked integration as it takes a wee while
    public void testStartSuspended() throws Exception {
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUrl(baseUrl)
                .poll(new HttpPollConfig<Integer>(SENSOR_INT)
                        .period(100)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .period(100)
                        .onSuccess(HttpValueFunctions.stringContentsFunction()))
                .suspended()
                .build();
        Asserts.continually(MutableMap.of("timeout", 500),
                Entities.attributeSupplier(entity, SENSOR_INT), Predicates.<Integer>equalTo(null));
        int countWhenSuspended = server.getRequestCount();
        feed.resume();
        Asserts.eventually(Entities.attributeSupplier(entity, SENSOR_INT), Predicates.<Integer>equalTo(200));
        if (server.getRequestCount() <= countWhenSuspended)
            Assert.fail("Request count failed to increment when feed was resumed, from "+countWhenSuspended+", still at "+server.getRequestCount());
        log.info("RUN: "+countWhenSuspended+" - "+server.getRequestCount());
    }


    @Test(groups="Integration")
    // marked as integration so it doesn't fail the plain build in environments
    // with dodgy DNS (ie where "thisdoesnotexistdefinitely" resolves as a host
    // which happily serves you adverts for your ISP, yielding "success" here)
    public void testPollsAndParsesHttpErrorResponseWild() throws Exception {
        feed = HttpFeed.builder()
                .entity(entity)
                .baseUri("http://thisdoesnotexistdefinitely")
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .onSuccess(Functions.constant("success"))
                        .onError(Functions.constant("error")))
                .build();
        
        assertSensorEventually(SENSOR_STRING, "error", TIMEOUT_MS);
    }
    
    @Test
    public void testPollsAndParsesHttpErrorResponseLocal() throws Exception {
        feed = HttpFeed.builder()
                .entity(entity)
                // combo of port 46069 and unknown path will hopefully give an error
                // (without the port, in jenkins it returns some bogus success page)
                .baseUri("http://localhost:46069/path/should/not/exist")
                .poll(new HttpPollConfig<String>(SENSOR_STRING)
                        .onSuccess(Functions.constant("success"))
                        .onError(Functions.constant("error")))
                .build();
        
        assertSensorEventually(SENSOR_STRING, "error", TIMEOUT_MS);
    }
    
    private <T> void assertSensorEventually(final AttributeSensor<T> sensor, final T expectedVal, long timeout) {
        Asserts.succeedsEventually(ImmutableMap.of("timeout", timeout), new Callable<Void>() {
            public Void call() {
                assertEquals(entity.getAttribute(sensor), expectedVal);
                return null;
            }
        });
    }
}

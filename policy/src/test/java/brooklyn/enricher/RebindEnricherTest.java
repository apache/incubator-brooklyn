package brooklyn.enricher;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.RebindTestFixtureWithApp;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.http.BetterMockWebServer;
import brooklyn.util.time.Duration;

import com.google.mockwebserver.MockResponse;

public class RebindEnricherTest extends RebindTestFixtureWithApp {

    public static AttributeSensor<String> METRIC1 = Sensors.newStringSensor("RebindEnricherTest.metric1");
    public static AttributeSensor<String> METRIC2 = Sensors.newStringSensor("RebindEnricherTest.metric2");
    public static AttributeSensor<Integer> INT_METRIC = Sensors.newIntegerSensor("RebindEnricherTest.int_metric");
    public static AttributeSensor<Integer> INT_METRIC2 = Sensors.newIntegerSensor("RebindEnricherTest.int_metric2");
    public static AttributeSensor<Double> DOUBLE_METRIC = Sensors.newDoubleSensor("RebindEnricherTest.double_metric");
    
    private BetterMockWebServer webServer;

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (webServer != null) webServer.shutdown();
    }
    
    @Test(enabled=false)
    public void testDeltaEnricher() throws Exception {
        origApp.addEnricher(new DeltaEnricher<Integer>(origApp, INT_METRIC, INT_METRIC2));
        
        TestApplication newApp = rebind();

        newApp.setAttribute(INT_METRIC, 1);
        newApp.setAttribute(INT_METRIC, 10);
        EntityTestUtils.assertAttributeEqualsEventually(newApp, INT_METRIC2, 9);
    }

    @Test(enabled=false)
    public void testHttpLatencyDetectorEnricher() throws Exception {
        webServer = BetterMockWebServer.newInstanceLocalhost();
        for (int i = 0; i < 1000; i++) {
            webServer.enqueue(new MockResponse().setResponseCode(200).addHeader("content-type: application/json").setBody("{\"foo\":\"myfoo\"}"));
        }
        webServer.play();
        URL baseUrl = webServer.getUrl("/");

        origApp.addEnricher(HttpLatencyDetector.builder()
                .rollup(Duration.of(50, TimeUnit.MILLISECONDS))
                .period(Duration.of(10, TimeUnit.MILLISECONDS))
                .url(baseUrl)
                .build());
        
        
        TestApplication newApp = rebind();

        newApp.setAttribute(HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_MOST_RECENT, null);
        newApp.setAttribute(HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_IN_WINDOW, null);

        EntityTestUtils.assertAttributeEventuallyNonNull(newApp, HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_MOST_RECENT);
        EntityTestUtils.assertAttributeEventuallyNonNull(newApp, HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_IN_WINDOW);
    }

    @Test(enabled=false)
    public void testRollingMeanEnricher() throws Exception {
        origApp.addEnricher(new RollingMeanEnricher<Integer>(origApp, INT_METRIC, DOUBLE_METRIC, 2));
        
        TestApplication newApp = rebind();

        newApp.setAttribute(INT_METRIC, 10);
        EntityTestUtils.assertAttributeEqualsEventually(newApp, DOUBLE_METRIC, 10d);
    }

    @Test(enabled=false)
    public void testRollingTimeWindowMeanEnricher() throws Exception {
        origApp.addEnricher(new RollingTimeWindowMeanEnricher<Integer>(origApp, INT_METRIC, DOUBLE_METRIC, Duration.of(10, TimeUnit.MILLISECONDS)));
        
        TestApplication newApp = rebind();

        newApp.setAttribute(INT_METRIC, 10);
        EntityTestUtils.assertAttributeEqualsEventually(newApp, DOUBLE_METRIC, 10d);
    }
    
    @Test(enabled=false)
    public void testTimeFractionDeltaEnricher() throws Exception {
        origApp.addEnricher(new TimeFractionDeltaEnricher<Integer>(origApp, INT_METRIC, DOUBLE_METRIC, TimeUnit.MICROSECONDS));
        
        TestApplication newApp = rebind();

        newApp.setAttribute(INT_METRIC, 10);
        newApp.setAttribute(INT_METRIC, 20);
        EntityTestUtils.assertAttributeEventuallyNonNull(newApp, DOUBLE_METRIC);
    }
    
    @Test(enabled=false)
    public void testTimeWeightedDeltaEnricher() throws Exception {
        origApp.addEnricher(new TimeWeightedDeltaEnricher<Integer>(origApp, INT_METRIC, DOUBLE_METRIC, 1000));
        
        TestApplication newApp = rebind();

        newApp.setAttribute(INT_METRIC, 10);
        newApp.setAttribute(INT_METRIC, 20);
        EntityTestUtils.assertAttributeEventuallyNonNull(newApp, DOUBLE_METRIC);
    }
}

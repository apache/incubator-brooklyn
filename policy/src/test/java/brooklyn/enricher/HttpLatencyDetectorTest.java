package brooklyn.enricher;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor.StringAttributeSensor;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

public class HttpLatencyDetectorTest {

    private static final Logger log = LoggerFactory.getLogger(HttpLatencyDetectorTest.class);
    public static final AttributeSensor<String> TEST_URL = new StringAttributeSensor( "test.url");
    
    private LocalhostMachineProvisioningLocation loc;
    private TestApplication app;
    private TestEntity entity;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        loc = new LocalhostMachineProvisioningLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }
    
    @Test(groups="Integration")
    public void testWaitsThenPolls() throws Exception {
        entity.addEnricher(HttpLatencyDetector.builder().
                url(TEST_URL).
                noServiceUp().
                rollup(500, TimeUnit.MILLISECONDS).
                period(100, TimeUnit.MILLISECONDS).
                build());
        // nothing until url is set
        TestUtils.assertContinuallyFromJava(MutableMap.of("timeout", 200), 
                Entities.attributeSupplier(entity, HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_MOST_RECENT), 
                Predicates.equalTo(null));
        entity.setAttribute(TEST_URL, "http://www.google.com");
        TestUtils.assertEventually(MutableMap.of("timeout", 10000), 
                Entities.attributeSupplier(entity, HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_MOST_RECENT), 
                Predicates.notNull());
        log.info("Latency to "+entity.getAttribute(TEST_URL)+" is "+entity.getAttribute(HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_MOST_RECENT));
        TestUtils.assertEventually(MutableMap.of("timeout", 10000), 
                Entities.attributeSupplier(entity, HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_IN_WINDOW), 
                Predicates.notNull());
        log.info("Mean latency to "+entity.getAttribute(TEST_URL)+" is "+entity.getAttribute(HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_IN_WINDOW));
    }
    
}

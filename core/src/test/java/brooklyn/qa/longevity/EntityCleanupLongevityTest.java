package brooklyn.qa.longevity;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.AbstractManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.time.Time;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;

public class EntityCleanupLongevityTest {

    private static final Logger LOG = LoggerFactory.getLogger(EntityCleanupLongevityTest.class);

    private ManagementContext managementContext;
    private SimulatedLocation loc;
    private TestApplication app;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        managementContext = new LocalManagementContextForTests();
        
        // do this to ensure GC is initialized
        managementContext.getExecutionManager();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    protected int numIterations() {
        return 100000;
    }

    // Note leaves behind brooklyn.management.usage.ApplicationUsage$ApplicationEvent (one each for started/stopped/destroyed, per app)
    @Test(groups={"Longevity","Acceptance"})
    public void testAppCreatedStartedAndStopped() throws Exception {
        int iterations = numIterations();
        Stopwatch timer = Stopwatch.createStarted();
        long last = timer.elapsed(TimeUnit.MILLISECONDS);
        
        for (int i = 0; i < iterations; i++) {
            if (i % 100 == 0 || i<5) {
                long now = timer.elapsed(TimeUnit.MILLISECONDS);
                String msg = JavaClassNames.niceClassAndMethod()+" iteration " + i + " at " + Time.makeTimeStringRounded(now) + " (delta "+Time.makeTimeStringRounded(now-last)+")";
                LOG.info(msg);
                System.out.println(msg);
                System.gc(); System.gc();
                ((AbstractManagementContext)managementContext).getGarbageCollector().logUsage(JavaClassNames.niceClassAndMethod(), true);
                last = now;
            }
            loc = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
            app = newApp();
            app.start(ImmutableList.of(loc));
            
            app.stop();
            managementContext.getLocationManager().unmanage(loc);
        }
    }

    // Note does not call stop() on the entities
    @Test(groups={"Longevity","Acceptance"})
    public void testAppCreatedStartedAndUnmanaged() throws Exception {
        int iterations = numIterations();
        Stopwatch timer = Stopwatch.createStarted();
        long last = timer.elapsed(TimeUnit.MILLISECONDS);
        
        for (int i = 0; i < iterations; i++) {
            if (i % 100 == 0 || i<5) {
                long now = timer.elapsed(TimeUnit.MILLISECONDS);
                String msg = JavaClassNames.niceClassAndMethod()+" iteration " + i + " at " + Time.makeTimeStringRounded(now) + " (delta "+Time.makeTimeStringRounded(now-last)+")";
                LOG.info(msg);
                System.out.println(msg);
                System.gc(); System.gc();
                ((AbstractManagementContext)managementContext).getGarbageCollector().logUsage(JavaClassNames.niceClassAndMethod(), true);
                last = now;
            }
            loc = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
            app = newApp();
            app.start(ImmutableList.of(loc));
            
            Entities.unmanage(app);
            managementContext.getLocationManager().unmanage(loc);
        }
    }

    @Test(groups={"Longevity","Acceptance"})
    public void testLocationCreatedAndUnmanaged() throws Exception {
        managementContext.getExecutionManager(); // to trigger BrooklynGarbageCollector, and its logging
        int iterations = numIterations();
        Stopwatch timer = Stopwatch.createStarted();
        
        for (int i = 0; i < iterations; i++) {
            if (i % 100 == 0) LOG.info("testLocationCreatedAndUnmanaged iteration {} at {}", i, Time.makeTimeStringRounded(timer));
            if (i % 100 == 0) System.out.println("testLocationCreatedAndUnmanaged iteration " + i + " at " + Time.makeTimeStringRounded(timer));
            loc = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
            managementContext.getLocationManager().unmanage(loc);
        }
    }

    protected TestApplication newApp() {
        final TestApplication result = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        TestEntity entity = result.createAndManageChild(EntitySpec.create(TestEntity.class));
        result.subscribe(entity, TestEntity.NAME, new SensorEventListener<String>() {
            @Override public void onEvent(SensorEvent<String> event) {
                result.setAttribute(TestApplication.MY_ATTRIBUTE, event.getValue());
            }});
        entity.setAttribute(TestEntity.NAME, "myname");
        return result;
    }
}

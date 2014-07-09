package brooklyn.qa.performance;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestFixtureWithApp;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.policy.TestPolicy;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.time.Duration;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Callables;

public class EntityPersistencePerformanceTest extends RebindTestFixtureWithApp {

    // TODO Not measuring performance per cycle; just looking at CPU usage during test
    
    protected int getPersistPeriodMillis() {
        return 1000;
    }

    @Test(groups="Integration")
    public void testManyEntities() throws Exception {
        final int NUM_ENTITIES = 100;
        final Duration TEST_LENGTH = Duration.of(60, TimeUnit.SECONDS);
        final Duration REPEAT_EVERY = Duration.of(500, TimeUnit.MILLISECONDS);
        run(NUM_ENTITIES, TEST_LENGTH, REPEAT_EVERY, "manyEntities");
    }
    
    @Test(groups="Integration")
    public void testRapidChanges() throws Exception {
        final int NUM_ENTITIES = 10;
        final Duration TEST_LENGTH = Duration.of(60, TimeUnit.SECONDS);
        final Duration REPEAT_EVERY = Duration.of(10, TimeUnit.MILLISECONDS);
        run(NUM_ENTITIES, TEST_LENGTH, REPEAT_EVERY, "rapidChanges");
    }
    
    protected void run(int numEntities, Duration testLength, Duration repeatEvery, String loggingContext) throws Exception {
        final List<TestEntity> entities = Lists.newArrayList();
        final List<SimulatedLocation> locs = Lists.newArrayList();
        
        for (int i = 0; i < numEntities; i++) {
            TestEntity entity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
            entity.addPolicy(PolicySpec.create(TestPolicy.class));
            SimulatedLocation loc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
            entities.add(entity);
            locs.add(loc);
        }
        
        Future<?> future = PerformanceTestUtils.sampleProcessCpuTime(Duration.ONE_SECOND, "during "+loggingContext);
        try {
            Repeater.create()
                    .every(repeatEvery)
                    .repeat(new Runnable() {
                            int i = 0;
                            public void run() {
                                for (TestEntity entity : entities) {
                                    entity.setAttribute(TestEntity.SEQUENCE, i++);
                                    Policy policy = Iterables.find(entity.getPolicies(), Predicates.instanceOf(TestPolicy.class));
                                    policy.setConfig(TestPolicy.CONF_NAME, "name-"+i);
                                }
                            }})
                    .limitTimeTo(testLength)
                    .until(Callables.returning(false))
                    .run();
        } finally {
            future.cancel(true);
        }
    }
}

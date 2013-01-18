package brooklyn.policy.autoscaling;

import static brooklyn.policy.autoscaling.AutoScalerPolicyTest.currentSizeAsserter;
import static brooklyn.test.TestUtils.assertSucceedsContinually;
import static brooklyn.test.TestUtils.executeUntilSucceeds;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestCluster;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableMap;

public class AutoScalerPolicyMetricTest {
    
    private static long TIMEOUT_MS = 10000;
    private static long SHORT_WAIT_MS = 250;
    
    private static final BasicAttributeSensor<Integer> MY_ATTRIBUTE = new BasicAttributeSensor<Integer>(Integer.class, "autoscaler.test.intAttrib");
    TestApplication app;
    TestCluster tc;
    
    @BeforeMethod()
    public void before() {
        app = ApplicationBuilder.builder(TestApplication.class).manage();
        tc = app.createAndManageChild(BasicEntitySpec.newInstance(TestCluster.class)
                .configure("initialSize", 1));
    }
    
    @Test
    public void testIncrementsSizeIffUpperBoundExceeded() {
        tc.resize(1);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.addPolicy(policy);

        tc.setAttribute(MY_ATTRIBUTE, 100);
        assertSucceedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), currentSizeAsserter(tc, 1));

        tc.setAttribute(MY_ATTRIBUTE, 101);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 2));
    }
    
    @Test
    public void testDecrementsSizeIffLowerBoundExceeded() {
        tc.resize(2);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.addPolicy(policy);

        tc.setAttribute(MY_ATTRIBUTE, 50);
        assertSucceedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), currentSizeAsserter(tc, 2));

        tc.setAttribute(MY_ATTRIBUTE, 49);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 1));
    }
    
    @Test
    public void testIncrementsSizeInProportionToMetric() {
        tc.resize(5);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.addPolicy(policy);
        
        // workload 200 so requires doubling size to 10 to handle: (200*5)/100 = 10
        tc.setAttribute(MY_ATTRIBUTE, 200);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 10));
        
        // workload 5, requires 1 entity: (10*110)/100 = 11
        tc.setAttribute(MY_ATTRIBUTE, 110);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 11));
    }
    
    @Test
    public void testDecrementsSizeInProportionToMetric() {
        tc.resize(5);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.addPolicy(policy);
        
        // workload can be handled by 4 servers, within its valid range: (49*5)/50 = 4.9
        tc.setAttribute(MY_ATTRIBUTE, 49);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 4));
        
        // workload can be handled by 4 servers, within its valid range: (25*4)/50 = 2
        tc.setAttribute(MY_ATTRIBUTE, 25);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 2));
        
        tc.setAttribute(MY_ATTRIBUTE, 0);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 0));
    }
    
    @Test
    public void obeysMinAndMaxSize() {
        tc.resize(4);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .minPoolSize(2).maxPoolSize(6)
                .build();
        tc.addPolicy(policy);

        // Decreases to min-size only
        tc.setAttribute(MY_ATTRIBUTE, 0);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 2));
        
        // Increases to max-size only
        tc.setAttribute(MY_ATTRIBUTE, 100000);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 6));
    }
    
    @Test
    public void testDestructionState() {
        tc.resize(1);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.addPolicy(policy);

        policy.destroy();
        assertTrue(policy.isDestroyed());
        assertFalse(policy.isRunning());
        
        tc.setAttribute(MY_ATTRIBUTE, 100000);
        assertSucceedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), currentSizeAsserter(tc, 1));
        
        // TODO Could assert all subscriptions have been de-registered as well, 
        // but that requires exposing more things just for testing...
    }
    
    @Test
    public void testSuspendState() {
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.addPolicy(policy);
        
        policy.suspend();
        assertFalse(policy.isRunning());
        assertFalse(policy.isDestroyed());
        
        policy.resume();
        assertTrue(policy.isRunning());
        assertFalse(policy.isDestroyed());
    }

    @Test
    public void testPostSuspendActions() {
        tc.resize(1);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.addPolicy(policy);

        policy.suspend();
        
        tc.setAttribute(MY_ATTRIBUTE, 100000);
        assertSucceedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), currentSizeAsserter(tc, 1));
    }
    
    @Test
    public void testPostResumeActions() {
        tc.resize(1);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.addPolicy(policy);
        
        policy.suspend();
        policy.resume();
        tc.setAttribute(MY_ATTRIBUTE, 101);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 2));
    }
    
    @Test
    public void testSubscribesToMetricOnSpecifiedEntity() {
        TestEntity entityWithMetric = app.createAndManageChild(BasicEntitySpec.newInstance(TestEntity.class));
        
        tc.resize(1);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder()
                .metric(TestEntity.SEQUENCE)
                .entityWithMetric(entityWithMetric)
                .metricLowerBound(50)
                .metricUpperBound(100)
                .build();
        tc.addPolicy(policy);

        // First confirm that tc is not being listened to for this entity
        tc.setAttribute(TestEntity.SEQUENCE, 101);
        assertSucceedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), currentSizeAsserter(tc, 1));

        // Then confirm we listen to the correct "entityWithMetric"
        entityWithMetric.setAttribute(TestEntity.SEQUENCE, 101);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 2));
    }
}

package brooklyn.policy.autoscaling;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.testng.annotations.Test;

import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestFixtureWithApp;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.Iterables;

public class AutoScalerPolicyRebindTest extends RebindTestFixtureWithApp {

    public static BasicNotificationSensor<Map> POOL_HOT_SENSOR = new BasicNotificationSensor<Map>(
            Map.class, "AutoScalerPolicyRebindTest.resizablepool.hot", "Pool is over-utilized; it has insufficient resource for current workload");
    public static BasicNotificationSensor<Map> POOL_COLD_SENSOR = new BasicNotificationSensor<Map>(
            Map.class, "AutoScalerPolicyRebindTest.resizablepool.cold", "Pool is under-utilized; it has too much resource for current workload");
    public static BasicNotificationSensor<Map> POOL_OK_SENSOR = new BasicNotificationSensor<Map>(
            Map.class, "AutoScalerPolicyRebindTest.resizablepool.cold", "Pool utilization is ok; the available resources are fine for the current workload");
    public static BasicNotificationSensor<MaxPoolSizeReachedEvent> MAX_SIZE_REACHED_SENSOR = new BasicNotificationSensor<MaxPoolSizeReachedEvent>(
            MaxPoolSizeReachedEvent.class, "AutoScalerPolicyRebindTest.maxSizeReached");
    public static AttributeSensor<Integer> METRIC_SENSOR = Sensors.newIntegerSensor("AutoScalerPolicyRebindTest.metric");
            
    private DynamicCluster cluster;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        cluster = origApp.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("memberSpec", EntitySpec.create(TestEntity.class)));
    }
    
    @Test(enabled=false)
    public void testRestoresAutoScalerConfig() throws Exception {
        cluster.addPolicy(AutoScalerPolicy.builder()
                .name("myname")
                .metric(METRIC_SENSOR)
                .entityWithMetric(cluster)
                .metricUpperBound(1)
                .metricLowerBound(2)
                .minPoolSize(0)
                .maxPoolSize(3)
                .minPeriodBetweenExecs(4)
                .resizeUpStabilizationDelay(5)
                .resizeDownStabilizationDelay(6)
                .poolHotSensor(POOL_HOT_SENSOR)
                .poolColdSensor(POOL_COLD_SENSOR)
                .poolOkSensor(POOL_OK_SENSOR)
                .maxSizeReachedSensor(MAX_SIZE_REACHED_SENSOR)
                .maxReachedNotificationDelay(7)
                .buildSpec());
        
        TestApplication newApp = rebind();
        AutoScalerPolicy newPolicy = (AutoScalerPolicy) Iterables.getOnlyElement(newApp.getPolicies());

        assertEquals(newPolicy.getName(), "myname");
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.METRIC), METRIC_SENSOR);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.ENTITY_WITH_METRIC), cluster);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.METRIC_UPPER_BOUND), 1);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.METRIC_LOWER_BOUND), 2);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.MIN_POOL_SIZE), (Integer)0);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.MAX_POOL_SIZE), (Integer)3);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.MIN_PERIOD_BETWEEN_EXECS), 4);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.RESIZE_UP_STABILIZATION_DELAY), 5);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.RESIZE_DOWN_STABILIZATION_DELAY), 6);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.POOL_HOT_SENSOR), POOL_HOT_SENSOR);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.POOL_COLD_SENSOR), POOL_COLD_SENSOR);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.POOL_OK_SENSOR), POOL_OK_SENSOR);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.MAX_SIZE_REACHED_SENSOR), MAX_SIZE_REACHED_SENSOR);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.MAX_REACHED_NOTIFICATION_DELAY), 7);
    }
    
    @Test(enabled=false)
    public void testAutoScalerResizesAfterRebind() throws Exception {
        cluster.addPolicy(AutoScalerPolicy.builder()
                .name("myname")
                .metric(METRIC_SENSOR)
                .entityWithMetric(cluster)
                .metricUpperBound(10)
                .metricLowerBound(100)
                .minPoolSize(0)
                .maxPoolSize(3)
                .buildSpec());
        
        TestApplication newApp = rebind();
        DynamicCluster newCluster = (DynamicCluster) Iterables.getOnlyElement(newApp.getChildren());

        assertEquals(newCluster.getCurrentSize(), (Integer)0);
        
        ((EntityInternal)cluster).setAttribute(METRIC_SENSOR, 1000);
        EntityTestUtils.assertGroupSizeEqualsEventually(newCluster, 3);
        
        ((EntityInternal)cluster).setAttribute(METRIC_SENSOR, 0);
        EntityTestUtils.assertGroupSizeEqualsEventually(newCluster, 0);
    }
}

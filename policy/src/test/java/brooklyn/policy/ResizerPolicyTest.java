package brooklyn.policy;

import static brooklyn.test.TestUtils.executeUntilSucceeds;
import static org.testng.AssertJUnit.assertEquals;

import java.util.Collection;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.LocallyManagedEntity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.trait.Resizable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.management.SubscriptionHandle;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestCluster;
import brooklyn.util.MutableMap;
import brooklyn.util.internal.TimeExtras;

public class ResizerPolicyTest {
    
    /**
     * Test class for providing a Resizable LocallyManagedEntity for policy testing
     * It is hooked up to a TestCluster that can be used to make assertions against
     */
    public static class LocallyResizableEntity extends LocallyManagedEntity implements Resizable {
        private static final long serialVersionUID = 3800434173175055012L;
        TestCluster tc;
        public LocallyResizableEntity (TestCluster tc) { this.tc = tc; }
        public Integer resize(Integer newSize) { return (tc.size = newSize); }
        public Integer getCurrentSize() { return tc.size; }
    }
    
    // TODO Write tests differently so that don't need to expose these extra methods...
    public static class ResizerPolicyForTesting<T extends Number> extends ResizerPolicy<T> {
        public ResizerPolicyForTesting(AttributeSensor<T> sensor) {
            super(sensor);
        }
        public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
            return super.subscribe(producer, sensor, listener);
        }
        public Collection<SubscriptionHandle> getAllSubscriptions() {
            return super.getAllSubscriptions();
        }
    }

    static { TimeExtras.init(); }
    
    ResizerPolicyForTesting<Integer> policy;
    TestApplication app;
    TestCluster tc;
    
    @BeforeMethod()
    public void before() {
        app = new TestApplication();
        tc = new TestCluster(app, 1);
        policy = new ResizerPolicyForTesting<Integer>(null);
        policy.setMinSize(0);
    }
    
    @Test
    public void testUpperBounds() {
        tc.size = 1;
        policy.setMetricLowerBound(0);
        policy.setMetricUpperBound(100);
        policy.setEntity(tc);

        assertEquals(1, policy.calculateDesiredSize(99));
        assertEquals(1, policy.calculateDesiredSize(100));
        assertEquals(2, policy.calculateDesiredSize(101));
    }
    
    @Test
    public void testLowerBounds() {
        policy.setMetricLowerBound(100);
        policy.setMetricUpperBound(10000);
        tc.size = 1;
        policy.setEntity(tc);
        
        assertEquals(1, policy.calculateDesiredSize(101));
        assertEquals(1, policy.calculateDesiredSize(100));
        assertEquals(0, policy.calculateDesiredSize(99));
    }
    
    @Test
    public void clustersWithSeveralEntities() {
        policy.setMetricLowerBound(50);
        policy.setMetricUpperBound(100);
        tc.size = 3;
        policy.setEntity(tc);
        
        assertEquals(3, policy.calculateDesiredSize(99));
        assertEquals(3, policy.calculateDesiredSize(100));
        assertEquals(4, policy.calculateDesiredSize(101));
        
        assertEquals(2, policy.calculateDesiredSize(49));
        assertEquals(3, policy.calculateDesiredSize(50));
        assertEquals(3, policy.calculateDesiredSize(51));

    }
    
    @Test
    public void extremeResizes() {
        tc.size = 5;
        policy.setMetricLowerBound(50);
        policy.setMetricUpperBound(100);
        policy.setEntity(tc);
        
        assertEquals(10, policy.calculateDesiredSize(200));
        assertEquals(0, policy.calculateDesiredSize(9));
        // Metric lower bound is 50 shared between 5 entities
        assertEquals(1, policy.calculateDesiredSize(10));
        assertEquals(1, policy.calculateDesiredSize(11));
        assertEquals(2, policy.calculateDesiredSize(20));
    }
    
    @Test
    public void obeysMinAndMaxSize() {
        tc.size = 4;
        policy.setMinSize(2);
        policy.setMaxSize(6);
        policy.setMetricLowerBound(50);
        policy.setMetricUpperBound(100);
        policy.setEntity(tc);
        
        TestCluster tcNoResize = new TestCluster(app, 4);
        ResizerPolicy policyNoResize = new ResizerPolicy(null);
        policyNoResize.setMetricLowerBound(50);
        policyNoResize.setMetricUpperBound(100);
        policyNoResize.setEntity(tcNoResize);
        
        assertEquals(2, policy.calculateDesiredSize(0));
        assertEquals(0, policyNoResize.calculateDesiredSize(0));
        
        assertEquals(6, policy.calculateDesiredSize(175));
        assertEquals(7, policyNoResize.calculateDesiredSize(175));
    }
    
    @Test
    public void testDestructionState() {
        policy.destroy();
        assertEquals(true, policy.isDestroyed());
        assertEquals(false, policy.isRunning());
        assertEquals(0, policy.getAllSubscriptions().size());
    }
    
    @Test
    public void testPostDestructionActions() {
        policy.destroy();
        policy.onEvent(new BasicSensorEvent<Integer>((Sensor)null, (Entity)null, (Integer)null) {
                @Override public Integer getValue() {
                    throw new IllegalStateException("Should not be called when destroyed");
                }
            }
        );
    }
    
    @Test
    public void testSuspendState() {
        policy.suspend();
        assertEquals(false, policy.isDestroyed());
        assertEquals(false, policy.isRunning());
        
        policy.resume();
        assertEquals(false, policy.isDestroyed());
        assertEquals(true, policy.isRunning());
    }

    @Test
    public void testPostSuspendActions() {
        policy.setMetricLowerBound(0);
        policy.setMetricUpperBound(1);
        TestCluster unresizableEntity = new TestCluster(app, 1) {
            @Override public Integer resize(Integer newSize) {
                throw new IllegalStateException("Should not be resizing when suspended");
            }
        };
        policy.setEntity(unresizableEntity);
        
        policy.suspend();
        policy.onEvent(new BasicSensorEvent<Integer>((Sensor)null, unresizableEntity, 2));
    }
    
    @Test
    public void testPostResumeActions() {
        policy.setEntity(new LocallyResizableEntity(tc));
        
        policy.setMetricLowerBound(0);
        policy.setMetricUpperBound(1);
        
        assertEquals(2, policy.calculateDesiredSize(2));

        policy.suspend();
        policy.resume();
        policy.onEvent(new BasicSensorEvent<Integer>((Sensor<Integer>)null, (Entity)null, 2));
        
        executeUntilSucceeds(MutableMap.of("timeout", 3000), new Runnable() {
                @Override public void run() {
                    assertEquals(2, tc.size);
                }});
    }

    @Test
    public void testDestructionUnsubscribes() {
        EntityLocal entity = new LocallyResizableEntity(null);
        policy.setEntity(entity);
        policy.subscribe(entity, (Sensor<?>)null, new SensorEventListener<Object>(){public void onEvent(SensorEvent<Object> e) {}});
        policy.destroy();
        
        executeUntilSucceeds(MutableMap.of("timeout",3000), new Runnable() {
                public void run() {
                    assertEquals(0, policy.getAllSubscriptions().size());
                }});
    }
    
}

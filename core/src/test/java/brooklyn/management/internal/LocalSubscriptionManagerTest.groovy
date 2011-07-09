package brooklyn.management.internal;

import static org.testng.Assert.*

import groovy.transform.InheritConstructors

import java.util.Map
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.testng.annotations.Test

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.event.EventListener
import brooklyn.event.SensorEvent
import brooklyn.event.basic.BasicAttributeSensor

/**
 * testing the {@link SubscriptionManager} and associated classes.
 */
public class LocalSubscriptionManagerTest {
    public class TestApplication extends AbstractApplication {
        public TestApplication(Map properties=[:]) {
            super(properties)
        }
    }
 
    @InheritConstructors
    public class TestEntity extends AbstractEntity {
        int sequenceValue = 0
 
        public static final BasicAttributeSensor<Integer> SEQUENCE = [ Integer, "test.sequence", "Test Sequence" ]
        
        public synchronized int getSequenceValue() {
            sequenceValue
        }
        
        public synchronized void setSequenceValue(int value) {
            sequenceValue = value
            setAttribute(SEQUENCE, value)
        }
    }
    
    CountDownLatch latch

    public class TestListener implements EventListener<Integer> {
        public void onEvent(SensorEvent<Integer> event) {
            latch.countDown()
        }
    }
    
    @Test
    public void testSubscribeToAttributeChange() {
        TestApplication app = new TestApplication()
        TestEntity entity = new TestEntity()
        entity.setApplication(app)
        app.subscribe(entity, TestEntity.SEQUENCE, new TestListener()) 
        latch = new CountDownLatch(1)
        entity.setSequenceValue(1234)
        if (!latch.await(1, TimeUnit.SECONDS)) {
            fail "Timeout waiting for Event on TestEntity listener"
        }
    }
    
    @Test
    public void testSubscribeToChildAttributeChange() {
        TestApplication app = new TestApplication()
        TestEntity parent = new TestEntity()
        parent.setApplication(app)
        app.subscribeToChildren(parent, TestEntity.SEQUENCE, new TestListener()) 
        latch = new CountDownLatch(1)
        // add owned child to parent
        TestEntity one = new TestEntity()
        parent.addOwnedChild(one)
        one.setSequenceValue(1234)
        if (!latch.await(1, TimeUnit.SECONDS)) {
            fail "Timeout waiting for Event on parent TestEntity listener"
        }
    }
}

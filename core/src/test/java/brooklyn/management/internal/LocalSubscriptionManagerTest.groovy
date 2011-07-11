package brooklyn.management.internal;

import static org.testng.Assert.*
import groovy.transform.InheritConstructors

import java.util.Map
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.testng.Assert
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.event.EventListener
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.management.SubscriptionHandle

/**
 * testing the {@link SubscriptionManager} and associated classes.
 */
public class LocalSubscriptionManagerTest {
    public class TestApplication extends AbstractApplication {
        public TestApplication(Map properties=[:]) {
            super(properties)
        }
        public <T> SubscriptionHandle subscribeToMembers(Entity parent, Sensor<T> sensor, EventListener<T> listener) {
            subscriptionContext.subscribeToMembers(parent, sensor, listener)
        }
    }
 
    @InheritConstructors
    public class TestEntity extends AbstractEntity {
        int sequenceValue = 0
 
        public static final BasicAttributeSensor<Integer> SEQUENCE = [ Integer, "test.sequence", "Test Sequence" ]
        
        public TestEntity(Map properties=[:]) {
            super(properties)
        }
        public synchronized int getSequenceValue() {
            sequenceValue
        }
        public synchronized void setSequenceValue(int value) {
            sequenceValue = value
            setAttribute(SEQUENCE, value)
        }
    }
    
    @Test
    public void testSubscribeToAttributeChange() {
        TestApplication app = new TestApplication()
        TestEntity entity = new TestEntity([owner:app])
        CountDownLatch latch = new CountDownLatch(1)
        app.subscribe(entity, TestEntity.SEQUENCE, { latch.countDown() } as EventListener) 
        entity.setSequenceValue(1234)
        if (!latch.await(1, TimeUnit.SECONDS)) {
            fail "Timeout waiting for Event on TestEntity listener"
        }
    }
    
    @Test
    public void testSubscribeToChildAttributeChange() {
        TestApplication app = new TestApplication()
        TestEntity child = new TestEntity([owner:app])
        CountDownLatch latch = new CountDownLatch(1)
        app.subscribeToChildren(app, TestEntity.SEQUENCE, { latch.countDown() } as EventListener) 
        child.setSequenceValue(1234)
        if (!latch.await(1, TimeUnit.SECONDS)) {
            fail "Timeout waiting for Event on child TestEntity listener"
        }
    }
    
    @Test
    public void testSubscribeToMemberAttributeChange() {
        TestApplication app = new TestApplication()
        AbstractGroup group = new AbstractGroup([owner:app]) {}
        TestEntity member = new TestEntity([owner:app])
        group.addMember(member);

        List<SensorEvent<Integer>> events = []
        CountDownLatch latch = new CountDownLatch(1)
        app.subscribeToMembers(group, TestEntity.SEQUENCE, { events.add(it); latch.countDown() } as EventListener)
        member.emit(TestEntity.SEQUENCE, 123)

        if (!latch.await(1, TimeUnit.SECONDS)) {
            fail "Timeout waiting for Event on parent TestEntity listener"
        }
        Assert.assertEquals(events.size(), 1)
        Assert.assertEquals(events.getAt(0).value, 123)
        Assert.assertEquals(events.getAt(0).sensor, TestEntity.SEQUENCE)
        Assert.assertEquals(events.getAt(0).source.id, member.id)
    }
}

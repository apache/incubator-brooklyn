package brooklyn.management.internal;

import static org.testng.Assert.*

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.testng.annotations.Test

import brooklyn.entity.basic.AbstractGroup
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

/**
 * testing the {@link SubscriptionManager} and associated classes.
 */
public class LocalSubscriptionManagerTest {
    @Test
    public void testSubscribeToAttributeChange() {
        TestApplication app = new TestApplication()
        TestEntity entity = new TestEntity([owner:app])
        CountDownLatch latch = new CountDownLatch(1)
        app.subscribe(entity, TestEntity.SEQUENCE, { latch.countDown() } as SensorEventListener) 
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
        app.subscribeToChildren(app, TestEntity.SEQUENCE, { latch.countDown() } as SensorEventListener) 
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
        app.subscribeToMembers(group, TestEntity.SEQUENCE, { events.add(it); latch.countDown() } as SensorEventListener)
        member.emit(TestEntity.SEQUENCE, 123)

        if (!latch.await(1, TimeUnit.SECONDS)) {
            fail "Timeout waiting for Event on parent TestEntity listener"
        }
        assertEquals(events.size(), 1)
        assertEquals(events.getAt(0).value, 123)
        assertEquals(events.getAt(0).sensor, TestEntity.SEQUENCE)
        assertEquals(events.getAt(0).source.id, member.id)
    }
}

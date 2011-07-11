package brooklyn.policy.wip

import java.util.Collections.UnmodifiableList

import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.EventListener
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.basic.BasicSensor
import brooklyn.management.SubscriptionHandle
import brooklyn.policy.basic.AbstractPolicy

class BufferingEnricher<T> extends AbstractPolicy implements EventListener<T> {
    public static interface BufferEvent {}
    public static final class BufferChangedEvent implements BufferEvent {}
    public static final class BufferFlushedEvent implements BufferEvent {}
    private Sensor<BufferChangedEvent> result
    private boolean alwaysRetainBuffer

    protected LinkedList<T> buffer = new LinkedList<T>()

    public BufferingEnricher(Entity owner, Entity producer, Sensor<T> source) {
        this(owner, producer, source, true)
    }

    private BufferingEnricher(Entity producer, Sensor<T> source, boolean alwaysRetainBuffer) {
        this.alwaysRetainBuffer = alwaysRetainBuffer
        this.result = new BasicSensor(BufferChangedEvent.class, "Buffer", "Buffer for ${source.getDescription()}")
    }
    
    @Override
     public void setEntity(EntityLocal entity) {
       super.setEntity(entity)
       super.subscribe(producer, source, this)
   }

    public void onEvent(SensorEvent<T> e) {
        if (!discardBuffer()) {
            buffer.addFirst(e.getValue())
            manageBuffer()
            subscription.publish(result.newEvent(entity, new BufferChangedEvent()))
        }
    }

    public <T> void unsubscribe(SubscriptionHandle subscriptionId) {
        subscription.unsubscribe subscriptionId
        if (discardBuffer()) {buffer = new LinkedList<T>()}
    }

    protected void manageBuffer() {} //for overriding by subclasses

    public List<T> getBuffer() {
        if (discardBuffer()) {
            throw new NullPointerException("No buffer available as there are no subscribers and retention policy is set to NEVER")
        }
        new UnmodifiableList<T>(buffer)
    }

    public void flush() {
        buffer = new LinkedList<T>()
        subscription.publish(result.newEvent(entity, new BufferFlushedEvent()))
    }

    private boolean discardBuffer() {!alwaysRetainBuffer && subscription.getSubscriptions().size() == 0}

    public Sensor<BufferChangedEvent> getSensor() {
        return result;
    }

}

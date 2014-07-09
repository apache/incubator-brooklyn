package brooklyn.entity.messaging.qpid;

import brooklyn.entity.messaging.Queue;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(QpidQueueImpl.class)
public interface QpidQueue extends QpidDestination, Queue {
    @Override
    public String getExchangeName();
}

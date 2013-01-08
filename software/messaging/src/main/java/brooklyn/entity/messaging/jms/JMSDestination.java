package brooklyn.entity.messaging.jms;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.util.MutableMap;

import com.google.common.base.Preconditions;

public abstract class JMSDestination extends AbstractEntity {
    public JMSDestination() {
        this(MutableMap.of(), null);
    }
    public JMSDestination(Map properties) {
        this(properties, null);
    }
    public JMSDestination(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public JMSDestination(Map properties, Entity parent) {
        super(properties, parent);

        Preconditions.checkNotNull(getName(), "Name must be specified");
    }

    public String getName() {
        return getDisplayName();
    }
    
    public abstract void init();

    protected abstract void connectSensors();

    protected abstract void disconnectSensors();

    public abstract void delete();

    public void destroy() {
        disconnectSensors();
        delete();
        super.destroy();
    }
}

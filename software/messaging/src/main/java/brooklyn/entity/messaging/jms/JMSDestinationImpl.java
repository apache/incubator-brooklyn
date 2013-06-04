package brooklyn.entity.messaging.jms;

import brooklyn.entity.basic.AbstractEntity;

import com.google.common.base.Preconditions;

public abstract class JMSDestinationImpl extends AbstractEntity implements JMSDestination {
    public JMSDestinationImpl() {
    }

    @Override
    public void onManagementStarting() {
        super.onManagementStarting();
        Preconditions.checkNotNull(getName(), "Name must be specified");
    }

    @Override
    public String getName() {
        return getDisplayName();
    }
    
    protected abstract void connectSensors();

    protected abstract void disconnectSensors();

    public abstract void delete();

    public void destroy() {
        disconnectSensors();
        delete();
        super.destroy();
    }
}

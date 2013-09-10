package brooklyn.test.entity;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEventListener;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.SubscriptionHandle;
import brooklyn.util.logging.LoggingSetup;

/**
 * Mock application for testing.
 */
public class TestApplicationImpl extends AbstractApplication implements TestApplication {
    private static final Logger LOG = LoggerFactory.getLogger(TestApplication.class);

    static {
        // our tests should redirect the j.u.l logging messages to logback 
        LoggingSetup.installJavaUtilLoggingBridge();
    }

    public TestApplicationImpl() {
        super();
    }

    public TestApplicationImpl(Map<?,?> flags) {
        super(flags);
    }

    @Override
    public void init() {
        // no-op
    }
    
    /**
     * @deprecated since 0.6.0 Use {@link #addChild(EntitySpec)}
     */
    @Override
    public <T extends Entity> T createChild(EntitySpec<T> spec) {
        return addChild(spec);
    }

    @Override
    public <T extends Entity> T createAndManageChild(EntitySpec<T> spec) {
        if (!getManagementSupport().isDeployed()) throw new IllegalStateException("Entity "+this+" not managed");
        T child = createChild(spec);
        getEntityManager().manage(child);
        return child;
    }
    
    @Override
    public <T> SubscriptionHandle subscribeToMembers(Group parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return getSubscriptionContext().subscribeToMembers(parent, sensor, listener);
    }

    @Override
    public String toString() {
        String id = getId();
        return "Application["+id.substring(Math.max(0, id.length()-8))+"]";
    }

    @Override
    public void startManagement() {
        Entities.startManagement(this);
    }
    
    @Override
    public <T extends Entity> T manage(T entity) {
        if (!Entities.manage(entity)) {
            Assert.assertEquals(entity.getApplication(), this);
        }
        return entity;
    }

    public LocalhostMachineProvisioningLocation newLocalhostProvisioningLocation() {
        return (LocalhostMachineProvisioningLocation) getManagementContext().getLocationRegistry().resolve("localhost");
    }

}

package brooklyn.test.entity;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;

/**
 * Mock application for testing.
 */
//TODO Don't want to extend EntityLocal/EntityInternal, but tests want to call things like app.setAttribute
@ImplementedBy(TestApplicationImpl.class)
public interface TestApplication extends StartableApplication, EntityInternal {

    public static final AttributeSensor<String> MY_ATTRIBUTE = Sensors.newStringSensor("test.myattribute", "Test attribute sensor");

    public <T extends Entity> T createAndManageChild(EntitySpec<T> spec);

    public LocalhostMachineProvisioningLocation newLocalhostProvisioningLocation();
    
}

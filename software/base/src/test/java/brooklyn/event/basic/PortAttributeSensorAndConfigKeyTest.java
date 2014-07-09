package brooklyn.event.basic;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.EmptySoftwareProcess;
import brooklyn.entity.basic.EmptySoftwareProcessImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;

import com.google.common.collect.ImmutableList;

public class PortAttributeSensorAndConfigKeyTest extends BrooklynAppUnitTestSupport {

    /*
     * FIXME Fails because port is never released. Nothing calls PortSupplier.releasePort(int).
     * The stacktrace below shows where it is obtained:
     * 
        Daemon Thread [brooklyn-execmanager-XwLLLdS4-5] (Suspended (breakpoint at line 244 in LocalhostMachineProvisioningLocation$LocalhostMachine))   
            LocalhostMachineProvisioningLocation$LocalhostMachine.obtainPort(PortRange) line: 244   
            PortAttributeSensorAndConfigKey.convertConfigToSensor(PortRange, Entity) line: 78   
            PortAttributeSensorAndConfigKey.convertConfigToSensor(Object, Entity) line: 1   
            PortAttributeSensorAndConfigKey(AttributeSensorAndConfigKey<ConfigType,SensorType>).getAsSensorValue(Entity) line: 93   
            ConfigToAttributes.apply(EntityLocal, AttributeSensorAndConfigKey<?,T>) line: 28    
            ConfigToAttributes.apply(EntityLocal) line: 17  
            SoftwareProcessDriverLifecycleEffectorTasks(MachineLifecycleEffectorTasks).preStartCustom(MachineLocation) line: 343    
            SoftwareProcessDriverLifecycleEffectorTasks.preStartCustom(MachineLocation) line: 69    
            MachineLifecycleEffectorTasks$6.run() line: 283 
     */
    @Test(enabled=false, groups="Integration") // test is slow (for some reason - why?)
    public void testStoppingEntityReleasesPortFromMachineForReuse() throws Exception {
        LocalhostMachineProvisioningLocation loc = (LocalhostMachineProvisioningLocation) mgmt.getLocationRegistry().resolve("localhost");
        SshMachineLocation machine = loc.obtain();
        runStoppingEntityReleasesPortFromLocalhostForReuse(machine);
    }

    @Test(groups="Integration") // test is slow (for some reason - why?)
    public void testStoppingEntityReleasesPortFromLocalhostProvisioningLocationForReuse() throws Exception {
        LocalhostMachineProvisioningLocation loc = (LocalhostMachineProvisioningLocation) mgmt.getLocationRegistry().resolve("localhost");
        runStoppingEntityReleasesPortFromLocalhostForReuse(loc);
    }
    
    protected void runStoppingEntityReleasesPortFromLocalhostForReuse(Location loc) throws Exception {
        MyEntity e1 = app.createAndManageChild(EntitySpec.create(MyEntity.class));
        e1.start(ImmutableList.of(loc));
        assertEquals(e1.getAttribute(MyEntity.MY_PORT), (Integer)47653);
        
        e1.stop();
        Entities.unmanage(e1);
        MyEntity e2 = app.createAndManageChild(EntitySpec.create(MyEntity.class));
        e2.start(ImmutableList.of(loc));
        assertEquals(e2.getAttribute(MyEntity.MY_PORT), (Integer)47653);
    }

    @ImplementedBy(MyEntityImpl.class)
    public interface MyEntity extends EmptySoftwareProcess {
        PortAttributeSensorAndConfigKey MY_PORT = new PortAttributeSensorAndConfigKey("myport", "", "47653");
    }
    
    public static class MyEntityImpl extends EmptySoftwareProcessImpl implements MyEntity {
    }        
}

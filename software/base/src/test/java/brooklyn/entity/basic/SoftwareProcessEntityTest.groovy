package brooklyn.entity.basic;

import groovy.transform.InheritConstructors;

import org.testng.Assert
import org.testng.annotations.Test

import brooklyn.config.ConfigKey
import brooklyn.entity.Application
import brooklyn.location.MachineLocation
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.test.entity.TestApplication


public class SoftwareProcessEntityTest {

//  NB: These tests don't actually require ssh to localhost -- only that 'localhost' resolves.
    
    @Test
    public void testBasicSoftwareProcessEntityLifecycle() {
        SshMachineLocation machine = new SshMachineLocation(address:"localhost");
        def loc = new FixedListMachineProvisioningLocation<MachineLocation>(machines:[machine]);
        TestApplication app = new TestApplication();
        MyService entity = new MyService(app)
        app.startManagement();
        entity.start([loc]);
        SimulatedDriver d = entity.getDriver();
        Assert.assertTrue(d.isRunning());
        entity.stop();
        Assert.assertEquals(d.events, ["install", "customize", "launch", "stop"]);
        Assert.assertFalse(d.isRunning());
    }
    
    @Test
    public void testShutdownIsIdempotent() {
        SshMachineLocation machine = new SshMachineLocation(address:"localhost");
        def loc = new FixedListMachineProvisioningLocation<MachineLocation>(machines:[machine]);
        TestApplication app = new TestApplication();
        MyService entity = new MyService(app)
        app.startManagement();
        entity.start([loc]);
        entity.stop();
        
        entity.stop();
    }
    
    @InheritConstructors
    private static class MyService extends SoftwareProcessEntity {
        @Override
        public <T> T getConfig(ConfigKey<T> key, T defaultValue=null) {
            return super.getConfig(key, defaultValue)
        }

        Class getDriverInterface() {
            return SimulatedDriver.class;
        }
    }
}

public class SimulatedDriver extends AbstractSoftwareProcessDriver {
    private volatile boolean launched = false;
    
    SimulatedDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    public List<String> events = new ArrayList<String>();
    
    @Override
    public boolean isRunning() {
        return launched;
    }

    @Override
    public void stop() {
        events.add("stop");
        launched = false;
    }

    @Override
    public void kill() {
        events.add("kill");
        launched = false;
    }

    @Override
    public void install() {
        events.add("install");
    }

    @Override
    public void customize() {
        events.add("customize");
    }

    @Override
    public void launch() {
        events.add("launch");
        launched = true;
    }
}

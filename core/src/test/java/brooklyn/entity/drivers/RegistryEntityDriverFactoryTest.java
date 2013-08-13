package brooklyn.entity.drivers;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import brooklyn.management.internal.LocalManagementContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.drivers.ReflectiveEntityDriverFactoryTest.MyDriver;
import brooklyn.entity.drivers.ReflectiveEntityDriverFactoryTest.MyDriverDependentEntity;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;

public class RegistryEntityDriverFactoryTest {

    private RegistryEntityDriverFactory factory;
    private SshMachineLocation sshLocation;
    private SimulatedLocation simulatedLocation;

    @BeforeMethod
    public void setUp() throws Exception {
        factory = new RegistryEntityDriverFactory();
        sshLocation = new SshMachineLocation(MutableMap.of("address", "localhost"));
        simulatedLocation = new SimulatedLocation();
    }

    @AfterMethod
    public void tearDown(){
        LocalManagementContext.terminateAll();
    }

    @Test
    public void testHasDriver() throws Exception {
        DriverDependentEntity<MyDriver> entity = new MyDriverDependentEntity<MyDriver>(MyDriver.class);
        factory.registerDriver(MyDriver.class, SshMachineLocation.class, MyOtherSshDriver.class);
        assertTrue(factory.hasDriver(entity, sshLocation));
        assertFalse(factory.hasDriver(entity, simulatedLocation));
    }

    @Test
    public void testInstantiatesRegisteredDriver() throws Exception {
        DriverDependentEntity<MyDriver> entity = new MyDriverDependentEntity<MyDriver>(MyDriver.class);
        factory.registerDriver(MyDriver.class, SshMachineLocation.class, MyOtherSshDriver.class);
        MyDriver driver = factory.build(entity, sshLocation);
        assertTrue(driver instanceof MyOtherSshDriver);
    }

    public static class MyOtherSshDriver implements MyDriver {
        public MyOtherSshDriver(Entity entity, Location machine) {
        }

        @Override
        public EntityLocal getEntity() {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public Location getLocation() {
            throw new UnsupportedOperationException();
        }
    }
}

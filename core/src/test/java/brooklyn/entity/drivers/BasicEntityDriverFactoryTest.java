package brooklyn.entity.drivers;

import static org.testng.Assert.assertTrue;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.drivers.ReflectiveEntityDriverFactoryTest.MyDriver;
import brooklyn.entity.drivers.ReflectiveEntityDriverFactoryTest.MyDriverDependentEntity;
import brooklyn.entity.drivers.ReflectiveEntityDriverFactoryTest.MySshDriver;
import brooklyn.entity.drivers.RegistryEntityDriverFactoryTest.MyOtherSshDriver;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;

public class BasicEntityDriverFactoryTest {

    private BasicEntityDriverFactory factory;
    private SshMachineLocation sshLocation;
    private SimulatedLocation simulatedLocation;

    @BeforeMethod
    public void setUp() throws Exception {
        factory = new BasicEntityDriverFactory();
        sshLocation = new SshMachineLocation(MutableMap.of("address", "localhost"));
        simulatedLocation = new SimulatedLocation();
    }
    
    @Test
    public void testPrefersRegisteredDriver() throws Exception {
        DriverDependentEntity<MyDriver> entity = new MyDriverDependentEntity<MyDriver>(MyDriver.class);
        factory.registerDriver(MyDriver.class, SshMachineLocation.class, MyOtherSshDriver.class);
        assertTrue(factory.build(entity, sshLocation) instanceof MyOtherSshDriver);
    }
    
    @Test
    public void testFallsBackToReflectiveDriver() throws Exception {
        DriverDependentEntity<MyDriver> entity = new MyDriverDependentEntity<MyDriver>(MyDriver.class);
        assertTrue(factory.build(entity, sshLocation) instanceof MySshDriver);
    }
    
    @Test
    public void testRespectsLocationWhenDecidingOnDriver() throws Exception {
        DriverDependentEntity<MyDriver> entity = new MyDriverDependentEntity<MyDriver>(MyDriver.class);
        factory.registerDriver(MyDriver.class, SimulatedLocation.class, MyOtherSshDriver.class);
        assertTrue(factory.build(entity, simulatedLocation) instanceof MyOtherSshDriver);
        assertTrue(factory.build(entity, sshLocation) instanceof MySshDriver);
    }
}

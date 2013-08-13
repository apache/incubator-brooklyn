package brooklyn.entity.drivers;

import static org.testng.Assert.assertTrue;

import brooklyn.management.internal.LocalManagementContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.drivers.ReflectiveEntityDriverFactoryTest.MyDriver;
import brooklyn.entity.drivers.ReflectiveEntityDriverFactoryTest.MyDriverDependentEntity;
import brooklyn.entity.drivers.ReflectiveEntityDriverFactoryTest.MySshDriver;
import brooklyn.entity.drivers.RegistryEntityDriverFactoryTest.MyOtherSshDriver;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;

public class BasicEntityDriverManagerTest {

    private BasicEntityDriverManager manager;
    private SshMachineLocation sshLocation;
    private SimulatedLocation simulatedLocation;

    @BeforeMethod
    public void setUp() throws Exception {
        manager = new BasicEntityDriverManager();
        sshLocation = new SshMachineLocation(MutableMap.of("address", "localhost"));
        simulatedLocation = new SimulatedLocation();
    }

    @AfterMethod
    public void tearDown(){
        LocalManagementContext.terminateAll();
    }
    
    @Test
    public void testPrefersRegisteredDriver() throws Exception {
        DriverDependentEntity<MyDriver> entity = new MyDriverDependentEntity<MyDriver>(MyDriver.class);
        manager.registerDriver(MyDriver.class, SshMachineLocation.class, MyOtherSshDriver.class);
        assertTrue(manager.build(entity, sshLocation) instanceof MyOtherSshDriver);
    }
    
    @Test
    public void testFallsBackToReflectiveDriver() throws Exception {
        DriverDependentEntity<MyDriver> entity = new MyDriverDependentEntity<MyDriver>(MyDriver.class);
        assertTrue(manager.build(entity, sshLocation) instanceof MySshDriver);
    }
    
    @Test
    public void testRespectsLocationWhenDecidingOnDriver() throws Exception {
        DriverDependentEntity<MyDriver> entity = new MyDriverDependentEntity<MyDriver>(MyDriver.class);
        manager.registerDriver(MyDriver.class, SimulatedLocation.class, MyOtherSshDriver.class);
        assertTrue(manager.build(entity, simulatedLocation) instanceof MyOtherSshDriver);
        assertTrue(manager.build(entity, sshLocation) instanceof MySshDriver);
    }
}

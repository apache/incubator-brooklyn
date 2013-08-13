package brooklyn.entity.drivers;

import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;

public class ReflectiveEntityDriverFactoryTest {

    private ReflectiveEntityDriverFactory factory;
    private SshMachineLocation sshLocation;

    @BeforeMethod
    public void setUp() throws Exception {
        factory = new ReflectiveEntityDriverFactory();
        sshLocation = new SshMachineLocation(MutableMap.of("address", "localhost"));
    }

    @AfterMethod
    public void tearDown() {
        // nothing to tear down; no management context created
    }

    @Test
    public void testInstantiatesSshDriver() throws Exception {
        DriverDependentEntity<MyDriver> entity = new MyDriverDependentEntity<MyDriver>(MyDriver.class);
        MyDriver driver = factory.build(entity, sshLocation);
        assertTrue(driver instanceof MySshDriver, "driver="+driver);
    }

    public static class MyDriverDependentEntity<D extends EntityDriver> extends AbstractEntity implements DriverDependentEntity<D> {
        private final Class<D> clazz;

        public MyDriverDependentEntity(Class<D> clazz) {
            this.clazz = clazz;
        }
        
        @Override
        public Class<D> getDriverInterface() {
            return clazz;
        }
    }
    
    public static interface MyDriver extends EntityDriver {
    }
    
    public static class MySshDriver implements MyDriver {
        public MySshDriver(Entity entity, SshMachineLocation machine) {
        }

        @Override
        public Location getLocation() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EntityLocal getEntity() {
            throw new UnsupportedOperationException();
        }
    }
}

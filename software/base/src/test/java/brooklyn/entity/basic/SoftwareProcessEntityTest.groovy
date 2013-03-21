package brooklyn.entity.basic;

import org.jclouds.util.Throwables2
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.config.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.trait.Startable
import brooklyn.location.MachineLocation
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.test.entity.TestApplication

import com.google.common.collect.ImmutableSet


public class SoftwareProcessEntityTest {

//  NB: These tests don't actually require ssh to localhost -- only that 'localhost' resolves.

    private static final Logger LOG = LoggerFactory.getLogger(SoftwareProcessEntityTest.class);
    
    private SshMachineLocation machine;
    private FixedListMachineProvisioningLocation loc;
    private TestApplication app;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        machine = new SshMachineLocation(address:"localhost");
        loc = new FixedListMachineProvisioningLocation<MachineLocation>(machines:[machine]);
        app = ApplicationBuilder.builder(TestApplication.class).manage();
        //entity = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        MyService entity = new MyService(app)
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }
    
    @Test
    public void testBasicSoftwareProcessEntityLifecycle() {
        MyService entity = new MyService(app)
        Entities.manage(entity);
        entity.start([loc]);
        SimulatedDriver d = entity.getDriver();
        Assert.assertTrue(d.isRunning());
        entity.stop();
        Assert.assertEquals(d.events, ["install", "customize", "launch", "stop"]);
        Assert.assertFalse(d.isRunning());
    }
    
    @Test
    public void testShutdownIsIdempotent() {
        MyService entity = new MyService(app)
        Entities.manage(entity);
        entity.start([loc]);
        entity.stop();
        
        entity.stop();
    }
    
    @Test
    public void testReleaseEvenIfErrorDuringStart() {
        MyService entity = new MyService(app) {
            @Override public Class getDriverInterface() {
                return SimulatedFailOnStartDriver.class;
            }
        };
        Entities.manage(entity);
        
        try {
            entity.start([loc]);
            Assert.fail();
        } catch (Exception e) {
            IllegalStateException cause = Throwables2.getFirstThrowableOfType(e, IllegalStateException.class);
            if (cause == null || !cause.toString().contains("Simulating start error")) throw e; 
        }
        
        try {
            entity.stop();
        } catch (Exception e) {
            // Keep going
            LOG.info("Error during stop, after simulating error during start", e);
        }
        Assert.assertEquals(loc.getAvailable(), ImmutableSet.of(machine));
    }

    @Test
    public void testReleaseEvenIfErrorDuringStop() {
        MyService entity = new MyService(app) {
            @Override public Class getDriverInterface() {
                return SimulatedFailOnStopDriver.class;
            }
        };
        Entities.manage(entity);
        
        entity.start([loc]);
        try {
            entity.stop();
            Assert.fail();
        } catch (Exception e) {
            Assert.assertEquals(loc.getAvailable(), ImmutableSet.of(machine));
            IllegalStateException cause = Throwables2.getFirstThrowableOfType(e, IllegalStateException.class);
            if (cause == null || !cause.toString().contains("Simulating stop error")) throw e;
        }
    }

    public static class MyService extends SoftwareProcessImpl {
        public MyService() {
            super();
        }
        public MyService(Entity parent) {
            super(parent);
        }
        public MyService(Map flags, Entity parent) {
            super(flags, parent);
        }
        @Override
        public <T> T getConfig(ConfigKey<T> key, T defaultValue=null) {
            return super.getConfig(key, defaultValue)
        }

        Class getDriverInterface() {
            return SimulatedDriver.class;
        }
    }
}

public class SimulatedFailOnStartDriver extends SimulatedDriver {
    public SimulatedFailOnStartDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine)
    }
    
    @Override
    public void install() {
        throw new IllegalStateException("Simulating start error");
    }
}

public class SimulatedFailOnStopDriver extends SimulatedDriver {
    public SimulatedFailOnStopDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine)
    }
    
    @Override
    public void stop() {
        throw new IllegalStateException("Simulating stop error");
    }
}

public class SimulatedDriver extends AbstractSoftwareProcessDriver {
    public List<String> events = new ArrayList<String>();
    private volatile boolean launched = false;
    
    public SimulatedDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine)
    }
    
    @Override
    public boolean isRunning() {
        return launched;
    }

    @Override
    public void stop() {
        events.add("stop");
        launched = false;
        entity.setAttribute(Startable.SERVICE_UP, false);
    }

    @Override
    public void kill() {
        events.add("kill");
        launched = false;
        entity.setAttribute(Startable.SERVICE_UP, false);
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
        entity.setAttribute(Startable.SERVICE_UP, true);
    }
}

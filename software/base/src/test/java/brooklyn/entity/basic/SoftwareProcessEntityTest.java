package brooklyn.entity.basic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jclouds.util.Throwables2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;


public class SoftwareProcessEntityTest {

//  NB: These tests don't actually require ssh to localhost -- only that 'localhost' resolves.

    private static final Logger LOG = LoggerFactory.getLogger(SoftwareProcessEntityTest.class);
    
    private SshMachineLocation machine;
    private FixedListMachineProvisioningLocation<SshMachineLocation> loc;
    private TestApplication app;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        machine = new SshMachineLocation(MutableMap.of("address", "localhost"));
        loc = new FixedListMachineProvisioningLocation<SshMachineLocation>(MutableMap.of("machines", ImmutableList.of(machine)));
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }

    @Test
    public void testProcessTemplateWithExtraSubstitutions() throws Exception {
        MyServiceImpl entity = new MyServiceImpl(app);
        Entities.manage(entity);
        entity.start(ImmutableList.of(loc));
        SimulatedDriver driver = (SimulatedDriver) entity.getDriver();
        Map<String,String> substitutions = MutableMap.of("myname","peter");
        String result = driver.processTemplate("/brooklyn/entity/basic/template_with_extra_substitutions.txt",substitutions);
        Assert.assertTrue(result.contains("peter"));
    }

    @Test
    public void testBasicSoftwareProcessEntityLifecycle() throws Exception {
        MyServiceImpl entity = new MyServiceImpl(app);
        Entities.manage(entity);
        entity.start(ImmutableList.of(loc));
        SimulatedDriver d = (SimulatedDriver) entity.getDriver();
        Assert.assertTrue(d.isRunning());
        entity.stop();
        Assert.assertEquals(d.events, ImmutableList.of("install", "customize", "launch", "stop"));
        Assert.assertFalse(d.isRunning());
    }
    
    @Test
    public void testShutdownIsIdempotent() throws Exception {
        MyServiceImpl entity = new MyServiceImpl(app);
        Entities.manage(entity);
        entity.start(ImmutableList.of(loc));
        entity.stop();
        
        entity.stop();
    }
    
    @Test
    public void testReleaseEvenIfErrorDuringStart() throws Exception {
        MyServiceImpl entity = new MyServiceImpl(app) {
            @Override public Class getDriverInterface() {
                return SimulatedFailOnStartDriver.class;
            }
        };
        Entities.manage(entity);
        
        try {
            entity.start(ImmutableList.of(loc));
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
    public void testReleaseEvenIfErrorDuringStop() throws Exception {
        MyServiceImpl entity = new MyServiceImpl(app) {
            @Override public Class getDriverInterface() {
                return SimulatedFailOnStopDriver.class;
            }
        };
        Entities.manage(entity);
        
        entity.start(ImmutableList.of(loc));
        try {
            entity.stop();
            Assert.fail();
        } catch (Exception e) {
            Assert.assertEquals(loc.getAvailable(), ImmutableSet.of(machine));
            IllegalStateException cause = Throwables2.getFirstThrowableOfType(e, IllegalStateException.class);
            if (cause == null || !cause.toString().contains("Simulating stop error")) throw e;
        }
    }

    @ImplementedBy(MyServiceImpl.class)
    public interface MyService extends SoftwareProcess {
    }
    
    public static class MyServiceImpl extends SoftwareProcessImpl implements MyService {
        public MyServiceImpl() {
        }

        public MyServiceImpl(Entity parent) {
            super(parent);
        }

        @Override
        public Class getDriverInterface() {
            return SimulatedDriver.class;
        }
    }

    public static class SimulatedFailOnStartDriver extends SimulatedDriver {
        public SimulatedFailOnStartDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
        }
        
        @Override
        public void install() {
            throw new IllegalStateException("Simulating start error");
        }
    }
    
    public static class SimulatedFailOnStopDriver extends SimulatedDriver {
        public SimulatedFailOnStopDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
        }
        
        @Override
        public void stop() {
            throw new IllegalStateException("Simulating stop error");
        }
    }
    
    public static class SimulatedDriver extends AbstractSoftwareProcessDriver {
        public List<String> events = new ArrayList<String>();
        private volatile boolean launched = false;
        
        public SimulatedDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
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
}

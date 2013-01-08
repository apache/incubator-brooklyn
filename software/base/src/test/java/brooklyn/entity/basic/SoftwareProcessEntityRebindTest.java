package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.SoftwareProcessEntityTest.MyService;
import brooklyn.entity.rebind.RebindEntityTest.MyApplication;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class SoftwareProcessEntityRebindTest {

    private ClassLoader classLoader = getClass().getClassLoader();
    private ManagementContext managementContext;
    private MyApplication origApp;
    private MyService origE;
    private File mementoDir;
    
    @BeforeMethod
    public void setUp() throws Exception {
        mementoDir = Files.createTempDir();
        managementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader);
        origApp = new MyApplication();
        Entities.startManagement(origApp, managementContext);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }
    
    @Test
    public void testReleasesLocationOnStopAfterRebinding() throws Exception {
        origE = new MyService(MutableMap.of(), origApp);
        Entities.manage(origE);
        
        MyProvisioningLocation origLoc = new MyProvisioningLocation(MutableMap.of("name", "mylocname"));
        origApp.start(ImmutableList.of(origLoc));
        assertEquals(origLoc.inUseCount.get(), 1);
        
        MyApplication newApp = (MyApplication) rebind();
        MyProvisioningLocation newLoc = (MyProvisioningLocation) Iterables.getOnlyElement(newApp.getLocations());
        assertEquals(newLoc.inUseCount.get(), 1);
        
        newApp.stop();
        assertEquals(newLoc.inUseCount.get(), 0);
    }

    private MyApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (MyApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
    
    public static class MyProvisioningLocation extends AbstractLocation implements MachineProvisioningLocation<SshMachineLocation> {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag(defaultVal="0")
        AtomicInteger inUseCount;

        public MyProvisioningLocation(Map flags) {
            super(flags);
        }

        @Override
        public SshMachineLocation obtain(Map flags) throws NoMachinesAvailableException {
            inUseCount.incrementAndGet();
            return new SshMachineLocation(MutableMap.of("address","localhost"));
        }

        @Override
        public void release(SshMachineLocation machine) {
            inUseCount.decrementAndGet();
        }

        @Override
        public Map getProvisioningFlags(Collection tags) {
            return Collections.emptyMap();
        }
    }
}

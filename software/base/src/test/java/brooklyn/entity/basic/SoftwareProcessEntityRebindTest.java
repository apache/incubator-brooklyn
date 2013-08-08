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
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class SoftwareProcessEntityRebindTest {

    private ClassLoader classLoader = getClass().getClassLoader();
    private ManagementContext managementContext;
    private TestApplication origApp;
    private MyService origE;
    private File mementoDir;
    
    @BeforeMethod
    public void setUp() throws Exception {
        mementoDir = Files.createTempDir();
        managementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader);
        origApp = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }
    
    @Test
    public void testReleasesLocationOnStopAfterRebinding() throws Exception {
        origE = origApp.createAndManageChild(EntitySpec.create(MyService.class));
        
        MyProvisioningLocation origLoc = new MyProvisioningLocation(MutableMap.of("name", "mylocname"));
        origApp.start(ImmutableList.of(origLoc));
        assertEquals(origLoc.inUseCount.get(), 1);
        
        TestApplication newApp = (TestApplication) rebind();
        MyProvisioningLocation newLoc = (MyProvisioningLocation) Iterables.getOnlyElement(newApp.getLocations());
        assertEquals(newLoc.inUseCount.get(), 1);
        
        newApp.stop();
        assertEquals(newLoc.inUseCount.get(), 0);
    }

    private TestApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
    
    public static class MyProvisioningLocation extends AbstractLocation implements MachineProvisioningLocation<SshMachineLocation> {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag(defaultVal="0")
        AtomicInteger inUseCount;

        public MyProvisioningLocation(Map flags) {
            super(flags);
        }

        @Override
        public MachineProvisioningLocation<SshMachineLocation> newSubLocation(Map<?, ?> newFlags) {
            throw new UnsupportedOperationException();
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

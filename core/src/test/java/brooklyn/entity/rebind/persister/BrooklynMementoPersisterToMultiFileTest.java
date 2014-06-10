package brooklyn.entity.rebind.persister;

import java.io.File;

import org.testng.annotations.AfterMethod;

import brooklyn.entity.rebind.RebindManagerImpl;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.os.Os;
import brooklyn.util.time.Duration;

import com.google.common.io.Files;

/**
 * @author Andrea Turli
 * @deprecated just tests the deprecated {@link BrooklynMementoPersisterToMultiFile}
 */
public class BrooklynMementoPersisterToMultiFileTest extends BrooklynMementoPersisterTestFixture {

    protected File mementoDir;
    
    @SuppressWarnings("deprecation")
    @Override
    protected LocalManagementContext newPersistingManagementContext() {
        mementoDir = Files.createTempDir();
        Os.deleteOnExitRecursively(mementoDir);
        
        LocalManagementContext mgmt = new LocalManagementContextForTests();
        ((RebindManagerImpl) mgmt.getRebindManager()).setPeriodicPersistPeriod(Duration.millis(100));
        persister = new BrooklynMementoPersisterToMultiFile(mementoDir, BrooklynMementoPersisterToMultiFileTest.class.getClassLoader());
        mgmt.getRebindManager().setPersister(persister);
        mgmt.getHighAvailabilityManager().disabled();
        mgmt.getRebindManager().start();
        return mgmt;
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
        super.tearDown();
    }

    // to have this picked up in the IDE
//    @Test public void noop() {}

}

package brooklyn.entity.rebind.persister;

import java.io.File;

import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.os.Os;

import com.google.common.io.Files;

/**
 * @author Andrea Turli
 */
public class BrooklynMementoPersisterToFileBasedObjectStoreTest extends BrooklynMementoPersisterTestFixture {

    protected File mementoDir;
    
    protected ManagementContext newPersistingManagementContext() {
        mementoDir = Files.createTempDir();
        Os.deleteOnExitRecursively(mementoDir);
        LocalManagementContext mgmt = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
        return mgmt;
    }
    
    // to have this picked up in the IDE
//  @Test public void noop() {}
    
}

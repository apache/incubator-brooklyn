package brooklyn.entity.rebind.persister;

import java.io.File;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.management.ManagementContext;
import brooklyn.util.os.Os;
import brooklyn.util.time.Duration;

import com.google.common.io.Files;

/**
 * @author Andrea Turli
 */
@Test
public class BrooklynMementoPersisterFileBasedTest extends BrooklynMementoPersisterTestFixture {

    protected File mementoDir;
    
    protected ManagementContext newPersistingManagementContext() {
        mementoDir = Files.createTempDir();
        Os.deleteOnExitRecursively(mementoDir);
        return RebindTestUtils.managementContextBuilder(classLoader, new FileBasedObjectStore(mementoDir))
            .persistPeriod(Duration.millis(10)).buildStarted();
    }
    
}

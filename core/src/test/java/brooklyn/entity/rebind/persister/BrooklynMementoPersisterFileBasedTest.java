package brooklyn.entity.rebind.persister;

import java.io.File;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.management.ManagementContext;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.os.Os;
import brooklyn.util.time.Duration;

/**
 * @author Andrea Turli
 */
@Test
public class BrooklynMementoPersisterFileBasedTest extends BrooklynMementoPersisterTestFixture {

    protected File mementoDir;
    
    protected ManagementContext newPersistingManagementContext() {
        mementoDir = Os.newTempDir(JavaClassNames.cleanSimpleClassName(this));
        Os.deleteOnExitRecursively(mementoDir);
        return RebindTestUtils.managementContextBuilder(classLoader, new FileBasedObjectStore(mementoDir))
            .persistPeriod(Duration.millis(10)).buildStarted();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        mementoDir = Os.deleteRecursively(mementoDir).asNullOrThrowing();
        super.tearDown();
    }

}

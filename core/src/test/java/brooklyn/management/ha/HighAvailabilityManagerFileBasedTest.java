package brooklyn.management.ha;

import java.io.File;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.FileBasedObjectStore;
import brooklyn.util.os.Os;

@Test
public class HighAvailabilityManagerFileBasedTest extends HighAvailabilityManagerTestFixture {

    private File dir;

    protected FileBasedObjectStore newPersistenceObjectStore() {
        if (dir!=null)
            throw new IllegalStateException("Test does not support multiple object stores");
        dir = Os.newTempDir(getClass());
        return new FileBasedObjectStore(dir);
    }

    @Override
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        super.tearDown();
        dir = Os.deleteRecursively(dir).asNullOrThrowing();
    }
}

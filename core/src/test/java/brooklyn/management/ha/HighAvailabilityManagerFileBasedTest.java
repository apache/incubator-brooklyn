package brooklyn.management.ha;

import java.io.File;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.FileBasedObjectStore;
import brooklyn.util.os.Os;

@Test
public class HighAvailabilityManagerFileBasedTest extends HighAvailabilityManagerTestFixture {

    private File dir;

    protected FileBasedObjectStore newPersistenceObjectStore() {
        dir = Os.newTempDir(getClass());
        return new FileBasedObjectStore(dir);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        dir = Os.deleteRecursively(dir).asNullOrThrowing();
    }
}

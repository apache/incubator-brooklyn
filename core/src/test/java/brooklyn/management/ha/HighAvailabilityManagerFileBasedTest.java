package brooklyn.management.ha;

import java.io.File;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.FileBasedObjectStore;
import brooklyn.util.os.Os;

import com.google.common.io.Files;

@Test
public class HighAvailabilityManagerFileBasedTest extends HighAvailabilityManagerTestFixture {

    protected FileBasedObjectStore newPersistenceObjectStore() {
        File dir = Files.createTempDir();
        Os.deleteOnExitRecursively(dir);
        return new FileBasedObjectStore(dir);
    }
    
}

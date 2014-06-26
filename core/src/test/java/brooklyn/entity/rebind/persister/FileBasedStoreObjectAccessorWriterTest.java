package brooklyn.entity.rebind.persister;

import java.io.File;
import java.io.IOException;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessorWithLock;
import brooklyn.util.os.Os;
import brooklyn.util.time.Duration;

@Test
public class FileBasedStoreObjectAccessorWriterTest extends PersistenceStoreObjectAccessorWriterTestFixture {

    protected StoreObjectAccessorWithLock newPersistenceStoreObjectAccessor() throws IOException {
        File file = Os.newTempFile(getClass(), "txt");
        return new StoreObjectAccessorLocking(new FileBasedStoreObjectAccessor(file, ".tmp"));
    }
    
    @Override
    protected Duration getLastModifiedResolution() {
        // OSX is 1s, Windows FAT is 2s !
        return Duration.seconds(2);
    }
    
    @Test(groups="Integration")
    public void testLastModifiedTime() throws Exception {
        super.testLastModifiedTime();
    }
    
}

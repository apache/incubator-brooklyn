package brooklyn.entity.rebind.persister;

import java.io.File;
import java.io.IOException;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessorWithLock;
import brooklyn.util.os.Os;

@Test
public class FileBasedStoreObjectAccessorWriterTest extends PersistenceStoreObjectAccessorWriterTestFixture {

    protected StoreObjectAccessorWithLock newPersistenceStoreObjectAccessor() throws IOException {
        File file = Os.newTempFile(getClass(), "txt");
        return new StoreObjectAccessorLocking(new FileBasedStoreObjectAccessor(file, ".tmp"));
    }
    
}

package brooklyn.entity.rebind.persister;

import java.io.File;
import java.io.IOException;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessorWithLock;

@Test
public class FileBasedStoreObjectAccessorWriterTest extends PersistenceStoreObjectAccessorWriterTestFixture {

    protected StoreObjectAccessorWithLock newPersistenceStoreObjectAccessor() throws IOException {
        File file = File.createTempFile("objectAccessorWriterTest", ".txt");
        file.deleteOnExit();
        return new StoreObjectAccessorLocking(new FileBasedStoreObjectAccessor(file, ".tmp"));
    }
    
}

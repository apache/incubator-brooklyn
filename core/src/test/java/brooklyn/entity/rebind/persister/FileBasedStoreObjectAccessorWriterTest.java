package brooklyn.entity.rebind.persister;

import java.io.File;
import java.io.IOException;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessor;

@Test
public class FileBasedStoreObjectAccessorWriterTest extends PersistenceStoreObjectAccessorWriterTestFixture {

    protected StoreObjectAccessor newPersistenceStoreObjectAccessor() throws IOException {
        File file = File.createTempFile("objectAccessorWriterTest", ".txt");
        file.deleteOnExit();
        return new FileBasedStoreObjectAccessor(file, executor, ".tmp");
    }
    
}

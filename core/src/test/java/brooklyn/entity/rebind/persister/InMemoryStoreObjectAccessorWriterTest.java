package brooklyn.entity.rebind.persister;

import java.io.IOException;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessor;

@Test
public class InMemoryStoreObjectAccessorWriterTest extends PersistenceStoreObjectAccessorWriterTestFixture {

    protected StoreObjectAccessor newPersistenceStoreObjectAccessor() throws IOException {
        return new InMemoryObjectStore().newAccessor("foo");
    }
    
}

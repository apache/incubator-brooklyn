package brooklyn.entity.rebind.persister;

import java.io.IOException;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessorWithLock;

@Test
public class InMemoryStoreObjectAccessorWriterTest extends PersistenceStoreObjectAccessorWriterTestFixture {

    protected StoreObjectAccessorWithLock newPersistenceStoreObjectAccessor() throws IOException {
        InMemoryObjectStore store = new InMemoryObjectStore();
        store.prepareForUse(null, null);
        return new StoreObjectAccessorLocking(store.newAccessor("foo"));
    }
    
}

package brooklyn.entity.rebind.persister;

import java.io.IOException;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessorWithLock;

@Test
public class InMemoryStoreObjectAccessorWriterTest extends PersistenceStoreObjectAccessorWriterTestFixture {

    protected StoreObjectAccessorWithLock newPersistenceStoreObjectAccessor() throws IOException {
        return new StoreObjectAccessorLocking(new InMemoryObjectStore().newAccessor("foo"));
    }
    
}

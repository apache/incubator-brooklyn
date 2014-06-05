package brooklyn.management.ha;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.InMemoryObjectStore;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;

@Test
public class HighAvailabilityManagerInMemoryTest extends HighAvailabilityManagerTestFixture {

    protected PersistenceObjectStore newPersistenceObjectStore() {
        return new InMemoryObjectStore();
    }
    
}

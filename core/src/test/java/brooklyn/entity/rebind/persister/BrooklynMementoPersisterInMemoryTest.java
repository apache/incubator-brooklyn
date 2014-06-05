package brooklyn.entity.rebind.persister;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.management.ManagementContext;
import brooklyn.util.time.Duration;

@Test
public class BrooklynMementoPersisterInMemoryTest extends BrooklynMementoPersisterTestFixture {

    protected ManagementContext newPersistingManagementContext() {
        return RebindTestUtils.managementContextBuilder(classLoader, new InMemoryObjectStore())
            .persistPeriod(Duration.millis(10)).buildStarted();
    }
}

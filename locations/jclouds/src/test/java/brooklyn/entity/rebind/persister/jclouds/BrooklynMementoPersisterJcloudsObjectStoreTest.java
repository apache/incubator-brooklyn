package brooklyn.entity.rebind.persister.jclouds;


import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterTestFixture;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Duration;

/**
 * @author Andrea Turli
 */
@Test(groups="Integration")
public class BrooklynMementoPersisterJcloudsObjectStoreTest extends BrooklynMementoPersisterTestFixture {

    protected LocalManagementContext newPersistingManagementContext() {
        objectStore = new JcloudsBlobStoreBasedObjectStore(
            BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC, BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4));
        return RebindTestUtils.managementContextBuilder(classLoader, objectStore)
            .persistPeriod(Duration.ONE_MILLISECOND)
            .properties(BrooklynProperties.Factory.newDefault())
            .buildStarted();
    }
    
}

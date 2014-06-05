package brooklyn.entity.rebind.persister.jclouds;

import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.management.ha.HighAvailabilityManagerTestFixture;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.text.Identifiers;

@Test(groups="Integration")
public class HighAvailabilityManagerJcloudsObjectStoreTest extends HighAvailabilityManagerTestFixture {

    protected ManagementContextInternal newLocalManagementContext() {
        return new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());
    }

    protected PersistenceObjectStore newPersistenceObjectStore() {
        return new JcloudsBlobStoreBasedObjectStore(
            BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC, BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4));
    }
    
    @Test(groups="Integration", invocationCount=5) //run fewer times w softlayer... 
    public void testGetManagementPlaneStatusManyTimes() throws Exception {
        testGetManagementPlaneStatus();
    }
    
}

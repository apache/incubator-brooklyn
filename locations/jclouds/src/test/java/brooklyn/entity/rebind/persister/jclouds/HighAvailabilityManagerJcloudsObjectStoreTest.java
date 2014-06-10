package brooklyn.entity.rebind.persister.jclouds;

import org.testng.annotations.BeforeMethod;
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

    @Override @BeforeMethod
    public void setUp() throws Exception { super.setUp(); }
    
    protected PersistenceObjectStore newPersistenceObjectStore() {
        return new JcloudsBlobStoreBasedObjectStore(
            BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC, BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4));
    }

    @Test(groups="Integration", invocationCount=5) //run fewer times w softlayer... 
    public void testGetManagementPlaneStatusManyTimes() throws Exception {
        testGetManagementPlaneStatus();
    }
    
    @Test(groups="Integration")
    @Override
    public void testDoesNotPromoteIfMasterTimeoutNotExpired() throws Exception {
        super.testDoesNotPromoteIfMasterTimeoutNotExpired();
    }
    
    @Test(groups="Integration")
    @Override
    public void testGetManagementPlaneStatus() throws Exception {
        super.testGetManagementPlaneStatus();
    }
    
    @Test(groups="Integration")
    @Override
    public void testPromotes() throws Exception {
        super.testPromotes();
    }

    @Test(groups="Integration")
    @Override
    public void testGetManagementPlaneSyncStateInfersTimedOutNodeAsFailed() throws Exception {
        super.testGetManagementPlaneSyncStateInfersTimedOutNodeAsFailed();
    }
    
    @Test(groups="Integration")
    @Override
    public void testGetManagementPlaneSyncStateDoesNotThrowNpeBeforePersisterSet() throws Exception {
        super.testGetManagementPlaneSyncStateDoesNotThrowNpeBeforePersisterSet();
    }
}

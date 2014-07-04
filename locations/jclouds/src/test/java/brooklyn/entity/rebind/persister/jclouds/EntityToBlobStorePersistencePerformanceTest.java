package brooklyn.entity.rebind.persister.jclouds;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.qa.performance.EntityPersistencePerformanceTest;

public class EntityToBlobStorePersistencePerformanceTest extends EntityPersistencePerformanceTest {

    private static final String LOCATION_SPEC = BlobStorePersistencePerformanceTest.LOCATION_SPEC;
    
    private JcloudsBlobStoreBasedObjectStore objectStore;

    @Override
    protected LocalManagementContext createOrigManagementContext() {
        objectStore = new JcloudsBlobStoreBasedObjectStore(LOCATION_SPEC, "EntityToBlobStorePersistencePerformanceTest");
        
        return RebindTestUtils.managementContextBuilder(classLoader, objectStore)
                .forLive(true)
                .persistPeriodMillis(getPersistPeriodMillis())
                .buildStarted();
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (objectStore != null) {
            objectStore.deleteCompletely();
            objectStore.close();
        }
    }

    @Test(groups="Live")
    @Override
    public void testManyEntities() throws Exception {
        super.testManyEntities();
    }
    
    @Test(groups="Live")
    @Override
    public void testRapidChanges() throws Exception {
        super.testRapidChanges();
    }
}

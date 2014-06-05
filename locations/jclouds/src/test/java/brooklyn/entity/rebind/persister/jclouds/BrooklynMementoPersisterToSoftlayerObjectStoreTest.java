package brooklyn.entity.rebind.persister.jclouds;


import java.io.IOException;
import java.util.concurrent.TimeoutException;

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
public class BrooklynMementoPersisterToSoftlayerObjectStoreTest extends BrooklynMementoPersisterTestFixture {

    public static final String PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC = "named:softlayer-objectstore-amsterdam-1";
    public static final String CONTAINER_PREFIX = "brooklyn-blobstore-persistence-test";
    private String containerName;
    
    protected LocalManagementContext newPersistingManagementContext() {
        containerName = CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4);
        objectStore = new JcloudsBlobStoreBasedObjectStore(PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC, containerName);
        return RebindTestUtils.managementContextBuilder(classLoader, objectStore)
            .persistPeriod(Duration.ONE_MILLISECOND)
            .properties(BrooklynProperties.Factory.newDefault())
            .buildStarted();
    }
    
    // redeclared as integration
    
    @Test(groups="Integration")
    public void testCheckPointAndLoadMementoUsingFileBasedObjectStore() throws IOException, TimeoutException, InterruptedException {
        super.testCheckPointAndLoadMementoUsingFileBasedObjectStore();
    }

    @Test(groups="Integration", dependsOnMethods = "testCheckPointAndLoadMementoUsingFileBasedObjectStore")
    public void testDeltaAndLoadMementoUsingFileBasedObjectStore() throws TimeoutException, InterruptedException, IOException {
        super.testDeltaAndLoadMementoUsingFileBasedObjectStore();
    }

}

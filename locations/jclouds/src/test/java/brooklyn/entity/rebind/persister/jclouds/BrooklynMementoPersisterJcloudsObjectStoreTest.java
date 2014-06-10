package brooklyn.entity.rebind.persister.jclouds;


import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.testng.annotations.BeforeMethod;
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

    @Override @BeforeMethod
    public void setUp() throws Exception { super.setUp(); }
    
    protected LocalManagementContext newPersistingManagementContext() {
        objectStore = new JcloudsBlobStoreBasedObjectStore(
            BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC, BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4));
        return RebindTestUtils.managementContextBuilder(classLoader, objectStore)
            .persistPeriod(Duration.ONE_MILLISECOND)
            .properties(BrooklynProperties.Factory.newDefault())
            .buildStarted();
    }
    
    @Test(groups="Integration")
    @Override
    public void testCheckPointAndLoadMemento() throws IOException, TimeoutException, InterruptedException {
        super.testCheckPointAndLoadMemento();
    }
    
    @Test(groups="Integration")
    @Override
    public void testDeleteAndLoadMemento() throws TimeoutException, InterruptedException, IOException {
        super.testDeleteAndLoadMemento();
    }
    
}

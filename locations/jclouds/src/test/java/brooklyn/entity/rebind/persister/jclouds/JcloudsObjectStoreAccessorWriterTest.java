package brooklyn.entity.rebind.persister.jclouds;

import java.io.IOException;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessor;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.PersistenceStoreObjectAccessorWriterTestFixture;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.text.Identifiers;

@Test(groups="Integration")
public class JcloudsObjectStoreAccessorWriterTest extends PersistenceStoreObjectAccessorWriterTestFixture {

    private JcloudsBlobStoreBasedObjectStore store;
    private LocalManagementContextForTests mgmt;

    @Override @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        store = new JcloudsBlobStoreBasedObjectStore(
            BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC, BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(4));
        store.prepareForUse(mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault()), PersistMode.CLEAN);
        super.setUp();
    }

    @Override @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        super.tearDown();
        if (mgmt!=null) Entities.destroyAll(mgmt);
        store.deleteCompletely();
    }
    
    protected StoreObjectAccessor newPersistenceStoreObjectAccessor() throws IOException {
        return store.newAccessor("sample-file-"+Identifiers.makeRandomId(4));
    }

    protected int biggishSize() {
        // bit smaller since it's actually uploading here!
        return 10000;
    }
    
}

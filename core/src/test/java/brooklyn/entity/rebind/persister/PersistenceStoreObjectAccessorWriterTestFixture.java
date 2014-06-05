package brooklyn.entity.rebind.persister;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.io.IOException;
import java.util.concurrent.Executors;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessorWithLock;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Duration;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

public abstract class PersistenceStoreObjectAccessorWriterTestFixture {

    private static final Duration TIMEOUT = Duration.TEN_SECONDS;
    
    protected ListeningExecutorService executor;
    protected StoreObjectAccessorWithLock accessor;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        accessor = newPersistenceStoreObjectAccessor();
    }

    protected abstract StoreObjectAccessorWithLock newPersistenceStoreObjectAccessor() throws IOException;
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (accessor != null) {
            accessor.delete();
            accessor.waitForCurrentWrites(Duration.TEN_SECONDS);
        }
        if (executor != null) executor.shutdownNow();
    }

    @Test
    public void testWritesFile() throws Exception {
        accessor.put("abc");
        accessor.waitForCurrentWrites(TIMEOUT);

        assertEquals(accessor.get(), "abc");
    }
    
    @Test
    public void testWriteBacklogThenDeleteWillLeaveFileDeleted() throws Exception {
        String big = makeBigString(biggishSize());
        
        accessor.put(big);
        accessor.put(big);
        accessor.delete();
        
        accessor.waitForCurrentWrites(TIMEOUT);
        assertFalse(accessor.exists());
    }

    protected int biggishSize() {
        return 100000;
    }
    
    protected String makeBigString(int size) {
        // prefer non-random so can't be compressed
        return Identifiers.makeRandomBase64Id(size);
//        return com.google.common.base.Strings.repeat("x", size);
    }
}

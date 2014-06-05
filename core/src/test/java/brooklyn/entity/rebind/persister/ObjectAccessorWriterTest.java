package brooklyn.entity.rebind.persister;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.io.File;
import java.util.concurrent.Executors;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import brooklyn.util.time.Duration;

public class ObjectAccessorWriterTest {

    private static final Duration TIMEOUT = Duration.TEN_SECONDS;
    
    private File file;
    private ListeningExecutorService executor;
    private FileBasedStoreObjectAccessor writer;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        file = File.createTempFile("objectAccessorWriterTest", ".txt");
        executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        writer = new FileBasedStoreObjectAccessor(file, executor);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (executor != null) executor.shutdownNow();
        if (file != null) file.delete();
    }

    @Test
    public void testWritesFile() throws Exception {
        writer.writeAsync("abc");
        writer.waitForWriteCompleted(TIMEOUT);
        
        String fromfile = Files.asCharSource(file, Charsets.UTF_8).read();
        assertEquals(fromfile, "abc");
    }
    
    @Test
    public void testWriteBacklogThenDeleteWillLeaveFileDeleted() throws Exception {
        String big = makeBigString(100000);
        
        writer.writeAsync(big);
        writer.writeAsync(big);
        writer.deleteAsync();
        
        writer.waitForWriteCompleted(TIMEOUT);
        assertFalse(file.exists());
    }
    
    private String makeBigString(int size) {
        return com.google.common.base.Strings.repeat("x", size);
    }
}

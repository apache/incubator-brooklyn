package brooklyn.entity.rebind.persister;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.mementos.BrooklynMementoPersister.LookupContext;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

public class MementoFileWriterTest {

    private static final long TIMEOUT_MS = 10*1000;
    
    private File file;
    private ListeningExecutorService executor;
    private MementoSerializer<String> serializer;
    private MementoFileWriter<String> writer;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        file = File.createTempFile("mementoFileWriterTest", "txt");
        executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        serializer = new MementoSerializer<String>() {
            @Override public String toString(String memento) {
                return memento;
            }
            @Override public String fromString(String string) {
                return string;
            }
            @Override public void setLookupContext(LookupContext lookupContext) {
            }
            @Override public void unsetLookupContext() {
            }
        };
        writer = new MementoFileWriter<String>(file, executor, serializer);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (executor != null) executor.shutdownNow();
        if (file != null) file.delete();
        
    }

    @Test
    public void testWritesFile() throws Exception {
        writer.write("abc");
        writer.waitForWriteCompleted(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        
        String fromfile = Files.asCharSource(file, Charsets.UTF_8).read();
        assertEquals(fromfile, "abc");
    }
    
    @Test
    public void testWriteBacklogThenDeleteWillLeaveFileDeleted() throws Exception {
        // FIXME google for repeat(str, num) when online!
        String big = makeBigString(100000);
        
        writer.write(big);
        writer.write(big);
        writer.delete();
        
        writer.waitForWriteCompleted(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertFalse(file.exists());
    }
    
    private String makeBigString(int size) {
        StringBuilder result = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            result.append(i % 10);
        }
        return result.toString();
    }
}

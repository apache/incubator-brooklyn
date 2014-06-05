package brooklyn.entity.rebind.persister;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Duration;

import com.google.common.base.Objects;

public class InMemoryObjectStore implements PersistenceObjectStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryObjectStore.class);

    Map<String,String> filesByName = MutableMap.<String,String>of();
    boolean prepared = false;
    
    public InMemoryObjectStore() {
        log.info("Using memory-based objectStore");
    }
    
    @Override
    public String getSummaryName() {
        return "in-memory (test) persistence store";
    }
    
    @Override
    public void createSubPath(String subPath) {
        if (!prepared) throw new IllegalStateException("prepare method not yet invoked: "+this);
    }

    @Override
    public StoreObjectAccessor newAccessor(final String path) {
        return new StoreObjectAccessor() {
            @Override
            public void writeAsync(String val) {
                synchronized (filesByName) {
                    filesByName.put(path, val);
                }
            }
            
            @Override
            public void waitForWriteCompleted(Duration timeout) throws InterruptedException, TimeoutException {
            }
            
            @Override
            public String read() {
                synchronized (filesByName) {
                    return filesByName.get(path);
                }
            }
            
            @Override
            public boolean exists() {
                synchronized (filesByName) {
                    return filesByName.containsKey(path);
                }
            }
            
            @Override
            public void deleteAsync() {
                synchronized (filesByName) {
                    filesByName.remove(path);
                }
            }
            
            @Override
            public void append(String s) {
                synchronized (filesByName) {
                    writeAsync(read()+s);
                }
            }
        };
    }

    @Override
    public List<String> listContentsWithSubPath(final String parentSubPath) {
        synchronized (filesByName) {
            List<String> result = MutableList.of();
            for (String file: filesByName.keySet())
                if (file.startsWith(parentSubPath))
                    result.add(file);
            return result;
        }
    }

    @Override
    public void backupContents(String parentSubPath, String backupSubPath) {
    }

    @Override
    public void close() {
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("size", filesByName.size()).toString();
    }

    @Override
    public void prepareForUse(ManagementContext mgmt, @Nullable PersistMode persistMode) {
        prepared = true;
    }

    @Override
    public void deleteCompletely() {
        synchronized (filesByName) {
            filesByName.clear();
        }
    }

}

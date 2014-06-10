package brooklyn.entity.rebind.persister;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;

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
        return new StoreObjectAccessorLocking(new SingleThreadedInMemoryStoreObjectAccessor(filesByName, path));
    }
    
    public static class SingleThreadedInMemoryStoreObjectAccessor implements StoreObjectAccessor {
        private final Map<String, String> map;
        private final String key;

        public SingleThreadedInMemoryStoreObjectAccessor(Map<String,String> map, String key) {
            this.map = map;
            this.key = key;
        }
        @Override
        public String get() {
            synchronized (map) {
                return map.get(key);
            }
        }
        @Override
        public boolean exists() {
            synchronized (map) {
                return map.containsKey(key);
            }
        }
        @Override
        public void put(String val) {
            synchronized (map) {
                map.put(key, val);
            }
        }
        @Override
        public void append(String val) {
            synchronized (map) {
                String val2 = get();
                if (val2==null) val2 = val;
                else val2 = val2 + val;

                map.put(key, val);
            }
        }
        @Override
        public void delete() {
            synchronized (map) {
                map.remove(key);
            }
        }
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

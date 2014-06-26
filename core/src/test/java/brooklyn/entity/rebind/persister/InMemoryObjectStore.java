package brooklyn.entity.rebind.persister;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.ManagementContext;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Objects;

public class InMemoryObjectStore implements PersistenceObjectStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryObjectStore.class);

    final Map<String,String> filesByName;
    final Map<String, Date> fileModTimesByName;
    boolean prepared = false;
    
    public InMemoryObjectStore() {
        this(MutableMap.<String,String>of(), MutableMap.<String,Date>of());
    }
    
    public InMemoryObjectStore(Map<String,String> map, Map<String, Date> fileModTimesByName) {
        filesByName = map;
        this.fileModTimesByName = fileModTimesByName;
        log.debug("Using memory-based objectStore");
    }
    
    @Override
    public String getSummaryName() {
        return "in-memory (test) persistence store";
    }
    
    @Override
    public void prepareForMasterUse() {
    }

    @Override
    public void createSubPath(String subPath) {
    }

    @Override
    public StoreObjectAccessor newAccessor(final String path) {
        if (!prepared) throw new IllegalStateException("prepare method not yet invoked: "+this);
        return new StoreObjectAccessorLocking(new SingleThreadedInMemoryStoreObjectAccessor(filesByName, fileModTimesByName, path));
    }
    
    public static class SingleThreadedInMemoryStoreObjectAccessor implements StoreObjectAccessor {
        private final Map<String, String> map;
        private final Map<String, Date> mapModTime;
        private final String key;

        public SingleThreadedInMemoryStoreObjectAccessor(Map<String,String> map, Map<String, Date> mapModTime, String key) {
            this.map = map;
            this.mapModTime = mapModTime;
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
                mapModTime.put(key, new Date());
            }
        }
        @Override
        public void append(String val) {
            synchronized (map) {
                String val2 = get();
                if (val2==null) val2 = val;
                else val2 = val2 + val;

                map.put(key, val);
                mapModTime.put(key, new Date());
            }
        }
        @Override
        public void delete() {
            synchronized (map) {
                map.remove(key);
                mapModTime.remove(key);
            }
        }
        @Override
        public Date getLastModifiedDate() {
            synchronized (map) {
                return mapModTime.get(key);
            }
        }
    }

    @Override
    public List<String> listContentsWithSubPath(final String parentSubPath) {
        if (!prepared) throw new IllegalStateException("prepare method not yet invoked: "+this);
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
    public void injectManagementContext(ManagementContext mgmt) {
    }
    
    @Override
    public void prepareForSharedUse(PersistMode persistMode, HighAvailabilityMode haMode) {
        prepared = true;
    }

    @Override
    public void deleteCompletely() {
        synchronized (filesByName) {
            filesByName.clear();
        }
    }

}

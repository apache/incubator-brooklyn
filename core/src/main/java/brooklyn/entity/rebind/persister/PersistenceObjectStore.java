package brooklyn.entity.rebind.persister;

import java.util.List;
import java.util.concurrent.TimeoutException;

import brooklyn.management.ManagementContext;
import brooklyn.util.time.Duration;

/**
 * Interface for working with persistence targets, including file system and jclouds object stores.
 * @author Andrea Turli
 */
public interface PersistenceObjectStore {

    /** accessor to an object/item in a {@link PersistenceObjectStore} */
    public interface StoreObjectAccessor {
        boolean exists();
        void writeAsync(String val);
        void append(String s);
        void deleteAsync();
        String read();
        public void waitForWriteCompleted(Duration timeout) throws InterruptedException, TimeoutException;
    }

    /** human-readable name of this object store */
    public String getSummaryName();
    
    /**
     * For reading/writing data to the item at the given path
     */
    StoreObjectAccessor newAccessor(String path);

    /** create the directory at the given subPath relative to the base of this store */
    void createSubPath(String subPath);

    /**
     * Lists the paths of objects contained at the given path, including the subpath.
     * For example, if a file-based ObjectStore is configured to write to file://path/to/root/
     * then parentSubPath=entities would return the contents of /path/to/root/entities/, such as
     * [entities/e1, entities/e2, entities/e3].
     * The returned paths values are usable in calls to {@link #newAccessor(String)}.
     */
    List<String> listContentsWithSubPath(String subPath);

    /**
     * Makes a copy of all objects under parentSubPath.
     * For example, if a file-based ObjectStore is configured to write to file://path/to/root/
     * then parentSubPath="abc", backupSubPath="abc.bak" would be the same as:
     * `cp -R /path/to/root/abc/* /path/to/root/abc.bak/`
     * <p>
     * May be no-op if using a substrate which can provide this by alternate means (e.g. an object store).
     */
    void backupContents(String sourceSubPath, String targetSubPathForBackups);
    
    /**
     * Closes all resources used by this ObjectStore. No subsequent calls should be made to the ObjectStore;
     * behaviour of such calls is undefined but likely to throw exceptions.
     */
    void close();

    /**
     * Prepares the persistence directory for use (e.g. backing up old dir, checking is non-empty 
     * or deleting as required, creating, etc).
     * <p>
     * Also allows a way for an object store to be created ahead of time,
     * and a mgmt context injected.
     * (If persistMode is null, it is ignored.)
     * <p>
     * currently subsequent changes are not permitted.
     */
   public void prepareForUse(ManagementContext managementContext, PersistMode persistMode);

   /** Entirely delete the contents of this persistence location.
    * Use with care, primarily in tests. This will recursively wipe the indicated location. */ 
   public void deleteCompletely();

}
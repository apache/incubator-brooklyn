package brooklyn.entity.rebind.persister;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReadWriteLock;

import brooklyn.management.ManagementContext;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;

/**
 * Interface for working with persistence targets, including file system and jclouds object stores.
 * @author Andrea Turli
 */
public interface PersistenceObjectStore {

    /** accessor to an object/item in a {@link PersistenceObjectStore} */
    public interface StoreObjectAccessor {
        /** gets the object, or null if not found */
        String get();
        boolean exists();
        void put(String contentsToReplaceOrCreate);
        void append(String contentsToAppendOrCreate);
        void delete();
    }
    public interface StoreObjectAccessorWithLock extends StoreObjectAccessor {
        /** waits for all currently scheduled write lock operations (puts, appends, and deletes) to complete;
         * but does not wait on or prevent subsequent modifications.
         * this is suitable for a model where the caller is managing synchronization.
         * <p> 
         * for more complex uses, readers should <code>getLockObject().readLock().lockInterruptibly()</code> 
         * and ensure they subsequently <code>unlock()</code> it of course. see {@link #getLockObject()}. */
        void waitForCurrentWrites(Duration timeout) throws InterruptedException, TimeoutException;
        
        /** returns the underlying lock in case callers need more complex synchronization control */ 
        ReadWriteLock getLockObject();
    }

    /** human-readable name of this object store */
    public String getSummaryName();
    
    /** triggers any backup if this method has not been invoked; 
     * used to allow certain non-contentious writes (e.g. node id's)
     * without triggering backup, but then forcing backup before a contended write */
    public void prepareForContendedWrite();
    
    /**
     * For reading/writing data to the item at the given path.
     * Note that the accessor is not generally thread safe, usually does not support blocking,
     * and multiple instances may conflict with each other.
     * <p>
     * Clients should wrap in a dedicated {@link StoreObjectAccessorLocking} and share
     * if multiple threads may be accessing the store.
     */
    // TODO this is not a nice API, better would be to do caching here probably,
    // but we've already been doing it this way above for now (Jun 2014)
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

    /** Entirely delete the contents of this persistence location.
     * Use with care, primarily in tests. This will recursively wipe the indicated location. */ 
    public void deleteCompletely();
    
    /**
     * Closes all resources used by this ObjectStore. No subsequent calls should be made to the ObjectStore;
     * behaviour of such calls is undefined but likely to throw exceptions.
     */
    void close();

    /**
     * Allows a way for an object store to be created ahead of time, and a mgmt context injected.
     * Currently subsequent changes are not permitted.
     * <p>
     * A {@link ManagementContext} must be supplied via constructor or this method before invoking other methods.
     */
    @Beta
    public void injectManagementContext(ManagementContext managementContext);
    
    /**
     * Prepares the persistence directory for use 
     * (e.g. detecting whether any backup will be necessary, deleting as required, etc) 
     */
    // although there is some commonality between the different stores it is mostly different,
    // so this method currently sits here; it may move if advanced backup strategies have commonalities
    @Beta
    public void prepareForUse(PersistMode persistMode, HighAvailabilityMode highAvailabilityMode);

}
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.rebind.persister;

import java.util.Date;
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
        // NB: creation date is available for many blobstores but 
        // not on java.io.File and filesystems, so it is not included here 
        /** last modified date, null if not supported or does not exist */
        Date getLastModifiedDate();
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
    
    /**
     * Allows a way for an object store to be created ahead of time, and a mgmt context injected.
     * Currently subsequent changes are not permitted.
     * <p>
     * A {@link ManagementContext} must be supplied via constructor or this method before invoking other methods.
     */
    @Beta
    public void injectManagementContext(ManagementContext managementContext);
    
    /**
     * Prepares the persistence store for read use and non-contentious write use,
     * in particular detecting whether we should clean or register a need for backup etc.
     * Typically called early in the setup lifecycle, after {@link #injectManagementContext(ManagementContext)},
     * but before {@link #prepareForMasterUse()}.
     * <p>
     * See {@link #prepareForMasterUse()} for discussion of "contentious writes".
     */
    @Beta
    public void prepareForSharedUse(PersistMode persistMode, HighAvailabilityMode highAvailabilityMode);

    /** 
     * Prepares the persistence store for "contentious writes".
     * These are defined as those writes which might overwrite important information.
     * Implementations usually perform backup/versioning of the store if required.
     * <p>
     * Caller must call {@link #prepareForSharedUse(PersistMode, HighAvailabilityMode)} first
     * (and {@link #injectManagementContext(ManagementContext)} before that).
     * <p>
     * This is typically invoked "at the last moment" e.g. before the any such write,
     * mainly in order to prevent backups being made unnecessarily (e.g. if a node is standby,
     * or if it tries to become master but is not capable),
     * but also to prevent simultaneous backups which can cause problems with some stores
     * (only a mgmt who knows he is the master should invoke this).
     **/
    @Beta
    public void prepareForMasterUse();
    
    /**
     * For reading/writing data to the item at the given path.
     * Note that the accessor is not generally thread safe, usually does not support blocking,
     * and multiple instances may conflict with each other.
     * <p>
     * Clients should wrap in a dedicated {@link StoreObjectAccessorLocking} and share
     * if multiple threads may be accessing the store.
     * This method may be changed in future to allow access to a shared locking accessor.
     */
    @Beta
    // TODO requiring clients to wrap and cache accessors is not very nice API, 
    // better would be to do caching here probably,
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

}
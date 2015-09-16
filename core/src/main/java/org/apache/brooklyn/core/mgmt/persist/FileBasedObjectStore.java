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
package org.apache.brooklyn.core.mgmt.persist;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.ha.HighAvailabilityMode;
import org.apache.brooklyn.core.server.BrooklynServerConfig;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.FatalConfigurationRuntimeException;
import org.apache.brooklyn.util.io.FileUtil;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.os.Os.DeletionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * @author Andrea Turli
 */
public class FileBasedObjectStore implements PersistenceObjectStore {

    private static final Logger log = LoggerFactory.getLogger(FileBasedObjectStore.class);

    private static final int SHUTDOWN_TIMEOUT_MS = 10*1000;

    private final File basedir;
    private final ListeningExecutorService executor;
    private ManagementContext mgmt;
    private boolean prepared = false;
    private boolean deferredBackupNeeded = false;
    private AtomicBoolean doneFirstContentiousWrite = new AtomicBoolean(false);

    /**
     * @param basedir
     */
    public FileBasedObjectStore(File basedir) {
        this.basedir = checkPersistenceDirPlausible(basedir);
        this.executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        log.debug("File-based objectStore will use directory {}", basedir);
        // don't check accessible yet, we do that when we prepare
    }

    @Override
    public String getSummaryName() {
        return getBaseDir().getAbsolutePath();
    }
    
    public File getBaseDir() {
        return basedir;
    }
    
    public void prepareForMasterUse() {
        if (doneFirstContentiousWrite.get())
            return;
        synchronized (this) {
            if (doneFirstContentiousWrite.get())
                return;
            try {
                if (deferredBackupNeeded) {
                    // defer backup and path creation until first write
                    // this way if node is standby or auto, the backup is not created superfluously

                    File backup = backupDirByCopying(basedir);
                    log.info("Persistence deferred backup, directory "+basedir+" backed up to "+backup.getAbsolutePath());

                    deferredBackupNeeded = false;
                }
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
            doneFirstContentiousWrite.getAndSet(true);
        }
    }
    
    @Override
    public void createSubPath(String subPath) {
        if (!prepared) throw new IllegalStateException("Not yet prepared: "+this);
        
        File dir = new File(getBaseDir(), subPath);
        if (dir.mkdir()) {
            try {
                FileUtil.setFilePermissionsTo700(dir);
            } catch (IOException e) {
                log.warn("Unable to set sub-directory permissions to 700 (continuing): "+dir);
            }
        } else {
            if (!dir.exists())
                throw new IllegalStateException("Cannot create "+dir+"; call returned false");
        }
        checkPersistenceDirAccessible(dir);
    }

    @Override
    public StoreObjectAccessor newAccessor(String path) {
        if (!prepared) throw new IllegalStateException("Not yet prepared: "+this);
        
        String tmpExt = ".tmp";
        if (mgmt!=null && mgmt.getManagementNodeId()!=null) tmpExt = "."+mgmt.getManagementNodeId()+tmpExt;
        return new FileBasedStoreObjectAccessor(new File(Os.mergePaths(getBaseDir().getAbsolutePath(), path)), tmpExt);
    }

    @Override
    public List<String> listContentsWithSubPath(final String parentSubPath) {
        if (!prepared) throw new IllegalStateException("Not yet prepared: "+this);
        
        Preconditions.checkNotNull(parentSubPath);
        File subPathDir = new File(basedir, parentSubPath);

        FileFilter fileFilter = new FileFilter() {
            @Override public boolean accept(File file) {
                // An inclusion filter would be safer than exclusion
                return !file.getName().endsWith(".tmp") && !file.getName().endsWith(".swp");
            }
        };
        File[] subPathDirFiles = subPathDir.listFiles(fileFilter);
        if (subPathDirFiles==null) return ImmutableList.<String>of();
        return FluentIterable.from(Arrays.asList(subPathDirFiles))
                .transform(new Function<File, String>() {
                    @Nullable
                    @Override
                    public String apply(@Nullable File input) {
                        return format("%s/%s", parentSubPath, input.getName());
                    }
                }).toList();
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("basedir", basedir).toString();
    }

    @Override
    public void injectManagementContext(ManagementContext mgmt) {
        if (this.mgmt!=null && !this.mgmt.equals(mgmt))
            throw new IllegalStateException("Cannot change mgmt context of "+this);
        this.mgmt = mgmt;
    }
    
    @Override
    public void prepareForSharedUse(@Nullable PersistMode persistMode, HighAvailabilityMode haMode) {
        if (mgmt==null) throw new NullPointerException("Must inject ManagementContext before preparing "+this);
        
        if (persistMode==null || persistMode==PersistMode.DISABLED) {
            // TODO is this check needed? shouldn't come here now without persistence on.
            prepared = true;
            return;
        }
        
        @SuppressWarnings("deprecation")
        Boolean backups = mgmt.getConfig().getConfig(BrooklynServerConfig.PERSISTENCE_BACKUPS_REQUIRED);
        if (Boolean.TRUE.equals(backups)) {
            log.warn("Using legacy backup for "+this+"; functionality will be removed in future versions, in favor of promotion/demotion-specific backups to a configurable backup location.");
        }
        // default backups behaviour here changed to false, Nov 2014, because these backups are now legacy;
        // we prefer the made when persistence is enabled, using routines in BrooklynPersistenceUtils
        if (backups==null) backups = false; 

        File dir = getBaseDir();
        try {
            String persistencePath = dir.getAbsolutePath();

            switch (persistMode) {
            case CLEAN:
                if (dir.exists()) {
                    checkPersistenceDirAccessible(dir);
                    try {
                        if (backups) {
                            File old = backupDirByMoving(dir);
                            log.info("Persistence mode CLEAN, directory "+persistencePath+" backed up to "+old.getAbsolutePath());
                        } else {
                            deleteCompletely();
                            log.info("Persistence mode CLEAN, directory "+persistencePath+" deleted");
                        }
                    } catch (IOException e) {
                        throw new FatalConfigurationRuntimeException("Error using existing persistence directory "+dir.getAbsolutePath(), e);
                    }
                } else {
                    log.debug("Persistence mode CLEAN, directory "+persistencePath+", no previous state");
                }
                break;
            case REBIND:
                checkPersistenceDirAccessible(dir);
                checkPersistenceDirNonEmpty(dir);
                try {
                    if (backups) {
                        if (haMode==HighAvailabilityMode.MASTER) {
                            File backup = backupDirByCopying(dir);
                            log.info("Persistence mode REBIND, directory "+persistencePath+" backed up to "+backup.getAbsolutePath());                            
                        } else {
                            deferredBackupNeeded = true;
                        }
                    }
                } catch (IOException e) {
                    throw new FatalConfigurationRuntimeException("Error backing up persistence directory "+dir.getAbsolutePath(), e);
                }
                break;
            case AUTO:
                if (dir.exists()) {
                    checkPersistenceDirAccessible(dir);
                }
                if (dir.exists() && !isMementoDirExistButEmpty(dir)) {
                    try {
                        if (backups) {
                            if (haMode==HighAvailabilityMode.MASTER) {
                                File backup = backupDirByCopying(dir);
                                log.info("Persistence mode REBIND, directory "+persistencePath+" backed up to "+backup.getAbsolutePath());                            
                            } else {
                                deferredBackupNeeded = true;
                            }
                        }
                    } catch (IOException e) {
                        throw new FatalConfigurationRuntimeException("Error backing up persistence directory "+dir.getAbsolutePath(), e);
                    }
                } else {
                    log.debug("Persistence mode AUTO, directory "+persistencePath+", no previous state");
                }
                break;
            default:
                throw new FatalConfigurationRuntimeException("Unexpected persist mode "+persistMode+"; modified during initialization?!");
            };

            if (!dir.exists()) {
                boolean success = dir.mkdirs();
                if (success) {
                    FileUtil.setFilePermissionsTo700(dir);
                } else {
                    throw new FatalConfigurationRuntimeException("Failed to create persistence directory "+dir);
                }
            }

        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
        
        prepared = true;        
    }

    protected File checkPersistenceDirPlausible(File dir) {
        checkNotNull(dir, "directory");
        if (!dir.exists()) return dir;
        if (dir.isFile()) throw new FatalConfigurationRuntimeException("Invalid persistence directory" + dir + ": must not be a file");
        if (!(dir.canRead() && dir.canWrite())) throw new FatalConfigurationRuntimeException("Invalid persistence directory" + dir + ": " +
                (!dir.canRead() ? "not readable" :
                        (!dir.canWrite() ? "not writable" : "unknown reason")));
        return dir;
    }

    protected void checkPersistenceDirAccessible(File dir) {
        if (!(dir.exists() && dir.isDirectory() && dir.canRead() && dir.canWrite())) {
            FatalConfigurationRuntimeException problem = new FatalConfigurationRuntimeException("Invalid persistence directory " + dir + ": " +
                    (!dir.exists() ? "does not exist" :
                            (!dir.isDirectory() ? "not a directory" :
                                    (!dir.canRead() ? "not readable" :
                                            (!dir.canWrite() ? "not writable" : "unknown reason")))));
            log.debug("Invalid persistence directory "+dir+" (rethrowing): "+problem, problem);
        } else {
            log.debug("Created dir {} for {}", dir, this);
        }
    }

    protected void checkPersistenceDirNonEmpty(File persistenceDir) {
        FatalConfigurationRuntimeException problem;
        if (!persistenceDir.exists()) {
            problem = new FatalConfigurationRuntimeException("Invalid persistence directory "+persistenceDir+" because directory does not exist");
            log.debug("Invalid persistence directory "+persistenceDir+" (rethrowing): "+problem, problem);
            throw problem;
        } if (isMementoDirExistButEmpty(persistenceDir)) {
            problem = new FatalConfigurationRuntimeException("Invalid persistence directory "+persistenceDir+" because directory is empty");
            log.debug("Invalid persistence directory "+persistenceDir+" (rethrowing): "+problem, problem);
            throw problem;
        }
    }

    protected File backupDirByCopying(File dir) throws IOException, InterruptedException {
        File parentDir = dir.getParentFile();
        String simpleName = dir.getName();
        String timestamp = new SimpleDateFormat("yyyyMMdd-hhmmssSSS").format(new Date());
        File backupDir = new File(parentDir, simpleName+"."+timestamp+".bak");
        
        FileUtil.copyDir(dir, backupDir);
        FileUtil.setFilePermissionsTo700(backupDir);
        
        return backupDir;
    }

    protected File backupDirByMoving(File dir) throws InterruptedException, IOException {
        File parentDir = dir.getParentFile();
        String simpleName = dir.getName();
        String timestamp = new SimpleDateFormat("yyyyMMdd-hhmmssSSS").format(new Date());
        File newDir = new File(parentDir, simpleName+"."+timestamp+".bak");

        FileUtil.moveDir(dir, newDir);
        return newDir;
    }

    /** 
     * Attempts an fs level atomic move then fall back to pure java rename.
     * Assumes files are on same mount point.
     * Overwriting existing destFile
     */
    static void moveFile(File srcFile, File destFile) throws IOException, InterruptedException {
        if (destFile.isDirectory()) {
            deleteCompletely(destFile);
        }

        try {
            Files.move(srcFile.toPath(), destFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        
        if (log.isTraceEnabled()) {
            log.trace("Completly moved from {} to {} completed", new Object[] { srcFile, destFile });
        }
    }

    /**
     * True if directory exists, but is entirely empty, or only contains empty directories.
     */
    static boolean isMementoDirExistButEmpty(String dir) {
        return isMementoDirExistButEmpty(new File(dir));
    }
    
    static boolean isMementoDirExistButEmpty(File dir) {
        if (!dir.exists()) return false;
        File[] contents = dir.listFiles();
        if (contents == null) return false;
        
        for (File sub : contents) {
            if (sub.isFile()) return false;
            if (sub.isDirectory() && sub.listFiles().length > 0) return false;
        }
        return true;
    }

    @Override
    public void deleteCompletely() {
        deleteCompletely(getBaseDir());
    }
    
    public static void deleteCompletely(File d) {
        DeletionResult result = Os.deleteRecursively(d);
        if (!result.wasSuccessful())
            log.warn("Unable to delete persistence dir "+d);
    }
}

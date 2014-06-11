package brooklyn.entity.rebind.persister;

import static java.lang.String.format;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynServerConfig;
import brooklyn.management.ManagementContext;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.FatalConfigurationRuntimeException;
import brooklyn.util.os.Os;
import brooklyn.util.os.Os.DeletionResult;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.FluentIterable;
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

    /**
     * @param basedir
     */
    public FileBasedObjectStore(File basedir) {
        this.basedir = basedir;
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
    
    @Override
    public void createSubPath(String subPath) {
        File dir = new File(getBaseDir(), subPath);
        dir.mkdir();
        checkPersistenceDirAccessible(dir);
    }

    @Override
    public StoreObjectAccessor newAccessor(String path) {
        String tmpExt = ".tmp";
        if (mgmt!=null && mgmt.getManagementNodeId()!=null) tmpExt = "."+mgmt.getManagementNodeId()+tmpExt;
        return new FileBasedStoreObjectAccessor(new File(Os.mergePaths(getBaseDir().getAbsolutePath(), path)), tmpExt);
    }

    @Override
    public List<String> listContentsWithSubPath(final String parentSubPath) {
        File subPathDir = new File(basedir, parentSubPath);

        FileFilter fileFilter = new FileFilter() {
            @Override public boolean accept(File file) {
                return !file.getName().endsWith(".tmp");
            }
        };
        File[] subPathDirFiles = subPathDir.listFiles(fileFilter);
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
    public void prepareForUse(ManagementContext mgmt, @Nullable PersistMode persistMode) {
        if (this.mgmt!=null && !this.mgmt.equals(mgmt))
            throw new IllegalStateException("Cannot change mgmt context of "+this);
        this.mgmt = mgmt;

        if (persistMode==null || persistMode==PersistMode.DISABLED)
            // is this check needed? shouldn't come here now without persistence on.
            return;
        
        Boolean backups = mgmt.getConfig().getConfig(BrooklynServerConfig.PERSISTENCE_BACKUPS_REQUIRED);
        if (backups==null) backups = true; // for file system

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
                        File backup = backupDirByCopying(dir);
                        log.info("Persistence mode REBIND, directory "+persistencePath+" backed up to "+backup.getAbsolutePath());
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
                            File backup = backupDirByCopying(dir);
                            log.info("Persistence mode REBIND, directory "+persistencePath+" backed up to "+backup.getAbsolutePath());
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
                if (!success) {
                    throw new FatalConfigurationRuntimeException("Failed to create persistence directory "+dir);
                }
            }

        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    protected void checkPersistenceDirAccessible(File dir) {
        if (!(dir.exists() && dir.isDirectory() && dir.canRead() && dir.canWrite())) {
            throw new FatalConfigurationRuntimeException("Invalid persistence directory " + dir + ": " +
                    (!dir.exists() ? "does not exist" :
                            (!dir.isDirectory() ? "not a directory" :
                                    (!dir.canRead() ? "not readable" :
                                            (!dir.canWrite() ? "not writable" : "unknown reason")))));
        } else {
            log.debug("Created dir {} for {}", dir, this);
        }
    }

    protected void checkPersistenceDirNonEmpty(File persistenceDir) {
        if (!persistenceDir.exists())
            throw new FatalConfigurationRuntimeException("Invalid persistence directory "+persistenceDir+" because directory does not exist");
        if (isMementoDirExistButEmpty(persistenceDir)) {
            throw new FatalConfigurationRuntimeException("Invalid persistence directory "+persistenceDir+" because directory is empty");
        }
    }

    protected File backupDirByCopying(File dir) throws IOException, InterruptedException {
        File parentDir = dir.getParentFile();
        String simpleName = dir.getName();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd-hhmm-ss").format(new Date());
        File backupDir = new File(parentDir, simpleName+"-"+timestamp+".bak");
        
        copyDir(dir, backupDir);
        return backupDir;
    }

    protected File backupDirByMoving(File dir) throws InterruptedException, IOException {
        File parentDir = dir.getParentFile();
        String simpleName = dir.getName();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd-hhmm-ss").format(new Date());
        File newDir = new File(parentDir, simpleName+"-"+timestamp+".old");

        moveDir(dir, newDir);
        return newDir;
    }

    /** 
     * Attempts an fs level atomic move then fall back to pure java rename.
     * Assumes files are on same mount point.
     * <p>
     * TODO Java 7 gives an atomic Files.move() which would be preferred.
     */
    static void moveFile(File srcFile, File destFile) throws IOException, InterruptedException {
        if (!Os.isMicrosoftWindows()) {
            String cmd = "mv '"+srcFile.getAbsolutePath()+"' '"+destFile.getAbsolutePath()+"'";
            Process proc = Runtime.getRuntime().exec(cmd);
            proc.waitFor();
            if (proc.exitValue() == 0) return;
        }
        
        destFile.delete();
        srcFile.renameTo(destFile);
    }
    static void moveDir(File srcDir, File destDir) throws IOException, InterruptedException {
        if (!Os.isMicrosoftWindows()) {
            String cmd = "mv '"+srcDir.getAbsolutePath()+"' '"+destDir.getAbsolutePath()+"'";
            Process proc = Runtime.getRuntime().exec(cmd);
            proc.waitFor();
            if (proc.exitValue() == 0) return;
        }
        
        FileUtils.moveDirectory(srcDir, destDir);
    }
    static void copyDir(File srcDir, File destDir) throws IOException, InterruptedException {
        if (!Os.isMicrosoftWindows()) {
            String cmd = "cp -R '"+srcDir.getAbsolutePath()+"' '"+destDir.getAbsolutePath()+"'";
            Process proc = Runtime.getRuntime().exec(cmd);
            proc.waitFor();
            if (proc.exitValue() == 0) return;
        }
        
        FileUtils.copyDirectory(srcDir, destDir);
    }

    /**
     * Empty if directory exists, but is entirely empty, or only contains empty directories.
     */
    public static boolean isMementoDirExistButEmpty(String dir) {
        return isMementoDirExistButEmpty(new File(dir));
    }
    public static boolean isMementoDirExistButEmpty(File dir) {
        if (!dir.exists()) return false;
        for (File sub : dir.listFiles()) {
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

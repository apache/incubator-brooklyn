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

import brooklyn.management.ManagementContext;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.FatalConfigurationRuntimeException;
import brooklyn.util.os.Os;

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
        log.info("File-based objectStore will use directory {}", basedir);
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
        return new FileBasedStoreObjectAccessor(new File(Os.mergePaths(getBaseDir().getAbsolutePath(), path)), executor, tmpExt);
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
    public void backupContents(String parentSubPath, String backupSubPath) {
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

        if (persistMode!=null) {
            File dir = getBaseDir();
            try {
                String persistencePath = dir.getAbsolutePath();

                switch (persistMode) {
                case CLEAN:
                    if (dir.exists()) {
                        checkPersistenceDirAccessible(dir);
                        try {
                            File old = moveDirectory(dir);
                            log.info("Persist-clean using "+persistencePath+"; moved old directory to "+old.getAbsolutePath());
                        } catch (IOException e) {
                            throw new FatalConfigurationRuntimeException("Error moving old persistence directory "+dir.getAbsolutePath(), e);
                        }
                    } else {
                        log.info("Persist-clean using "+persistencePath+"; no pre-existing persisted data");
                    }
                    break;
                case REBIND:
                    checkPersistenceDirAccessible(dir);
                    checkPersistenceDirNonEmpty(dir);
                    try {
                        File backup = backupDirectory(dir);
                        log.info("Persist-rebind using "+persistencePath+"; backed up directory to "+backup.getAbsolutePath());
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
                            File backup = backupDirectory(dir);
                            log.info("Persist-auto will rebind using "+persistencePath+"; backed up directory to "+backup.getAbsolutePath());
                        } catch (IOException e) {
                            throw new FatalConfigurationRuntimeException("Error backing up persistence directory "+dir.getAbsolutePath(), e);
                        }
                    } else {
                        log.info("Persist-auto using fresh "+persistencePath+"; no pre-existing persisted data");
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
    }

    static void checkPersistenceDirAccessible(File dir) {
        if (!(dir.exists() && dir.isDirectory() && dir.canRead() && dir.canWrite())) {
            throw new FatalConfigurationRuntimeException("Invalid persistence directory " + dir + ": " +
                    (!dir.exists() ? "does not exist" :
                            (!dir.isDirectory() ? "not a directory" :
                                    (!dir.canRead() ? "not readable" :
                                            (!dir.canWrite() ? "not writable" : "unknown reason")))));
        } else {
            log.info("Directory {} has been created.", dir);
        }
    }

    protected void checkPersistenceDirNonEmpty(File persistenceDir) {
        if (!persistenceDir.exists())
            throw new FatalConfigurationRuntimeException("Invalid persistence directory "+persistenceDir+" because directory does not exist");
        if (isMementoDirExistButEmpty(persistenceDir)) {
            throw new FatalConfigurationRuntimeException("Invalid persistence directory "+persistenceDir+" because directory is empty");
        }
    }

    static File backupDirectory(File dir) throws IOException, InterruptedException {
        File parentDir = dir.getParentFile();
        String simpleName = dir.getName();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd-hhmm-ss").format(new Date());
        File backupDir = new File(parentDir, simpleName+"-"+timestamp+".bak");
        
        String cmd = "cp -R "+dir.getAbsolutePath()+" "+backupDir.getAbsolutePath();
        Process proc = Runtime.getRuntime().exec(cmd);
        proc.waitFor();
        if (proc.exitValue() != 0) {
            throw new IOException("Error backing up directory, with command `"+cmd+"` (exit value "+proc.exitValue()+")");
        }
        return backupDir;
    }

    static File moveDirectory(File dir) throws InterruptedException, IOException {
        File parentDir = dir.getParentFile();
        String simpleName = dir.getName();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd-hhmm-ss").format(new Date());
        File newDir = new File(parentDir, simpleName+"-"+timestamp+".old");
        
        String cmd = "mv  "+dir.getAbsolutePath()+" "+newDir.getAbsolutePath();
        Process proc = Runtime.getRuntime().exec(cmd);
        proc.waitFor();
        if (proc.exitValue() != 0) {
            throw new IOException("Error moving directory, with command "+cmd);
        }
        return newDir;
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
        try {
            if (d==null) return;
            String dp = d.getAbsolutePath();
            if (dp.length()<=4) {
                log.warn("Refusing instruction to delete base dir "+d+": name too short");
                return;
            }
            if (Os.home().equals(dp)) {
                log.warn("Refusing instruction to delete base dir "+d+": it's the home directory");
                return;
            }
            FileUtils.deleteDirectory(d);
        } catch (IOException e) {
            Exceptions.propagateIfFatal(e);
            log.warn("Unable to delete persistence dir "+d);
        }
    }

}

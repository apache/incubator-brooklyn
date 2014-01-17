package brooklyn.util.os;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;

import brooklyn.util.net.Urls;
import brooklyn.util.text.Strings;

public class Os {

    private static final Logger log = LoggerFactory.getLogger(Os.class);
    
    /** returns the /tmp dir, based on java.io.tmpdir but ignoring it if it's weird
     * (e.g. /var/folders/q2/363yynwx5lb_qpch1km2xvr80000gn/T/) and /tmp exists */
    public static String tmp() {
        String tmpdir = System.getProperty("java.io.tmpdir");
        if (tmpdir.contains("/var/") && new File("/tmp").exists())
            return "/tmp";
        return tmpdir;
    }

    public static String user() {
        return System.getProperty("user.name");
    }

    /** merges paths using forward slash (unix way); see {@link Urls#mergePaths(String...)} */
    public static String mergePathsUnix(String ...items) {
        return Urls.mergePaths(items);
    }

    /** merges paths using the local file separator */
    public static String mergePaths(String ...items) {
        char separatorChar = File.separatorChar;
        StringBuilder result = new StringBuilder();
        for (String item: items) {
            if (item.isEmpty()) continue;
            if (result.length() > 0 && result.charAt(result.length()-1) != separatorChar) result.append(separatorChar);
            result.append(item);
        }
        return result.toString();
    }

    /** tries to delete a directory recursively;
     * use with care - see http://stackoverflow.com/questions/8320376/why-is-files-deletedirectorycontents-deprecated-in-guava.
     * @return true if there are no errors (but the directory may still exist if a file is created there in parallel),
     * false if there are errors (without reporting the errors)
     * <p>
     * NB: this method might change, based on Guava choosing to deprecate it;
     * also the return type might be modified in future to supply more information;
     * thus it is marked beta */
    @Beta
    public static boolean tryDeleteDirectory(String dir) {
        try {
            FileUtils.deleteDirectory(new File(dir));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static class FileDeletionHook {
        public FileDeletionHook(File f, boolean recursively) {
            this.path = f;
            this.recursively = recursively;
        }
        final File path;
        final boolean recursively;
        
        public void run() throws IOException {
            if (path.exists()) {
                if (recursively && path.isDirectory()) {
                    FileUtils.deleteDirectory(path);
                } else {
                    path.delete();
                }
            }
        }
    }
    
    private static final Map<String,FileDeletionHook> deletions = new LinkedHashMap<String, Os.FileDeletionHook>();
    
    private static void addShutdownFileDeletionHook(String path, FileDeletionHook hook) {
        synchronized (deletions) {
            if (deletions.isEmpty()) {
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        synchronized (deletions) {
                            List<String> pathsToDelete = new ArrayList<String>(deletions.keySet());
                            Collections.sort(pathsToDelete, Strings.lengthComparator().reverse());
                            for (String path: pathsToDelete) {
                                try {
                                    deletions.remove(path).run();
                                } catch (Exception e) {
                                    log.warn("Unable to delete '"+path+"' on shutdown: "+e);
                                }
                            }
                        }
                    }
                });
            }
            FileDeletionHook oldHook = deletions.put(path, hook);
            if (oldHook!=null && oldHook.recursively)
                // prefer any hook which is recursive
                deletions.put(path, oldHook);
        }
    }

    /** deletes the given file or empty directory on exit
     * <p>
     * similar to {@link File#deleteOnExit()} except it is smart about trying to delete longer filenames first
     * (and the shutdown hook order does not use proprietary java hooks)
     * <p>
     * note this does not delete non-empty directories; see {@link #deleteOnExitRecursively(File)} for that */
    public static void deleteOnExit(File directoryToDeleteIfEmptyOrFile) {
        addShutdownFileDeletionHook(directoryToDeleteIfEmptyOrFile.getAbsolutePath(), new FileDeletionHook(directoryToDeleteIfEmptyOrFile, false));
    }

    /** deletes the given file or directory and, in the case of directories, any contents;
     * similar to apache commons FileUtils.cleanDirectoryOnExit but corrects a bug in that implementation
     * which causes it to fail if content is added to that directory after the hook is registered */
    public static void deleteOnExitRecursively(File directoryToCleanOrFile) {
        addShutdownFileDeletionHook(directoryToCleanOrFile.getAbsolutePath(), new FileDeletionHook(directoryToCleanOrFile, true));
    }

    /** causes empty directories from subsubdir up to and including dir to be deleted on exit;
     * useful e.g. if we create  /tmp/brooklyn-test/foo/test1/  and someone else might create
     * /tmp/brooklyn-test/foo/test2/   and we'd like the last tear-down to result in /tmp/brooklyn-test  being deleted!
     * <p> 
     * returns number of directories queued for deletion so caller can check for errors if desired;
     * if dir is not an ancestor of subsubdir this logs a warning but does not throw  */
    public static int deleteOnExitEmptyParentsUpTo(File subsubDirOrFile, File dir) {
        if (subsubDirOrFile==null || dir==null) 
            return 0;
        
        List<File> dirsToDelete = new ArrayList<File>();
        File d = subsubDirOrFile;
        do {
            dirsToDelete.add(d);
            if (d.equals(dir)) break;
            d = d.getParentFile();
        } while (d!=null);
        
        if (d==null) {
            log.warn("File "+subsubDirOrFile+" has no ancestor "+dir+": will not attempt to clean up with ancestors on exit");
            // dir is not an ancestor if subsubdir
            return 0;
        }
        
        for (File f: dirsToDelete)
            deleteOnExit(f);
        
        return dirsToDelete.size();
    }

    /** like {@link #deleteOnExitRecursively(File)} followed by {@link #deleteOnExitEmptyParentsUpTo(File, File)} */
    public static void deleteOnExitRecursivelyAndEmptyParentsUpTo(File directoryToCleanOrFile, File highestAncestorToDelete) {
        deleteOnExitRecursively(directoryToCleanOrFile);
        deleteOnExitEmptyParentsUpTo(directoryToCleanOrFile, highestAncestorToDelete);
    }


    // TODO migrate static OS-things from ResourceUtils to here!

}

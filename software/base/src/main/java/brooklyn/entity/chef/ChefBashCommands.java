package brooklyn.entity.chef;

import static brooklyn.util.ssh.BashCommands.INSTALL_CURL;
import static brooklyn.util.ssh.BashCommands.INSTALL_TAR;
import static brooklyn.util.ssh.BashCommands.INSTALL_UNZIP;
import static brooklyn.util.ssh.BashCommands.downloadToStdout;
import static brooklyn.util.ssh.BashCommands.sudo;
import brooklyn.util.ssh.BashCommands;

import com.google.common.annotations.Beta;

/** BASH commands useful for setting up Chef */
@Beta
public class ChefBashCommands {

    public static final String INSTALL_FROM_OPSCODE =
            BashCommands.chain(
                    INSTALL_CURL,
                    INSTALL_TAR,
                    INSTALL_UNZIP,
                    "( "+downloadToStdout("https://www.opscode.com/chef/install.sh") + " | " + sudo("bash")+" )");

    // TODO replaced by tasks
    
//    /** this assumes the download is an archive containing a single directory on the root which will be renamed to "cookbookName";
//     * if that directory already has the correct name cookbookName can be null,
//     * but if e.g. taking from a github tarball it will typically be of the form cookbookName-master/ 
//     * hence the renaming */
//    // TODO support installing from classpath, and using the repository (tie in with those methods)
//    public static final String downloadAndExpandCookbook(String cookbookArchiveUrl, @Nullable String cookbookName, boolean force) {
//        String dl = downloadAndExpandCookbook(cookbookArchiveUrl);
//        if (cookbookName==null) return dl;
//        XXX;
//        String privateTmpDirContainingUnpackedCookbook = "tmp-"+Strings.makeValidFilename(cookbookName)+"-"+Identifiers.makeRandomId(4);
//        String installCmd = BashCommands.chain("mkdir "+privateTmpDirContainingUnpackedCookbook, "cd "+privateTmpDirContainingUnpackedCookbook, dl, 
//                BashCommands.requireTest("`ls | wc -w` -eq 1", 
//                        "The downloaded archive must contain exactly one directory; contained"),
//        		"COOKBOOK_EXPANDED_DIR=`ls`",
//        		"mv $COOKBOOK_EXPANDED_DIR '../"+cookbookName+"'",
//        		"cd ..",
//        		"rm -rf "+privateTmpDirContainingUnpackedCookbook);
//        if (!force) return BashCommands.alternatives("ls "+cookbookName, installCmd);
//        else return BashCommands.alternatives("rm -rf "+cookbookName, installCmd);
//    }
//    
//    /** as {@link #downloadAndExpandCookbook(String, String)} with no cookbook name */
//    // TODO deprecate
//    public static final String downloadAndExpandCookbook(String cookbookArchiveUrl) {
////        curl -f -L  https://github.com/opscode-cookbooks/postgresql/archive/master.tar.gz | tar xvz
//        String ext = Files.getFileExtension(cookbookArchiveUrl);
//        if ("tar".equalsIgnoreCase(ext))
//            return downloadToStdout(cookbookArchiveUrl) + " | tar xv";
//        if ("tgz".equalsIgnoreCase(ext) || cookbookArchiveUrl.toLowerCase().endsWith(".tar.gz"))
//            return downloadToStdout(cookbookArchiveUrl) + " | tar xvz";
//        
//        String target = FilenameUtils.getName(cookbookArchiveUrl);
//        if (target==null) target = ""; else target = target.trim();
//        target += "_"+Strings.makeRandomId(4);
//        
//        if ("zip".equalsIgnoreCase(ext) || "tar.gz".equalsIgnoreCase(ext))
//            return BashCommands.chain(
//                BashCommands.commandToDownloadUrlAs(cookbookArchiveUrl, target), 
//                "unzip "+target,
//        		"rm "+target);
//
//        // TODO if it's a local dir, automatically pack it
//        throw new UnsupportedOperationException("No way to install cookbooks in format "+cookbookArchiveUrl+" (yet) -- use tgz, tar, or zip");
//    }
//
//    public static String renameDownloadedCookbook(String privateTmpDirContainingUnpackedCookbook, String cookbook, boolean force) {
//        XXX;
//        return null;
//    }
//    
}

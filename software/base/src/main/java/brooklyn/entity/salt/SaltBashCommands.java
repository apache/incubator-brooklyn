package brooklyn.entity.salt;

import static brooklyn.util.ssh.BashCommands.INSTALL_CURL;
import static brooklyn.util.ssh.BashCommands.INSTALL_TAR;
import static brooklyn.util.ssh.BashCommands.INSTALL_UNZIP;
import static brooklyn.util.ssh.BashCommands.downloadToStdout;
import static brooklyn.util.ssh.BashCommands.sudo;

import javax.annotation.Nullable;

import org.apache.commons.io.FilenameUtils;

import brooklyn.entity.chef.ChefBashCommands;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.io.Files;

/**
 * BASH commands useful for setting up SaltStack.
 *
 * @see ChefBashCommands
 */
@Beta
public class SaltBashCommands {

    // TODO inject the version number tag
    public static final String INSTALL_MASTER_USING_SALTSTACK_BOOSTRAP =
            BashCommands.chain(
                    INSTALL_CURL,
                    INSTALL_TAR,
                    INSTALL_UNZIP,
                    "( "+downloadToStdout("http://bootstrap.saltstack.org/") + " | " + sudo("sudo sh -s -- -M -N stable")+" )");

    // TODO inject the version number tag
    public static final String INSTALL_MINION_USING_SALTSTACK_BOOSTRAP =
            BashCommands.chain(
                    INSTALL_CURL,
                    INSTALL_TAR,
                    INSTALL_UNZIP,
                    "( "+downloadToStdout("http://bootstrap.saltstack.org/") + " | " + sudo("sudo sh")+" )");

    /**
     * SaltStack formulas are normally found at https://github.com/saltstack-formulas as repositories.
     * 
     * this assumes the download is an archive containing a single directory on the root which will be renamed to "cookbookName";
     * if that directory already has the correct name cookbookName can be null,
     * but if e.g. taking from a github tarball it will typically be of the form cookbookName-master/ 
     * hence the renaming.
     */
    // TODO support installing from classpath, and using the repository (tie in with those methods)
    public static final String downloadAndExpandFormula(String source, @Nullable String formulaName, boolean force) {
        String dl = downloadAndExpandFormula(source);
        if (formulaName==null) return dl;
        String tmpName = "tmp-"+Strings.makeValidFilename(formulaName)+"-"+Identifiers.makeRandomId(4);
        String installCmd = BashCommands.chain("mkdir "+tmpName, "cd "+tmpName, dl, 
                BashCommands.requireTest("`ls | wc -w` -eq 1", 
                        "The downloaded archive must contain exactly one directory; contained"),
                "FORMULA_EXPANDED_DIR=`ls`",
                "mv $FORMULA_EXPANDED_DIR '../"+formulaName+"'",
                "cd ..",
                "rm -rf "+tmpName);
        if (!force) return BashCommands.alternatives("ls "+formulaName, installCmd);
        else return BashCommands.alternatives("rm -rf "+formulaName, installCmd);
    }
    
    /** as {@link #downloadAndExpandFormula(String, String)} with no formula name */
    public static final String downloadAndExpandFormula(String source) {
//        curl -f -L  https://github.com/saltstack-formulas/nginx-formula/archive/master.tar.gz | tar xvz
        String ext = Files.getFileExtension(source);
        if ("tar".equalsIgnoreCase(ext))
            return downloadToStdout(source) + " | tar xv";
        if ("tgz".equalsIgnoreCase(ext) || source.toLowerCase().endsWith(".tar.gz"))
            return downloadToStdout(source) + " | tar xvz";
        
        String target = FilenameUtils.getName(source);
        if (target==null) target = ""; else target = target.trim();
        target += "_"+Strings.makeRandomId(4);
        
        if ("zip".equalsIgnoreCase(ext) || "tar.gz".equalsIgnoreCase(ext))
            return BashCommands.chain(
                BashCommands.commandToDownloadUrlAs(source, target), 
                "unzip "+target,
                "rm "+target);
        
        throw new UnsupportedOperationException("No way to expand "+source+" (yet)");
    }
    
}

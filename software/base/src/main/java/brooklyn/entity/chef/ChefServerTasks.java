package brooklyn.entity.chef;


import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyPair;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.crypto.SecureKeys;

import com.google.common.base.Throwables;
import com.google.common.io.Files;

public class ChefServerTasks {

    private static File chefKeyDir;
    
    private synchronized static File getExtractedKeysDir() {
        if (chefKeyDir==null) {
            chefKeyDir = Files.createTempDir();
            chefKeyDir.deleteOnExit();
        }
        return chefKeyDir;
    }
    
    /** extract key to a temp file, but one per machine, scheduled for deletion afterwards;
     * we extract the key because chef has no way to accept passphrases at present */
    synchronized static File extractKeyFile(SshMachineLocation machine) {
        File f = new File(getExtractedKeysDir(), machine.getAddress().getHostName()+".pem");
        if (f.exists()) return f;
        KeyPair data = machine.findKeyPair();
        if (data==null) return null;
        try {
            f.deleteOnExit();
            Files.write(SecureKeys.stringPem(data), f, Charset.defaultCharset());
            return f;
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
    
    public static KnifeTaskFactory<Boolean> isKnifeInstalled() {
        return new KnifeTaskFactory<Boolean>("knife install check")
                .knifeAddParameters("node list")
                .notThrowingOnCommonKnifeErrors()
                .returningIsExitCodeZero();
    }

    /** plain knife converge task - run list must be set, other arguments are optional */
    public static KnifeConvergeTaskFactory<String> knifeConvergeTask() {
        return new KnifeConvergeTaskFactory<String>("knife converge")
                .requiringZeroAndReturningStdout();
    }
    /** knife converge task configured for this run list (and sudo) */
    public static KnifeConvergeTaskFactory<String> knifeConvergeRunList(String runList) {
        return knifeConvergeTask()
                .knifeRunList(runList)
                .knifeSudo(true);
    }
    
    /** knife converge task configured for this run list on windows (ssh) */
    public static KnifeConvergeTaskFactory<String> knifeConvergeRunListWindowsSsh(String runList) {
        return knifeConvergeTask()
                .knifeRunList(runList)
                .knifeSudo(false)
                .knifeAddExtraBootstrapParameters("windows ssh");
    }
    
    /** knife converge task configured for this run list on windows (winrm) */
    public static KnifeConvergeTaskFactory<String> knifeConvergeRunListWindowsWinrm(String runList) {
        return knifeConvergeTask()
                .knifeRunList(runList)
                .knifeSudo(false)
                .knifeAddExtraBootstrapParameters("windows winrm")
                .knifePortUseKnifeDefault();
    }
    
}

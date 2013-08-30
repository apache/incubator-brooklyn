package brooklyn.entity.chef;


import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyPair;

import brooklyn.entity.Entity;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.crypto.SecureKeys;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
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

    /** @deprecated since 0.6.0 (soon after introduced) use {@link #knifeConvergeRunList(String)} or {@link #knifeConvergeTask()} with fluent API */ @Deprecated
    public static KnifeTaskFactory<String> knifeConverge(final String runList, boolean sudo) {
        return knifeConverge(Functions.constant(runList), sudo);
    }
    /** @deprecated since 0.6.0 (soon after introduced) use {@link #knifeConvergeRunList(String)} or {@link #knifeConvergeTask()} with fluent API */ @Deprecated
    public static KnifeTaskFactory<String> knifeConverge() {
        return knifeConverge(new Function<Entity,String>() {
            public String apply(Entity input) {
                return Strings.join(Preconditions.checkNotNull(input.getConfig(ChefConfig.CHEF_RUN_LIST), 
                        "%s must be supplied for %s", ChefConfig.CHEF_RUN_LIST, input),
                        ",");
            }
        }, true); 
    }
    /** @deprecated since 0.6.0 (soon after introduced) use {@link #knifeConvergeRunList(String)} or {@link #knifeConvergeTask()} with fluent API */ @Deprecated
    public static KnifeTaskFactory<String> knifeConverge(final Function<? super Entity,String> runList, final boolean sudo) {
        return knifeConverge(runList, sudo, null);
    }
    /** @deprecated since 0.6.0 (soon after introduced) use {@link #knifeConvergeRunList(String)} or {@link #knifeConvergeTask()} with fluent API */ @Deprecated
    public static KnifeTaskFactory<String> knifeConverge(final Function<? super Entity,String> runList, final boolean sudo, final String otherParameters) {
        return knifeConverge(runList, sudo, otherParameters, false);
    }
    /** @deprecated since 0.6.0 (soon after introduced) use {@link #knifeConvergeRunList(String)} or {@link #knifeConvergeTask()} with fluent API */ @Deprecated
    public static KnifeTaskFactory<String> knifeConverge(final Function<? super Entity,String> runList, final boolean sudo, final String otherParameters, final boolean runTwice) {
        return new KnifeConvergeTaskFactory<String>("knife converge")
                .knifeRunList(runList)
                .knifeSudo(sudo)
                .knifeRunTwice(runTwice)
                .knifeAddParameters(otherParameters)
                .requiringZeroAndReturningStdout();
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

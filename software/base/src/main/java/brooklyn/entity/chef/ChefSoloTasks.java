package brooklyn.entity.chef;

import java.util.Map;

import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.management.TaskFactory;
import brooklyn.util.ssh.BashCommands;

import com.google.common.annotations.Beta;

@Beta
public class ChefSoloTasks {

    public static TaskFactory<?> installChef(String chefDirectory, boolean force) {
        // TODO check on entity whether it is chef _server_
        String installCmd = cdAndRun(chefDirectory, ChefBashCommands.INSTALL_FROM_OPSCODE);
        if (!force) installCmd = BashCommands.alternatives("which chef-solo", installCmd);
        return SshEffectorTasks.ssh(installCmd).summary("install chef");
    }

    public static TaskFactory<?> installCookbooks(final String chefDirectory, final Map<String,String> cookbooksAndUrls, final boolean force) {
        return ChefTasks.installCookbooks(chefDirectory, cookbooksAndUrls, force);
    }

    public static TaskFactory<?> installCookbook(String chefDirectory, String cookbookName, String cookbookArchiveUrl, boolean force) {
        return ChefTasks.installCookbook(chefDirectory, cookbookName, cookbookArchiveUrl, force);
    }

    protected static String cdAndRun(String targetDirectory, String command) {
        return BashCommands.chain("mkdir -p "+targetDirectory,
                "cd "+targetDirectory,
                command);
    }

    public static TaskFactory<?> buildChefFile(String runDirectory, String chefDirectory, String phase, Iterable<? extends String> runList,
            Map<String, Object> optionalAttributes) {
        return ChefTasks.buildChefFile(runDirectory, chefDirectory, phase, runList, optionalAttributes);
    }

    public static TaskFactory<?> runChef(String runDir, String phase) {
        return runChef(runDir, phase, false);
    }
    /** see {@link ChefConfig#CHEF_RUN_CONVERGE_TWICE} for background on why 'twice' is available */
    public static TaskFactory<?> runChef(String runDir, String phase, Boolean twice) {
        String cmd = "sudo chef-solo -c "+phase+".rb -j "+phase+".json -ldebug";
        if (twice!=null && twice) cmd = BashCommands.alternatives(cmd, cmd);

        return SshEffectorTasks.ssh(cdAndRun(runDir, cmd)).
                summary("run chef for "+phase).requiringExitCodeZero();
    }
    
}

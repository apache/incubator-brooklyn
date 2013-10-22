package brooklyn.entity.salt;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.effector.EffectorTasks;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.management.TaskFactory;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Urls;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@Beta
public class SaltTasks {

    public static TaskFactory<?> installSaltMaster(String saltDirectory, boolean force) {
        // TODO check on entity whether it is salt _server_
        String installCmd = cdAndRun(saltDirectory, SaltBashCommands.INSTALL_MASTER_USING_SALTSTACK_BOOSTRAP);
        if (!force) installCmd = BashCommands.alternatives("which salt-master", installCmd);
        return SshEffectorTasks.ssh(installCmd).summary("install salt master");
    }

    public static TaskFactory<?> installSaltMinion(String saltDirectory, boolean force) {
        String installCmd = cdAndRun(saltDirectory, SaltBashCommands.INSTALL_MINION_USING_SALTSTACK_BOOSTRAP);
        if (!force) installCmd = BashCommands.alternatives("which salt-minion", installCmd);
        return SshEffectorTasks.ssh(installCmd).summary("install salt minion");
    }

    public static TaskFactory<?> installFormulas(final String saltDirectory, final Map<String,String> formulasAndUrls, final boolean force) {
        return Tasks.<Void>builder().name("install formulas").body(
                new Runnable() {
                    public void run() {
                        Entity e = EffectorTasks.findEntity();
                        if (formulasAndUrls==null)
                            throw new IllegalStateException("No formulas defined to install at "+e);
                        for (String formula: formulasAndUrls.keySet())
                            DynamicTasks.queue(installFormula(saltDirectory, formula, formulasAndUrls.get(formula), force));
                    }
                }).buildFactory();
    }

    public static TaskFactory<?> installFormula(String saltDirectory, String formula, String url, boolean force) {
        // TODO if it's server, try knife first
        // TODO support downloads from classpath / local server
        return SshEffectorTasks.ssh(cdAndRun(saltDirectory, SaltBashCommands.downloadAndExpandFormula(url, formula, force))).
                summary("install formula "+formula).requiringExitCodeZero();
    }

    protected static String cdAndRun(String targetDirectory, String command) {
        return BashCommands.chain("mkdir -p "+targetDirectory,
                "cd "+targetDirectory,
                command);
    }

    public static TaskFactory<?> buildSaltFile(String runDirectory, String saltDirectory, String phase, Iterable<? extends String> runList,
            Map<String, Object> optionalAttributes) {
        // TODO if it's server, try knife first
        // TODO configure add'l properties
        String phaseRb = 
                "root = File.absolute_path(File.dirname(__FILE__))\n"+
                "\n"+
                "file_cache_path root\n"+
//                "formula_path root + '/formulas'\n";
                "formula_path '"+saltDirectory+"'\n";

        Map<String,Object> phaseJsonMap = MutableMap.of();
        if (optionalAttributes!=null)
            phaseJsonMap.putAll(optionalAttributes);
        if (runList!=null)
            phaseJsonMap.put("run_list", ImmutableList.copyOf(runList));
        Gson json = new GsonBuilder().create();
        String phaseJson = json.toJson(phaseJsonMap);

        return Tasks.sequential("build salt files for "+phase,
                    SshEffectorTasks.put(Urls.mergePaths(runDirectory)+"/"+phase+".rb").contents(phaseRb).createDirectory(),
                    SshEffectorTasks.put(Urls.mergePaths(runDirectory)+"/"+phase+".json").contents(phaseJson));
    }

    public static TaskFactory<?> runSalt(String runDir, String phase) {
        // TODO salt server
        return SshEffectorTasks.ssh(cdAndRun(runDir, "sudo salt-solo -c "+phase+".rb -j "+phase+".json -ldebug")).
                summary("run salt for "+phase).requiringExitCodeZero();
    }
    
}

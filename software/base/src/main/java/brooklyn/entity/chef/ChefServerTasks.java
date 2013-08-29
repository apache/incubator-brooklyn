package brooklyn.entity.chef;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyPair;

import javax.annotation.Nullable;

import brooklyn.entity.Entity;
import brooklyn.entity.effector.EffectorTasks;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.TaskFactory;
import brooklyn.util.crypto.SecureKeys;
import brooklyn.util.internal.ssh.process.ProcessTool;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.system.ProcessTaskFactory;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.task.system.SystemTasks;
import brooklyn.util.text.StringEscapes.BashStringEscapes;
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
    private synchronized static File extractKeyFile(SshMachineLocation machine) {
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
    
    public static TaskFactory<ProcessTaskWrapper<Boolean>> isKnifeInstalled() {
        return new KnifeTaskFactory<Boolean,ProcessTaskWrapper<Boolean>>("knife install check") {
            public ProcessTaskWrapper<Boolean> newTask() {
                return knifeTaskFactory("node list").returningIsExitCodeZero().newTask();
            }
            @Override
            public ProcessTaskFactory<Boolean> tuneTaskFactory(ProcessTaskFactory<Boolean> factory) {
                // don't throw exception, just check whether knife runs with the config we have (or default installed)
                return factory;
            }
        };
    }

    public static TaskFactory<ProcessTaskWrapper<Integer>> knifeConverge(final String runList, boolean sudo) {
        return knifeConverge(Functions.constant(runList), sudo);
    }
    public static TaskFactory<ProcessTaskWrapper<Integer>> knifeConverge() {
        return knifeConverge(new Function<Entity,String>() {
            public String apply(Entity input) {
                return Strings.join(Preconditions.checkNotNull(input.getConfig(ChefConfig.CHEF_RUN_LIST), 
                        "%s must be supplied for %s", ChefConfig.CHEF_RUN_LIST, input),
                        ",");
            }
        }, true); 
    }
    public static TaskFactory<ProcessTaskWrapper<Integer>> knifeConverge(final Function<? super Entity,String> runList, final boolean sudo) {
        return knifeConverge(runList, sudo, null);
    }
    public static TaskFactory<ProcessTaskWrapper<Integer>> knifeConverge(final Function<? super Entity,String> runList, final boolean sudo, final String otherParameters) {
        return new KnifeTaskFactory<Integer,ProcessTaskWrapper<Integer>>("knife converge") {
            public ProcessTaskWrapper<Integer> newTask() {
                SshMachineLocation machine = EffectorTasks.findSshMachine();
                String host = machine.getAddress().getHostName();
                String user = Preconditions.checkNotNull(machine.getUser(), "user");
                File keyfile = extractKeyFile(machine);
                String auth;
                if (keyfile!=null) {
                    auth = "-i "+keyfile.getPath();
                } else {
                    auth = "-P "+Preconditions.checkNotNull(machine.findPassword(), "No password or private key data for "+machine);
                }
                int port = machine.getPort();
                
                ProcessTaskFactory<Integer> f = knifeTaskFactory(
                        "bootstrap "+host
                        +" -p "+port+" -x "+user+" "+auth
                        +(sudo ? " --sudo" : "") 
                        +" -r "+BashStringEscapes.wrapBash(runList.apply(entity()))
                        +(Strings.isNonEmpty(otherParameters) ? " "+otherParameters : "")
                        ).requiringExitCodeZero();
                return f.newTask();
            }
            @Override
            public String fullCommand(String knifeSubcommand) {
                String result = super.fullCommand(knifeSubcommand);
                if (entity().getConfig(ChefConfig.CHEF_RUN_CONVERGE_TWICE))
                    result = BashCommands.alternatives(result, result);
                String extraCommands = entity().getConfig(ChefConfig.KNIFE_SETUP_COMMANDS);
                if (!Strings.isEmpty(extraCommands))
                    result = BashCommands.chain(extraCommands, result);
                return result;
            }
        };
    }

    /** defines a skeleton factory for creating knife tasks, where all we have to do is override newTask,
     * calling to processTaskFactory(subcommand) */
    // see e.g. http://docs.opscode.com/knife_bootstrap.html
    public abstract static class KnifeTaskFactory<U,T extends TaskAdaptable<U>> implements TaskFactory<T> {
        public final String taskName;

        public KnifeTaskFactory() {
            this(null);
        }
        public KnifeTaskFactory(String taskName) {
            this.taskName = taskName;
        }

        @SuppressWarnings("unchecked")
        public ProcessTaskFactory<U> knifeTaskFactory(String knifeSubcommand) {
            return tuneTaskFactory( (ProcessTaskFactory<U>)  
                    SystemTasks.exec(fullCommand(knifeSubcommand)).summary(taskName)
                    .configure(ProcessTool.PROP_LOGIN_SHELL, true));
        }
        
        public ProcessTaskFactory<U> tuneTaskFactory(ProcessTaskFactory<U> factory) {
            Function<ProcessTaskWrapper<?>, Void> propagateIfKnifeConfigFileMissing = new Function<ProcessTaskWrapper<?>, Void>() {
                public Void apply(@Nullable ProcessTaskWrapper<?> input) {
                    if (input.getExitCode()!=0 && input.getStderr().indexOf("WARNING: No knife configuration file found")>=0) {
                        String myConfig = knifeConfigFileOption();
                        if (Strings.isEmpty(myConfig))
                            throw new IllegalStateException("Config file for Chef knife must be specified in "+ChefConfig.KNIFE_CONFIG_FILE+" (or valid knife default set up)");
                        else
                            throw new IllegalStateException("Error reading config file for Chef knife ("+myConfig+") -- does it exist?");
                    }
                    return null;
                }
            };
            return factory.addCompletionListener(propagateIfKnifeConfigFileMissing);
        }
        
        public String fullCommand(String knifeSubcommand) {
            return knifeExecutable()+" "+knifeSubcommand+" "+knifeConfigFileOption();
        }

        public Entity entity() {
            return EffectorTasks.findEntity();
        }
        
        public String knifeExecutable() {
            String knifeCommand = entity().getConfig(ChefConfig.KNIFE_EXECUTABLE);
            if (knifeCommand!=null) return BashStringEscapes.wrapBash(knifeCommand);
            // assume on the path, if executable not set
            return "knife";
        }
        
        public String knifeConfigFileOption() {
            String knifeConfigFile = entity().getConfig(ChefConfig.KNIFE_CONFIG_FILE);
            if (knifeConfigFile!=null) return "-c "+BashStringEscapes.wrapBash(knifeConfigFile);
            // use global config, option can be empty
            return "";
        }
    }

}

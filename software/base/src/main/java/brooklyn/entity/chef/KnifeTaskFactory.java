package brooklyn.entity.chef;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTasks;
import brooklyn.util.collections.MutableList;
import brooklyn.util.internal.ssh.process.ProcessTool;
import brooklyn.util.task.Tasks;
import brooklyn.util.task.system.ProcessTaskFactory;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.task.system.internal.SystemProcessTaskFactory;
import brooklyn.util.text.StringEscapes.BashStringEscapes;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;

/** A factory which acts like {@link ProcessTaskFactory} with special options for knife.
 * Typical usage is to {@link #addKnifeParameters(String)}s for the knife command to be run.
 * You can also {@link #add(String...)} commands as needed; these will run *before* knife,
 * unless you addKnifeCommandHere().
 * <p>
 * This impl will use sensible defaults, including {@link ConfigKey}s on the context entity,
 * for general knife config but not specific commands etc. It supports:
 * <li> {@link ChefConfig#KNIFE_EXECUTABLE}
 * <li> {@link ChefConfig#KNIFE_CONFIG_FILE}
 * <p>
 * (Other fields will typically be used by methods calling to this factory.) 
 *  */
// see e.g. http://docs.opscode.com/knife_bootstrap.html
public class KnifeTaskFactory<RET> extends SystemProcessTaskFactory<KnifeTaskFactory<RET>, RET>{
    
    private static String KNIFE_PLACEHOLDER = "<knife command goes here 1234>";
    public final String taskName;
    protected String knifeExecutable;
    protected List<String> knifeParameters = new ArrayList<String>();
    protected String knifeConfigFile;
    protected String knifeSetupCommands;
    protected Boolean throwOnCommonKnifeErrors;
    
    public KnifeTaskFactory(String taskName) {
        this.taskName = taskName;
        summary(taskName);
        // knife setup usually requires a login shell
        config.put(ProcessTool.PROP_LOGIN_SHELL, true);
    }
    
    @Override
    public List<Function<ProcessTaskWrapper<?>, Void>> getCompletionListeners() {
        MutableList<Function<ProcessTaskWrapper<?>, Void>> result = MutableList.copyOf(super.getCompletionListeners());
        if (throwOnCommonKnifeErrors != Boolean.FALSE)
            insertKnifeCompletionListenerIntoCompletionListenersList(result);
        return result.toImmutable();
    }
    
    public KnifeTaskFactory<RET> notThrowingOnCommonKnifeErrors() {
        throwOnCommonKnifeErrors = false;
        return self();
    }

    protected void insertKnifeCompletionListenerIntoCompletionListenersList(List<Function<ProcessTaskWrapper<?>, Void>> listeners) {
        // give a nice warning if chef/knife not set up correctly
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
        listeners.add(propagateIfKnifeConfigFileMissing);
    }

    
    @Override
    public ProcessTaskWrapper<RET> newTask() {
        return new SystemProcessTaskWrapper("Knife");
    }

    /** Inserts the knife command at the current place in the list.
     * Can be run multiple times. The knife command added at the end of the list
     * if this is not invoked (and it is the only command if nothing is {@link #add(String...)}ed.
     */
    public KnifeTaskFactory<RET> addKnifeCommandToScript() {
        add(KNIFE_PLACEHOLDER);
        return self();
    }
    
    @Override
    public List<String> getCommands() {
        MutableList<String> result = new MutableList<String>();
        String setupCommands = knifeSetupCommands();
        if (setupCommands != null && Strings.isNonBlank(setupCommands))
            result.add(setupCommands);
        int numKnifes = 0;
        for (String c: super.getCommands()) {
            if (c==KNIFE_PLACEHOLDER)
                result.add(buildKnifeCommand(numKnifes++));
            else
                result.add(c);
        }
        if (numKnifes==0)
            result.add(buildKnifeCommand(numKnifes++));
        return result.toImmutable();
    }
    
    /** creates the command for running knife.
     * in some cases knife may be added multiple times,
     * and in that case the parameter here tells which time it is being added, 
     * on a single run. */
    protected String buildKnifeCommand(int knifeCommandIndex) {
        MutableList<String> words = new MutableList<String>();
        words.add(knifeExecutable());
        words.addAll(initialKnifeParameters());
        words.addAll(knifeParameters());
        String x = knifeConfigFileOption();
        if (Strings.isNonBlank(x)) words.add(knifeConfigFileOption());
        return Strings.join(words, " ");
    }
    
    /** allows a way for subclasses to build up parameters at the start */
    protected List<String> initialKnifeParameters() {
        return new MutableList<String>();
    }
    
    @Nullable /** callers should allow this to be null so task can be used outside of an entity */
    protected Entity entity() {
        return BrooklynTasks.getTargetOrContextEntity(Tasks.current());
    }
    protected <T> T entityConfig(ConfigKey<T> key) {
        Entity entity = entity();
        if (entity!=null)
            return entity.getConfig(key);
        return null;
    }
    
    public KnifeTaskFactory<RET> knifeExecutable(String knifeExecutable) {
        this.knifeExecutable = knifeExecutable;
        return this;
    }
    
    protected String knifeExecutable() {
        if (knifeExecutable!=null) return knifeExecutable;
        
        String knifeExecFromConfig = entityConfig(ChefConfig.KNIFE_EXECUTABLE);
        if (knifeExecFromConfig!=null) return BashStringEscapes.wrapBash(knifeExecFromConfig);
        
        // assume on the path, if executable not set
        return "knife";
    }
    
    protected List<String> knifeParameters() {
        return knifeParameters;
    }
    
    public KnifeTaskFactory<RET> knifeAddParameters(String word1, String ...words) {
        knifeParameters.add(word1);
        for (String w: words)
            knifeParameters.add(w);
        return self();
    }

    public KnifeTaskFactory<RET> knifeConfigFile(String knifeConfigFile) {
        this.knifeConfigFile = knifeConfigFile;
        return self();
    }
    
    @Nullable
    protected String knifeConfigFileOption() {
        if (knifeConfigFile!=null) return "-c "+knifeConfigFile;

        String knifeConfigFileFromConfig = entityConfig(ChefConfig.KNIFE_CONFIG_FILE);
        if (knifeConfigFileFromConfig!=null) return "-c "+BashStringEscapes.wrapBash(knifeConfigFileFromConfig);

        // if not supplied will use global config
        return null;
    }

    public KnifeTaskFactory<RET> knifeSetupCommands(String knifeSetupCommands) {
        this.knifeSetupCommands = knifeSetupCommands;
        return self();
    }
    
    @Nullable
    protected String knifeSetupCommands() {
        if (knifeSetupCommands!=null) return knifeSetupCommands;
        
        String knifeSetupCommandsFromConfig = entityConfig(ChefConfig.KNIFE_SETUP_COMMANDS);
        if (knifeSetupCommandsFromConfig!=null) return knifeSetupCommandsFromConfig;
        
        // if not supplied will use global config
        return null;
    }
    
    @Override
    public <T2> KnifeTaskFactory<T2> returning(ScriptReturnType type) {
        return (KnifeTaskFactory<T2>) super.<T2>returning(type);
    }

    @Override
    public <RET2> KnifeTaskFactory<RET2> returning(Function<ProcessTaskWrapper<?>, RET2> resultTransformation) {
        return (KnifeTaskFactory<RET2>) super.returning(resultTransformation);
    }
    
    @Override
    public KnifeTaskFactory<Boolean> returningIsExitCodeZero() {
        return (KnifeTaskFactory<Boolean>) super.returningIsExitCodeZero();
    }
    
    @Override
    public KnifeTaskFactory<String> requiringZeroAndReturningStdout() {
        return (KnifeTaskFactory<String>) super.requiringZeroAndReturningStdout();
    }
}
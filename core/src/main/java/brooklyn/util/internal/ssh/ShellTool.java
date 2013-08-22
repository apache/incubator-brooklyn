package brooklyn.util.internal.ssh;

import static brooklyn.entity.basic.ConfigKeys.newConfigKey;

import java.io.File;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;

/** Methods for executing things in an environment (localhost process, or ssh) */
public interface ShellTool {

    // config which applies to sessions
    
    public static final ConfigKey<File> PROP_LOCAL_TEMP_DIR = newConfigKey("localTempDir", "The directory on the local machine (i.e. running brooklyn) for writing temp files", 
            new File(System.getProperty("java.io.tmpdir"), "tmpssh"));
    
    // config which applies to calls:
    
    public static final ConfigKey<Boolean> PROP_RUN_AS_ROOT = newConfigKey("runAsRoot", "When running a script, whether to run as root", Boolean.FALSE);
    
    public static final ConfigKey<OutputStream> PROP_OUT_STREAM = newConfigKey(OutputStream.class, "out", "Stream to which to capture stdout");
    public static final ConfigKey<OutputStream> PROP_ERR_STREAM = newConfigKey(OutputStream.class, "err", "Stream to which to capture stderr");
    
    public static final ConfigKey<Boolean> PROP_NO_EXTRA_OUTPUT = newConfigKey("noExtraOutput", "Suppresses any decorative output such as result code which some tool commands insert", false);
    
    public static final ConfigKey<String> PROP_SEPARATOR = newConfigKey("separator", "string to insert between caller-supplied commands being executed as commands", " ; ");
    
//    public static final ConfigKey<String> PROP_SCRIPT_DIR = newConfigKey("scriptDir", "directory where scripts should be copied", "/tmp");
//    public static final ConfigKey<String> PROP_SCRIPT_HEADER = newConfigKey("scriptHeader", "lines to insert at the start of scripts generated for caller-supplied commands for script execution", "#!/bin/bash -e\n");
//    public static final ConfigKey<String> PROP_DIRECT_HEADER = newConfigKey("directHeader", "commands to run at the target before any caller-supplied commands for direct execution", "exec bash -e");
    @SuppressWarnings("deprecation")
    public static final ConfigKey<String> PROP_SCRIPT_DIR = ShellToolConfigKeysForRemote.PROP_SCRIPT_DIR;
    @SuppressWarnings("deprecation")
    public static final ConfigKey<String> PROP_SCRIPT_HEADER = ShellToolConfigKeysForRemote.PROP_SCRIPT_HEADER;
    @SuppressWarnings("deprecation")
    public static final ConfigKey<String> PROP_DIRECT_HEADER = ShellToolConfigKeysForRemote.PROP_DIRECT_HEADER;

    
    /**
     * Executes the set of commands in a shell script. Blocks until completion.
     * <p>
     * 
     * Optional properties are the same common ones as for {@link #execCommands(Map, List, Map)} with the addition of:
     * <ul>
     * <li>{@link #PROP_RUN_AS_ROOT}
     * <li>{@link #PROP_SCRIPT_DIR}
     * </ul>
     * 
     * @return exit status of script
     */
    public int execScript(Map<String,?> props, List<String> commands, Map<String,?> env);

    /**
     * @see #execScript(Map, List, Map)
     */
    public int execScript(Map<String,?> props, List<String> commands);

    /**
     * Executes the set of commands using ssh exec.
     * 
     * This is generally more efficient than ssh shell mode (cf {@link #execScript(Map, List, Map)}), 
     * but is not suitable if you need env values which are only set on a fully-fledged shell,
     * or if you want the entire block executed with root permission.
     *
     * Common optional properties (which also apply to {@link #execScript(Map, List, Map)}) are:
     * <ul>
     * <li>{@link #PROP_OUT_STREAM}
     * <li>{@link #PROP_ERR_STREAM}
     * <li>{@link #PROP_SEPARATOR} (for some modes)
     * <li>{@link #PROP_NO_EXTRA_OUTPUT} (often there is no extra output here)
     * </ul>
     * 
     * Note that {@link #PROP_RUN_AS_ROOT} is <i>not</i> typically supported here. Prefer {@link #execScript(Map, List, Map)}).
     * 
     * @return exit status of commands
     */
    public int execCommands(Map<String,?> properties, List<String> commands, Map<String,?> env);

    /**
     * @see #execCommands(Map, List, Map)
     */
    public int execCommands(Map<String,?> properties, List<String> commands);

}

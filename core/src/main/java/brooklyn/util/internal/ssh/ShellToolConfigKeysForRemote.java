package brooklyn.util.internal.ssh;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.basic.BasicConfigKey;

import com.google.common.annotations.Beta;

/** @deprecated callers should use the statics defined in {@link ShellTool}
 * <p>
 * they have (temporarily) been pulled up here
 * to prevent classload errors. once the references in {@link ConfigKeys} are gone
 * (the ones from {@link BrooklynConfigKeys} are okay) we can delete this class and re-instate the
 * definitions in {@link ShellTool}
 * 
 * @since 0.6.0 */
@Deprecated
@Beta
public class ShellToolConfigKeysForRemote {

    public static final ConfigKey<String> PROP_SCRIPT_DIR = new BasicConfigKey<String>(String.class, "scriptDir", "directory where scripts should be copied", "/tmp");
    public static final ConfigKey<String> PROP_SCRIPT_HEADER = new BasicConfigKey<String>(String.class, "scriptHeader", "lines to insert at the start of scripts generated for caller-supplied commands for script execution", "#!/bin/bash -e\n");
    public static final ConfigKey<String> PROP_DIRECT_HEADER = new BasicConfigKey<String>(String.class, "directHeader", "commands to run at the target before any caller-supplied commands for direct execution", "exec bash -e");

}

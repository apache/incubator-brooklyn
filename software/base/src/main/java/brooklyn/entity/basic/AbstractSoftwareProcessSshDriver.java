package brooklyn.entity.basic;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynLogging;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigUtils;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.entity.basic.lifecycle.ScriptRunner;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.internal.ssh.SshTool;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * An abstract SSH implementation of the {@link AbstractSoftwareProcessDriver}.
 * 
 * This provides conveniences for clients implementing the install/customize/launch/isRunning/stop lifecycle
 * over SSH.  These conveniences include checking whether software is already installed,
 * creating/using a PID file for some operations, and reading ssh-specific config from the entity
 * to override/augment ssh flags on the session.  
 */
public abstract class AbstractSoftwareProcessSshDriver extends AbstractSoftwareProcessDriver implements ScriptRunner {

    public static final Logger log = LoggerFactory.getLogger(AbstractSoftwareProcessSshDriver.class);
    public static final Logger logSsh = LoggerFactory.getLogger(BrooklynLogging.SSH_IO);

    public static final String BROOKLYN_HOME_DIR = "/tmp/brooklyn-"+System.getProperty("user.name");
    public static final String DEFAULT_INSTALL_BASEDIR = BROOKLYN_HOME_DIR+File.separator+"installs";
    public static final String DEFAULT_RUN_BASEDIR = BROOKLYN_HOME_DIR+File.separator+"apps";
    public static final String NO_VERSION_INFO = "no-version-info";

    /** include this flag in newScript creation to prevent entity-level flags from being included;
     * any SSH-specific flags passed to newScript override flags from the entity,
     * and flags from the entity override flags on the location
     * (where there aren't conflicts, flags from all three are used however) */
    public static final String IGNORE_ENTITY_SSH_FLAGS = "ignoreEntitySshFlags"; 

    private volatile String runDir;
    private volatile String installDir;
    
    public AbstractSoftwareProcessSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    /**
     * @deprecated will be deleted in 0.5. Set default on ConfigKey in entity? Rather than overriding it here and not
     * telling the entity what value was chosen!
     */
    @Deprecated
    protected String getDefaultVersion() { return NO_VERSION_INFO; }

    /** returns location (tighten type, since we know it is an ssh machine location here) */	
    public SshMachineLocation getLocation() {
        return (SshMachineLocation) super.getLocation();
    }

    protected String getVersion() {
        return elvis(getEntity().getConfig(SoftwareProcessEntity.SUGGESTED_VERSION), getDefaultVersion());
    }

    protected String getEntityVersionLabel() {
        return getEntityVersionLabel("_");
    }
    
    protected String getEntityVersionLabel(String separator) {
        return elvis(entity.getClass().getSimpleName(),  
                entity.getClass().getName())+(!NO_VERSION_INFO.equals(getVersion()) ? separator+getVersion() : "");
    }
    
    public String getInstallDir() {
        // Cache it; evaluate lazily (and late) to ensure managementContext.config is accessible and completed its setup
        // Caching has the benefit that the driver is usable, even if the entity is unmanaged (useful in some tests!)
        if (installDir == null) {
            String installBasedir = entity.getManagementContext().getConfig().getFirst("brooklyn.dirs.install");
            if (installBasedir == null) installBasedir = DEFAULT_INSTALL_BASEDIR;
            if (installBasedir.endsWith(File.separator)) installBasedir.substring(0, installBasedir.length()-1);
            
            installDir = elvis(entity.getConfig(SoftwareProcessEntity.SUGGESTED_INSTALL_DIR),
                    installBasedir+"/"+getEntityVersionLabel("/"));
        }
        return installDir;
    }
    
    public String getRunDir() {
        if (runDir == null) {
            String runBasedir = entity.getManagementContext().getConfig().getFirst("brooklyn.dirs.run");
            if (runBasedir == null) runBasedir = DEFAULT_RUN_BASEDIR;
            if (runBasedir.endsWith(File.separator)) runBasedir.substring(0, runBasedir.length()-1);
            
            runDir = elvis(entity.getConfig(SoftwareProcessEntity.SUGGESTED_RUN_DIR), 
                    runBasedir+"/"+entity.getApplication().getId()+"/"+"entities"+"/"+
                    getEntityVersionLabel()+"_"+entity.getId());
        }
        return runDir;
    }

    public SshMachineLocation getMachine() { return getLocation(); }
    public String getHostname() { return entity.getAttribute(Attributes.HOSTNAME); }

    /** extracts the values for the main brooklyn.ssh.config.* config keys (i.e. those declared in ConfigKeys) 
     * as declared on the entity, and inserts them in a map using the unprefixed state, for ssh. */
    /* currently this is computed for each call, which may be wasteful, but it is reliable in the face of config changes. 
     * we could cache the Map.  note that we do _not_ cache (or even own) the SshTool; 
     * the SshTool is created or re-used by the SshMachineLocation making use of these properties */
    protected Map getSshFlags() {
        Map result = new LinkedHashMap();
        StringConfigMap mgmtConfig = getEntity().getManagementSupport().getManagementContext(true).getConfig();
        for (Field f: ConfigKeys.class.getFields()) {
            if ((f.getModifiers() & Modifier.STATIC)!=0) {
                if (ConfigKey.class.isAssignableFrom(f.getType())) {
                    ConfigKey c;
                    try {
                        c = (ConfigKey) f.get(null);
                        if (c.getName().startsWith(SshTool.BROOKLYN_CONFIG_KEY_PREFIX)) {
                            // have to use raw config to test whether the config is set
                            Object v = ((AbstractEntity)getEntity()).getConfigMap().getRawConfig(c);
                            if (v!=null) {
                                v = getEntity().getConfig(c);
                            } else {
                                v = mgmtConfig.getRawConfig(c);
                                if (v!=null) v = mgmtConfig.getConfig(c);
                            }
                            if (v!=null)
                                result.put(ConfigUtils.unprefixedKey(SshTool.BROOKLYN_CONFIG_KEY_PREFIX, c).getName(), v);
                        }
                    } catch (Exception e) {
                        log.warn("Error scanning SSH field "+f+": "+e);
                        Exceptions.propagateIfFatal(e);
                    }
                }
            }
        }
        return result;
    }

    public int execute(List<String> script, String summaryForLogging) {
        return execute(Maps.newLinkedHashMap(), script, summaryForLogging);
    }
    
    @Override
    public int execute(Map flags2, List<String> script, String summaryForLogging) {
        Map flags = new LinkedHashMap();
        if (!flags2.containsKey(IGNORE_ENTITY_SSH_FLAGS))
            flags.putAll(getSshFlags());
        flags.putAll(flags2);
        Map<String, String> environment = (Map<String, String>) ((flags.get("env") != null) ? flags.get("env") : getShellEnvironment());
        if (!flags.containsKey("logPrefix")) flags.put("logPrefix", ""+entity.getId()+"@"+getLocation().getName());
        return getMachine().execScript(flags, summaryForLogging, script, environment);
    }

    /**
     * The environment variables to be set when executing the commands (for install, run, check running, etc).
     */
    public Map<String, String> getShellEnvironment() {
        return Maps.newLinkedHashMap(entity.getConfig(SoftwareProcessEntity.SHELL_ENVIRONMENT, Collections.emptyMap()));
    }

    public void copyFile(File src, String destination) {
        copyFile(MutableMap.of(), src, destination);
    }
    /** @deprecated since 0.5.  destination should be a string not a File */
    public void copyFile(File src, File destination) {
    }
    
    public void copyFile(Map flags2, File src, String destination) {
        Map flags = new LinkedHashMap();
        if (!flags2.containsKey(IGNORE_ENTITY_SSH_FLAGS))
            flags.putAll(getSshFlags());
        flags.putAll(flags2);
        
        getMachine().copyTo(flags, src, destination);
    }

    protected final static String INSTALLING = "installing";
    protected final static String CUSTOMIZING = "customizing";
    protected final static String LAUNCHING = "launching";
    protected final static String CHECK_RUNNING = "check-running";
    protected final static String STOPPING = "stopping";
    protected final static String KILLING = "killing";
    protected final static String RESTARTING = "restarting";
    
    /* flags */
    
    /** specify as a flag to use a PID file, creating for 'start', and reading it for 'status', 'start';
     * value can be true, or a path to a pid file to use (either relative to RUN_DIR, or an absolute path) */
    protected final static String USE_PID_FILE = "usePidFile";
    
    public final static String PID_FILENAME = "pid.txt";

    /** sets up a script for the given phase, including default wrapper commands
     * (e.g. INSTALLING, LAUNCHING, etc)
     * <p>
     * flags supported include:
     * - usePidFile: true, or a filename, meaning to create (for launching) that pid
     * @param phase
     * @return
     */
    protected ScriptHelper newScript(String phase) {
        return newScript(Maps.newLinkedHashMap(), phase);
    }
    protected ScriptHelper newScript(Map flags, String phase) {
        ScriptHelper s = new ScriptHelper(this, phase+" "+elvis(entity,this));
        if (!truth(flags.get("nonStandardLayout"))) {
            if (INSTALLING.equals(phase)) {
                s.useMutex(getLocation(), getInstallDir(), "installing "+elvis(entity,this));
                s.header.append(
                        "export INSTALL_DIR=\""+getInstallDir()+"\"",
                        "mkdir -p $INSTALL_DIR",
                        "cd $INSTALL_DIR",
                        "test -f BROOKLYN && exit 0"
                        ).footer.append(
                        "date > $INSTALL_DIR/BROOKLYN"
                        );
            }
            if (ImmutableSet.of(CUSTOMIZING, LAUNCHING, CHECK_RUNNING, STOPPING, KILLING, RESTARTING).contains(phase)) {
                s.header.append(
                        "export RUN_DIR=\""+getRunDir()+"\"",
                        "mkdir -p $RUN_DIR",
                        "cd $RUN_DIR"
                        );
            }
        }

        if (ImmutableSet.of(CUSTOMIZING).contains(phase))
            s.skipIfBodyEmpty();
        if (ImmutableSet.of(CHECK_RUNNING, LAUNCHING, STOPPING, KILLING, RESTARTING).contains(phase))
            s.failIfBodyEmpty();
        if (ImmutableSet.of(INSTALLING, LAUNCHING).contains(phase))
            s.updateTaskAndFailOnNonZeroResultCode();

        if (truth(flags.get(USE_PID_FILE))) {
            String pidFile = (flags.get(USE_PID_FILE) instanceof CharSequence ? flags.get(USE_PID_FILE) : getRunDir()+"/"+PID_FILENAME).toString();
            if (LAUNCHING.equals(phase))
                s.footer.prepend("echo $! > "+pidFile);
            else if (CHECK_RUNNING.equals(phase))
                s.body.append(
                        "test -f "+pidFile+" || exit 1", //no pid, not running

                        //old method, for supplied service, or entity.id
                        //					"ps aux | grep ${service} | grep \$(cat ${pidFile}) > /dev/null"
                        //new way, preferred?
                        "ps -p `cat "+pidFile+"`"

                        ).requireResultCode(Predicates.or(Predicates.equalTo(0), Predicates.equalTo(1)));
            // 1 is not running

            else if (STOPPING.equals(phase))
                s.body.append(
                        "export PID=`cat "+pidFile+"`",
                        "[[ -n \"$PID\" ]] || exit 0",
                        "kill $PID",
                        "kill -9 $PID",
                        "rm "+pidFile
                        );
                
            else if (KILLING.equals(phase))
                s.body.append(
                        "export PID=`cat "+pidFile+"`",
                        "[[ -n \"$PID\" ]] || exit 0",
                        "kill -9 $PID",
                        "rm "+pidFile
                        );
                
            else if (RESTARTING.equals(phase))
                s.footer.prepend(
                        "test -f "+pidFile+" || exit 1", //no pid, not running
                        "ps -p `cat "+pidFile+"` || exit 1" //no process; can't restart,
                        );
            // 1 is not running

            else
                log.warn(USE_PID_FILE+": script option not valid for "+s.summary);
        }

        return s;
    }

    public Set<Integer> getPortsUsed() {
        Set<Integer> result = Sets.newLinkedHashSet();
        result.add(22);
        return result;
    }

}

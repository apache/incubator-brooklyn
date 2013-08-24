package brooklyn.entity.basic;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;

import java.io.File;
import java.io.StringReader;
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
import brooklyn.entity.basic.lifecycle.NaiveScriptRunner;
import brooklyn.entity.drivers.downloads.DownloadResolverManager;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.ssh.BashCommands;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
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
public abstract class AbstractSoftwareProcessSshDriver extends AbstractSoftwareProcessDriver implements NaiveScriptRunner {

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
        // FIXME this assumes we own the location!!!
        machine.addConfig(getSshFlags());
    }

    /**
     * @deprecated since 0.4. Set default on ConfigKey in entity, rather than overriding it here 
     * and not telling the entity what value was chosen!
     */
    @Deprecated
    protected String getDefaultVersion() { return NO_VERSION_INFO; }

    /** returns location (tighten type, since we know it is an ssh machine location here) */	
    public SshMachineLocation getLocation() {
        return (SshMachineLocation) super.getLocation();
    }

    public String getVersion() {
        return elvis(getEntity().getConfig(SoftwareProcess.SUGGESTED_VERSION), getDefaultVersion());
    }

    /**
     * Name to be used in the local repo, when looking for the download file.
     * If null, will 
     */
    public String getDownloadFilename() {
        return getEntity().getEntityType().getSimpleName().toLowerCase() + "-"+getVersion() + ".tar.gz";
    }

    /**
     * Suffix to use when looking up the file in the local repo.
     * Ignored if {@link getDownloadFilename()} returns non-null.
     */
    public String getDownloadFileSuffix() {
        return "tar.gz";
    }
    
    /**
     * @deprecated since 0.5.0; instead rely on {@link DownloadResolverManager} to include local-repo, such as:
     * 
     * <pre>
     * {@code
     * DownloadResolver resolver = entity.getManagementContext().getEntityDownloadsManager().resolve(this);
       List<String> urls = resolver.getTargets();
     * }
     * </pre>
     */
    protected String getEntityVersionLabel() {
        return getEntityVersionLabel("_");
    }
    
    /**
     * @deprecated since 0.5.0; instead rely on {@link DownloadResolverManager} to include local-repo
     */
    protected String getEntityVersionLabel(String separator) {
        return elvis(entity.getEntityType().getSimpleName(),  
                entity.getClass().getName())+(!NO_VERSION_INFO.equals(getVersion()) ? separator+getVersion() : "");
    }
    
    public String getInstallDir() {
        // Cache it; evaluate lazily (and late) to ensure managementContext.config is accessible and completed its setup
        // Caching has the benefit that the driver is usable, even if the entity is unmanaged (useful in some tests!)
        if (installDir == null) {
            String installBasedir = ((EntityInternal)entity).getManagementContext().getConfig().getFirst("brooklyn.dirs.install");
            if (installBasedir == null) installBasedir = DEFAULT_INSTALL_BASEDIR;
            if (installBasedir.endsWith(File.separator)) installBasedir.substring(0, installBasedir.length()-1);
            
            installDir = elvis(entity.getConfig(SoftwareProcess.SUGGESTED_INSTALL_DIR),
                    installBasedir+"/"+getEntityVersionLabel("/"));
        }
        return installDir;
    }
    
    public String getRunDir() {
        if (runDir == null) {
            String runBasedir = ((EntityInternal)entity).getManagementContext().getConfig().getFirst("brooklyn.dirs.run");
            if (runBasedir == null) runBasedir = DEFAULT_RUN_BASEDIR;
            if (runBasedir.endsWith(File.separator)) runBasedir.substring(0, runBasedir.length()-1);
            
            runDir = elvis(entity.getConfig(SoftwareProcess.SUGGESTED_RUN_DIR), 
                    runBasedir+"/"+entity.getApplication().getId()+"/"+"entities"+"/"+
                    getEntityVersionLabel()+"_"+entity.getId());
        }
        return runDir;
    }

    public SshMachineLocation getMachine() { return getLocation(); }
    public String getHostname() { return entity.getAttribute(Attributes.HOSTNAME); }
    public String getAddress() { return entity.getAttribute(Attributes.ADDRESS); }

    /** extracts the values for the main brooklyn.ssh.config.* config keys (i.e. those declared in ConfigKeys) 
     * as declared on the entity, and inserts them in a map using the unprefixed state, for ssh. */
    /* currently this is computed for each call, which may be wasteful, but it is reliable in the face of config changes. 
     * we could cache the Map.  note that we do _not_ cache (or even own) the SshTool; 
     * the SshTool is created or re-used by the SshMachineLocation making use of these properties */
    protected Map<String, Object> getSshFlags() {
        Map<String, Object> result = Maps.newLinkedHashMap();
        StringConfigMap globalConfig = ((EntityInternal)getEntity()).getManagementContext().getConfig();
        Map<ConfigKey<?>, Object> mgmtConfig = globalConfig.getAllConfig();
        Map<ConfigKey<?>, Object> entityConfig = ((EntityInternal)getEntity()).getAllConfig();
        Map<ConfigKey<?>, Object> allConfig = MutableMap.<ConfigKey<?>, Object>builder().putAll(mgmtConfig).putAll((Map)entityConfig).build();
        
        for (ConfigKey<?> key : allConfig.keySet()) {
            if (key.getName().startsWith(SshTool.BROOKLYN_CONFIG_KEY_PREFIX)) {
                // have to use raw config to test whether the config is set
                Object val = ((EntityInternal)getEntity()).getConfigMap().getRawConfig(key);
                if (val!=null) {
                    val = getEntity().getConfig(key);
                } else {
                    val = globalConfig.getRawConfig(key);
                    if (val!=null) val = globalConfig.getConfig(key);
                }
                if (val!=null) {
                    result.put(ConfigUtils.unprefixedKey(SshTool.BROOKLYN_CONFIG_KEY_PREFIX, key).getName(), val);
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
        if (!flags.containsKey("logPrefix")) flags.put("logPrefix", ""+entity.getId()+"@"+getLocation().getDisplayName());
        return getMachine().execScript(flags, summaryForLogging, script, environment);
    }

    /**
     * The environment variables to be set when executing the commands (for install, run, check running, etc).
     */
    public Map<String, String> getShellEnvironment() {
        return Maps.newLinkedHashMap(entity.getConfig(SoftwareProcess.SHELL_ENVIRONMENT, Collections.emptyMap()));
    }

    /** @deprecated since 0.5.0, should use {@link copyResource(File, String)}. */
    @Deprecated
    public void copyFile(File src, String destination) {
        copyFile(MutableMap.of(), src, destination);
    }
    /** @deprecated since 0.5.0, destination should be a string not a File */
    @Deprecated
    public void copyFile(File src, File destination) {
        getMachine().copyTo(src, destination);
    }
    /** @deprecated since 0.5.0, should use {@link copyResource(Map, String, String)}. */
    @Deprecated
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void copyFile(Map flags2, File src, String destination) {
        Map flags = new LinkedHashMap();
        if (!flags2.containsKey(IGNORE_ENTITY_SSH_FLAGS))
            flags.putAll(getSshFlags());
        flags.putAll(flags2);
        
        getMachine().copyTo(flags, src, destination);
    }

    public int copyTemplate(File template, String target) {
        return copyTemplate(template.toURI().toASCIIString(), target);
    }
    public int copyTemplate(String template, String target) {
        // prefix with runDir if relative target
        String dest = target;
        if (!new File(target).isAbsolute()) {
            dest = getRunDir() + "/" + target;
        }

        String data = processTemplate(template);
        int result = getMachine().copyTo(new StringReader(data), dest);
        if (log.isDebugEnabled())
            log.debug("Copied filtered template for {}: {} to {} - result {}", new Object[] { entity, template, dest, result });
        return result;
    }

    public void copyTemplates(Map<String, String> templates) {
        if (templates != null && templates.size() > 0) {
            log.info("Customising {} with templates: {}", entity, templates);

            for (Map.Entry<String, String> entry : templates.entrySet()) {
                String source = entry.getValue();
                String dest = entry.getKey();
                copyTemplate(source, dest);
            }
        }
    }

    public void copyResources(Map<String, String> resources) {
        if (resources != null && resources.size() > 0) {
            log.info("Customising {} with resources: {}", entity, resources);

            for (Map.Entry<String, String> entry : resources.entrySet()) {
                String source = entry.getValue();
                String dest = entry.getKey();
                copyResource(source, dest);
            }
        }
    }

    public int copyResource(File file, String target) {
        return copyResource(file.toURI().toASCIIString(), target);
    }
    public int copyResource(String resource, String target) {
        return copyResource(MutableMap.of(), resource, target);
    }
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public int copyResource(Map flags2, String resource, String target) {
        Map flags = Maps.newLinkedHashMap();
        if (!flags2.containsKey(IGNORE_ENTITY_SSH_FLAGS))
            flags.putAll(getSshFlags());
        flags.putAll(flags2);

        // prefix with runDir if relative target
        String dest = target;
        if (!new File(target).isAbsolute()) {
            dest = getRunDir() + "/" + target;
        }

        int result = -1;
        // TODO allow s3://bucket/file URIs for AWS S3 resources
        // TODO use PAX-URL style URIs for maven artifacts
        if (resource.toLowerCase().matches("^https?://.*")) {
            // try resolving http resources remotely using curl
            result = getMachine().execCommands(flags, "download-resource",
                    ImmutableList.of(
                            BashCommands.INSTALL_CURL,
                            String.format("curl -f --silent --insecure %s -o %s", resource, dest)));
        }
        // if not downloaded yet, retrieve locally and copy across
        if (result != 0) {
            result = getMachine().copyTo(flags, getResource(resource), dest);
        }
        if (log.isDebugEnabled())
            log.debug("Copied file for {}: {} to {} - result {}", new Object[] { entity, resource, dest, result });
        return result;
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

        if (ImmutableSet.of(LAUNCHING, STOPPING, KILLING, RESTARTING).contains(phase))
            s.failIfBodyEmpty();
        if (ImmutableSet.of(INSTALLING, LAUNCHING).contains(phase))
            s.updateTaskAndFailOnNonZeroResultCode();
        if (phase.equalsIgnoreCase(CHECK_RUNNING))
            s.setTransient();

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

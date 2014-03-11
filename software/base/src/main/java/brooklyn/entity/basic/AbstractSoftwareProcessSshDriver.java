package brooklyn.entity.basic;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynLogging;
import brooklyn.entity.basic.lifecycle.NaiveScriptRunner;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.entity.drivers.downloads.DownloadResolverManager;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Maybe;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.net.Urls;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.StringPredicates;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
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

    // we cache these in case the entity becomes unmanaged
    private volatile String installDir;
    private volatile String runDir;
    private volatile String expandedInstallDir;
    
    /** include this flag in newScript creation to prevent entity-level flags from being included;
     * any SSH-specific flags passed to newScript override flags from the entity,
     * and flags from the entity override flags on the location
     * (where there aren't conflicts, flags from all three are used however) */
    public static final String IGNORE_ENTITY_SSH_FLAGS = SshEffectorTasks.IGNORE_ENTITY_SSH_FLAGS.getName(); 

    public AbstractSoftwareProcessSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
        
        // FIXME this assumes we own the location, and causes warnings about configuring location after deployment;
        // better would be to wrap the ssh-execution-provider to supply these flags
        if (getSshFlags()!=null && !getSshFlags().isEmpty())
            machine.configure(getSshFlags());
        
        // ensure these are set using the routines below, not a global ConfigToAttributes.apply() 
        getInstallDir();
        getRunDir();
    }

    /** returns location (tighten type, since we know it is an ssh machine location here) */	
    public SshMachineLocation getLocation() {
        return (SshMachineLocation) super.getLocation();
    }

    public String getVersion() {
        return getEntity().getConfig(SoftwareProcess.SUGGESTED_VERSION);
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
     * DownloadResolver resolver = Entities.newDownloader(this);
     * List<String> urls = resolver.getTargets();
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
                entity.getClass().getName())+(getVersion() != null ? separator+getVersion() : "");
    }
    
    public String getInstallDir() {
        if (installDir != null) return installDir;

        String existingVal = getEntity().getAttribute(SoftwareProcess.INSTALL_DIR);
        if (Strings.isNonBlank(existingVal)) { // e.g. on rebind
            installDir = existingVal;
            return installDir;
        }
        
        // deprecated in 0.7.0
        Maybe<Object> minstallDir = getEntity().getConfigRaw(SoftwareProcess.INSTALL_DIR, true);
        if (!minstallDir.isPresent() || minstallDir.get()==null) {
            String installBasedir = ((EntityInternal)entity).getManagementContext().getConfig().getFirst("brooklyn.dirs.install");
            if (installBasedir != null) {
                log.warn("Using legacy 'brooklyn.dirs.install' setting for "+entity+"; may be removed in future versions.");
                installDir = Os.mergePathsUnix(installBasedir, getEntityVersionLabel()+"_"+entity.getId());
                installDir = Os.tidyPath(installDir);
                getEntity().setAttribute(SoftwareProcess.INSTALL_DIR, installDir);
                return installDir;
            }
        }

        installDir = Os.tidyPath(ConfigToAttributes.apply(getEntity(), SoftwareProcess.INSTALL_DIR));
        entity.setAttribute(SoftwareProcess.INSTALL_DIR, installDir);
        return installDir;
    }
    
    public String getRunDir() {
        if (runDir != null) return runDir;
        
        String existingVal = getEntity().getAttribute(SoftwareProcess.RUN_DIR);
        if (Strings.isNonBlank(existingVal)) { // e.g. on rebind
            runDir = existingVal;
            return runDir;
        }

        // deprecated in 0.7.0
        Maybe<Object> mRunDir = getEntity().getConfigRaw(SoftwareProcess.RUN_DIR, true);
        if (!mRunDir.isPresent() || mRunDir.get()==null) {
            String runBasedir = ((EntityInternal)entity).getManagementContext().getConfig().getFirst("brooklyn.dirs.run");
            if (runBasedir != null) {
                log.warn("Using legacy 'brooklyn.dirs.run' setting for "+entity+"; may be removed in future versions.");
                runDir = Os.mergePathsUnix(runBasedir, entity.getApplication().getId()+"/"+"entities"+"/"+getEntityVersionLabel()+"_"+entity.getId());
                runDir = Os.tidyPath(runDir);
                getEntity().setAttribute(SoftwareProcess.RUN_DIR, runDir);
                return runDir;
            }
        }

        runDir = Os.tidyPath(ConfigToAttributes.apply(getEntity(), SoftwareProcess.RUN_DIR));
        entity.setAttribute(SoftwareProcess.RUN_DIR, runDir);
        return runDir;
    }

    public void setExpandedInstallDir(String val) {
        checkNotNull(val, "expandedInstallDir");
        String oldVal = getEntity().getAttribute(SoftwareProcess.EXPANDED_INSTALL_DIR);
        if (Strings.isNonBlank(oldVal) && !oldVal.equals(val)) {
            log.info("Resetting expandedInstallDir (to "+val+" from "+oldVal+") for "+getEntity());
        }
        
        getEntity().setAttribute(SoftwareProcess.EXPANDED_INSTALL_DIR, val);
    }
    
    public String getExpandedInstallDir() {
        if (expandedInstallDir != null) return expandedInstallDir;
        
        String untidiedVal = ConfigToAttributes.apply(getEntity(), SoftwareProcess.EXPANDED_INSTALL_DIR);
        if (Strings.isNonBlank(untidiedVal)) {
            expandedInstallDir = Os.tidyPath(untidiedVal);
            entity.setAttribute(SoftwareProcess.INSTALL_DIR, expandedInstallDir);
            return expandedInstallDir;
        } else {
            throw new IllegalStateException("expandedInstallDir is null; most likely install was not called for "+getEntity());
        }
    }

    public SshMachineLocation getMachine() { return getLocation(); }
    public String getHostname() { return entity.getAttribute(Attributes.HOSTNAME); }
    public String getAddress() { return entity.getAttribute(Attributes.ADDRESS); }

    protected Map<String, Object> getSshFlags() {
        return SshEffectorTasks.getSshFlags(getEntity(), getMachine());
    }
    
    public int execute(List<String> script, String summaryForLogging) {
        return execute(Maps.newLinkedHashMap(), script, summaryForLogging);
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
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
     * @see SoftwareProcess#SHELL_ENVIRONMENT
     */
    public Map<String, String> getShellEnvironment() {
        return Strings.toStringMap(entity.getConfig(SoftwareProcess.SHELL_ENVIRONMENT));
    }

    /**
     * @param template File to template and copy.
     * @param target Destination on server. Will be prefixed with the entity's
     *               {@link #getRunDir() run directory} if relative.
     * @return The exit code the SSH command run.
     */
    public int copyTemplate(File template, String target) {
        return copyTemplate(template.toURI().toASCIIString(), target);
    }

    /**
     * @param template URI of file to template and copy, e.g. file://.., http://.., classpath://..
     * @param target Destination on server. Will be prefixed with the entity's
     *               {@link #getRunDir() run directory} if relative.
     * @return The exit code of the SSH command run.
     */
    public int copyTemplate(String template, String target) {
        return copyTemplate(template, target, ImmutableMap.<String, String>of());
    }

    /**
     * @param template URI of file to template and copy, e.g. file://.., http://.., classpath://..
     * @param target Destination on server. Will be prefixed with the entity's
     *               {@link #getRunDir() run directory} if relative.
     * @param extraSubstitutions Extra substitutions for the templater to use, for example
     *               "foo" -> "bar", and in a template ${foo}.
     * @return The exit code of the SSH command run.
     */
    public int copyTemplate(String template, String target, Map<String, ?> extraSubstitutions) {
        // prefix with runDir if relative target
        String dest = target;
        if (!new File(target).isAbsolute()) {
            dest = getRunDir() + "/" + target;
        }
        
        String data = processTemplate(template, extraSubstitutions);
        int result = getMachine().copyTo(new StringReader(data), dest);
        if (log.isDebugEnabled())
            log.debug("Copied filtered template for {}: {} to {} - result {}", new Object[] { entity, template, dest, result });
        return result;
    }

    /**
     * Templates all resources in the given map, then copies them to the driver's {@link #getMachine() machine}.
     * @param templates A mapping of resource URI to server destination.
     * @see #copyTemplate(String, String)
     */
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

    /**
     * Copies all resources in the given map to the driver's {@link #getMachine() machine}.
     * @param resources A mapping of resource URI to server destination.
     * @see #copyResource(String, String)
     */
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

    /**
     * @param file File to copy.
     * @param target Destination on server. Will be prefixed with the entity's
     *               {@link #getRunDir() run directory} if relative.
     * @return The exit code the SSH command run.
     */
    public int copyResource(File file, String target) {
        return copyResource(file.toURI().toASCIIString(), target);
    }

    /**
     * @param resource URI of file to copy, e.g. file://.., http://.., classpath://..
     * @param target Destination on server. Will be prefixed with the entity's
     *               {@link #getRunDir() run directory} if relative.
     * @return The exit code of the SSH command run
     */
    public int copyResource(String resource, String target) {
        return copyResource(MutableMap.of(), resource, target);
    }

    /**
     * @param sshFlags Extra flags to be used when making an SSH connection to the entity's machine.
     *                 If the map contains the key {@link #IGNORE_ENTITY_SSH_FLAGS} then only the
     *                 given flags are used. Otherwise, the given flags are combined with (and take
     *                 precendence over) the flags returned by {@link #getSshFlags()}.
     * @param source URI of file to copy, e.g. file://.., http://.., classpath://..
     * @param target Destination on server. Will be prefixed with the entity's
     *               {@link #getRunDir() run directory} if relative.
     * @return The exit code of the SSH command run
     */
    public int copyResource(Map sshFlags, String source, String target) {
        Map flags = Maps.newLinkedHashMap();
        if (!sshFlags.containsKey(IGNORE_ENTITY_SSH_FLAGS)) {
            flags.putAll(getSshFlags());
        }
        flags.putAll(sshFlags);

        // prefix with runDir if relative target
        File file = new File(target);
        String dest = file.isAbsolute() ? target : Urls.mergePaths(getRunDir(), target);

        int result = getMachine().installTo(resource, flags, source, dest);
        if (result == 0) {
            if (log.isDebugEnabled()) {
                log.debug("Copied file for {}: {} to {} - result {}", new Object[] { entity, source, dest, result });
            }
        }
        return result;
    }

    public static final String INSTALLING = "installing";
    public static final String CUSTOMIZING = "customizing";
    public static final String LAUNCHING = "launching";
    public static final String CHECK_RUNNING = "check-running";
    public static final String STOPPING = "stopping";
    public static final String KILLING = "killing";
    public static final String RESTARTING = "restarting";

    public static final String PID_FILENAME = "pid.txt";

    /* Flags */

    /**
     * Use a PID file, created by <em>launching</em>, and reading it for <em>check-running</em>,
     * <em>stopping</em> and <em>killing</em>. The value can be <em>true</em> or a path to a file to
     * use; either relative to <em>RUN_DIR</em> or an absolute path.
     */
    public static final String USE_PID_FILE = "usePidFile";

    /**
     * Define the process owner if not the same as the brooklyn user. Both <em>stopping</em> and
     * <em>killing</em> will sudo to this user before issuing the <code>kill</code> command. Only valid
     * if <em>USE_PID_FILE</em> is also set.
     */
    public static final String PROCESS_OWNER = "processOwner";

    /**
     * Marks the script as having a customised setup, to prevent the default headers and footers being
     * added to the list of commands.
     */
    public static final String NON_STANDARD_LAYOUT = "nonStandardLayout";

    /**
     * Prevents creation of the <code>$INSTALL_DIR/BROOKLYN</code> marker file after <em>installing</em>
     * phase finishes, to allow further installation phases to execute.
     */
    public static final String INSTALL_INCOMPLETE = "installIncomplete";

    /**
     * Enable shell debugging output via <code>set -x</code> prepended to the command header.
     */
    public static final String DEBUG = "debug";

    /** Permitted flags for {@link #newScript(Map, String)}. */
    public static final List<String> VALID_FLAGS =
            ImmutableList.of(USE_PID_FILE, PROCESS_OWNER, NON_STANDARD_LAYOUT, INSTALL_INCOMPLETE, DEBUG);

    /** @see #newScript(Map, String) */
    protected ScriptHelper newScript(String phase) {
        return newScript(Maps.<String, Object>newLinkedHashMap(), phase);
    }

    /**
     * Sets up a {@link ScriptHelper} to generate a script that controls the given phase
     * (<em>check-running</em>, <em>launching</em> etc.) including default header and
     * footer commands.
     * <p>
     * Supported flags:
     * <ul>
     * <li><strong>usePidFile</strong> - <em>true</em> or <em>filename</em> to save and retrieve the PID
     * <li><strong>processOwner</strong> - <em>username</em> that owns the running process
     * <li><strong>nonStandardLayout</strong> - <em>true</em> to omit all default commands
     * <li><strong>installIncomplete</strong> - <em>true</em> to prevent marking complete
     * <li><strong>debug</strong> - <em>true</em> to enable shell debug output
     * </li>
     *
     * @param flags a {@link Map} of flags to control script generation
     * @param phase the phase to create the ScriptHelper for
     *
     * @see #newScript(String)
     * @see #USE_PID_FILE
     * @see #PROCESS_OWNER
     * @see #NON_STANDARD_LAYOUT
     * @see #INSTALL_INCOMPLETE
     * @see #DEBUG
     */
    protected ScriptHelper newScript(Map<String, ?> flags, String phase) {
        if (!Iterables.all(flags.keySet(), StringPredicates.equalToAny(VALID_FLAGS))) {
            throw new IllegalArgumentException("Invalid flags passed: " + flags);
        }

        ScriptHelper s = new ScriptHelper(this, phase+" "+elvis(entity,this));
        if (!truth(flags.get(NON_STANDARD_LAYOUT))) {
            if (truth(flags.get(DEBUG))) {
                s.header.prepend("set -x");
            }
            if (INSTALLING.equals(phase)) {
                // mutexId should be global because otherwise package managers will contend with each other 
                s.useMutex(getLocation(), "installing", "installing "+elvis(entity,this));
                s.header.append(
                        "export INSTALL_DIR=\""+getInstallDir()+"\"",
                        "mkdir -p $INSTALL_DIR",
                        "cd $INSTALL_DIR",
                        "test -f BROOKLYN && exit 0"
                        );

                if (!truth(flags.get(INSTALL_INCOMPLETE))) {
                    s.footer.append("date > $INSTALL_DIR/BROOKLYN");
                }
            }
            if (ImmutableSet.of(CUSTOMIZING, LAUNCHING, CHECK_RUNNING, STOPPING, KILLING, RESTARTING).contains(phase)) {
                s.header.append(
                        "export RUN_DIR=\""+getRunDir()+"\"",
                        "mkdir -p $RUN_DIR",
                        "cd $RUN_DIR"
                        );
            }
        }

        if (ImmutableSet.of(LAUNCHING, RESTARTING).contains(phase)) {
            s.failIfBodyEmpty();
        }
        if (ImmutableSet.of(STOPPING, KILLING).contains(phase)) {
            // stopping and killing allowed to have empty body if pid file set
            if (!truth(flags.get(USE_PID_FILE)))
                s.failIfBodyEmpty();
        }
        if (ImmutableSet.of(INSTALLING, LAUNCHING).contains(phase)) {
            s.updateTaskAndFailOnNonZeroResultCode();
        }
        if (phase.equalsIgnoreCase(CHECK_RUNNING)) {
            s.setTransient();
            s.setFlag(SshTool.PROP_CONNECT_TIMEOUT, Duration.TEN_SECONDS.toMilliseconds());
            s.setFlag(SshTool.PROP_SESSION_TIMEOUT, Duration.THIRTY_SECONDS.toMilliseconds());
            s.setFlag(SshTool.PROP_SSH_TRIES, 1);
        }

        if (truth(flags.get(USE_PID_FILE))) {
            Object usePidFile = flags.get(USE_PID_FILE);
            String pidFile = (usePidFile instanceof CharSequence ? usePidFile : Os.mergePaths(getRunDir(), PID_FILENAME)).toString();
            String processOwner = (String) flags.get(PROCESS_OWNER);
            if (LAUNCHING.equals(phase)) {
                entity.setAttribute(SoftwareProcess.PID_FILE, pidFile);
                s.footer.prepend("echo $! > "+pidFile);
            } else if (CHECK_RUNNING.equals(phase)) {
                // old method, for supplied service, or entity.id
                // "ps aux | grep ${service} | grep \$(cat ${pidFile}) > /dev/null"
                // new way, preferred?
                if (processOwner != null) {
                    s.body.append(
                            BashCommands.sudoAsUser(processOwner, "test -f "+pidFile) + " || exit 1",
                            "ps -p $(" + BashCommands.sudoAsUser(processOwner, "cat "+pidFile) + ")"
                    );
                } else {
                    s.body.append(
                            "test -f "+pidFile+" || exit 1",
                            "ps -p `cat "+pidFile+"`"
                    );
                }
                // no pid, not running; 1 is not running
                s.requireResultCode(Predicates.or(Predicates.equalTo(0), Predicates.equalTo(1)));
            } else if (STOPPING.equals(phase)) {
                if (processOwner != null) {
                    s.body.append(
                            "export PID=$(" + BashCommands.sudoAsUser(processOwner, "cat "+pidFile) + ")",
                            "test -n \"$PID\" || exit 0",
                            BashCommands.sudoAsUser(processOwner, "kill $PID"),
                            BashCommands.sudoAsUser(processOwner, "kill -9 $PID"),
                            BashCommands.sudoAsUser(processOwner, "rm -f "+pidFile)
                    );
                } else {
                    s.body.append(
                            "export PID=$(cat "+pidFile+")",
                            "test -n \"$PID\" || exit 0",
                            "kill $PID",
                            "kill -9 $PID",
                            "rm -f "+pidFile
                    );
                }
            } else if (KILLING.equals(phase)) {
                if (processOwner != null) {
                    s.body.append(
                            "export PID=$(" + BashCommands.sudoAsUser(processOwner, "cat "+pidFile) + ")",
                            "test -n \"$PID\" || exit 0",
                            BashCommands.sudoAsUser(processOwner, "kill -9 $PID"),
                            BashCommands.sudoAsUser(processOwner, "rm -f "+pidFile)
                    );
                } else {
                    s.body.append(
                            "export PID=$(cat "+pidFile+")",
                            "test -n \"$PID\" || exit 0",
                            "kill -9 $PID",
                            "rm -f "+pidFile
                    );
                }
            } else if (RESTARTING.equals(phase)) {
                if (processOwner != null) {
                    s.footer.prepend(
                            BashCommands.sudoAsUser(processOwner, "test -f "+pidFile) + " || exit 1",
                            "ps -p $(" + BashCommands.sudoAsUser(processOwner, "cat "+pidFile) + ") || exit 1"
                    );
                } else {
                    s.footer.prepend(
                            "test -f "+pidFile+" || exit 1", 
                            "ps -p $(cat "+pidFile+") || exit 1" 
                    );
                }
                // no pid, not running; no process; can't restart, 1 is not running
            } else {
                log.warn(USE_PID_FILE + ": script option not valid for " + s.summary);
            }
        }

        return s;
    }

    public Set<Integer> getPortsUsed() {
        Set<Integer> result = Sets.newLinkedHashSet();
        result.add(22);
        return result;
    }

}

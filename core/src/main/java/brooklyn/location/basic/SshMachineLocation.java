package brooklyn.location.basic;

import static brooklyn.util.GroovyJavaMethods.truth;
import groovy.lang.Closure;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynLogging;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.config.ConfigUtils;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.OsDetails;
import brooklyn.location.PortRange;
import brooklyn.location.PortSupplier;
import brooklyn.location.basic.PortRanges.BasicPortRange;
import brooklyn.location.geo.HasHostGeoInfo;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.internal.ssh.SshException;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.internal.ssh.sshj.SshjTool;
import brooklyn.util.mutex.MutexSupport;
import brooklyn.util.mutex.WithMutexes;
import brooklyn.util.pool.BasicPool;
import brooklyn.util.pool.Pool;
import brooklyn.util.stream.KnownSizeInputStream;
import brooklyn.util.stream.ReaderInputStream;
import brooklyn.util.stream.StreamGobbler;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;

/**
 * Operations on a machine that is accessible via ssh.
 * <p>
 * We expose two ways of running scripts.
 * One (execCommands) passes lines to bash, that is lightweight but fragile.
 * Another (execScript) creates a script on the remote machine, more portable but heavier.
 * <p>
 * Additionally there are routines to copyTo, copyFrom; and installTo (which tries a curl, and falls back to copyTo
 * in event the source is accessible by the caller only). 
 */
public class SshMachineLocation extends AbstractLocation implements MachineLocation, PortSupplier, WithMutexes, Closeable {
    public static final Logger LOG = LoggerFactory.getLogger(SshMachineLocation.class);
    public static final Logger logSsh = LoggerFactory.getLogger(BrooklynLogging.SSH_IO);
    
    protected interface ExecRunner {
        public int exec(SshTool ssh, Map<String,?> flags, List<String> cmds, Map<String,?> env);
    }
    
    @SetFromFlag
    String user;

    @SetFromFlag
    String privateKeyData;

    @SetFromFlag(nullable = false)
    InetAddress address;

    @SetFromFlag
    transient WithMutexes mutexSupport;
    
    @SetFromFlag
    private Set<Integer> usedPorts;

    /** any property that should be passed as ssh config (connection-time) 
     *  can be prefixed with this and . and will be passed through (with the prefix removed),
     *  e.g. (SSHCONFIG_PREFIX+"."+"StrictHostKeyChecking"):"yes" 
     *  @deprecated use {@link SshTool#BROOKLYN_CONFIG_KEY_PREFIX} */
    @Deprecated
    public static final String SSHCONFIG_PREFIX = "sshconfig";
    
    public static final ConfigKey<String> SSH_HOST = ConfigKeys.SSH_CONFIG_HOST;
    public static final ConfigKey<Integer> SSH_PORT = ConfigKeys.SSH_CONFIG_PORT;
    
    public static final ConfigKey<String> SSH_EXECUTABLE = ConfigKeys.newStringConfigKey("sshExecutable", "Allows an `ssh` executable file to be specified, to be used in place of the default (programmatic) java ssh client", null);
    public static final ConfigKey<String> SCP_EXECUTABLE = ConfigKeys.newStringConfigKey("scpExecutable", "Allows an `scp` executable file to be specified, to be used in place of the default (programmatic) java ssh client", null);
    
    // TODO remove
    public static final ConfigKey<String> PASSWORD = SshTool.PROP_PASSWORD;
    public static final ConfigKey<String> PRIVATE_KEY_FILE = SshTool.PROP_PRIVATE_KEY_FILE;
    public static final ConfigKey<String> PRIVATE_KEY_DATA = SshTool.PROP_PRIVATE_KEY_DATA;
    public static final ConfigKey<String> PRIVATE_KEY_PASSPHRASE = SshTool.PROP_PRIVATE_KEY_PASSPHRASE;
    
    public static final ConfigKey<String> SCRIPT_DIR = ConfigKeys.newStringConfigKey("scriptDir", "directory where scripts should be placed and executed on the SSH target machine", null);
    public static final ConfigKey<Map<String,Object>> SSH_ENV_MAP = new MapConfigKey<Object>(Object.class, "env", "environment variables to pass to the remote SSH shell session", null);
    
    public static final ConfigKey<Boolean> ALLOCATE_PTY = SshTool.PROP_ALLOCATE_PTY;
    // TODO remove
//            new BasicConfigKey<Boolean>(Boolean.class, "allocatePTY", "whether pseudo-terminal emulation should be turned on; " +
//            "this causes stderr to be redirected to stdout, but it may be required for some commands (such as `sudo` when requiretty is set)", false);
    
    public static final ConfigKey<OutputStream> STDOUT = new BasicConfigKey<OutputStream>(OutputStream.class, "out");
    public static final ConfigKey<OutputStream> STDERR = new BasicConfigKey<OutputStream>(OutputStream.class, "err");
    public static final ConfigKey<Boolean> NO_STDOUT_LOGGING = new BasicConfigKey<Boolean>(Boolean.class, "noStdoutLogging", "whether to disable logging of stdout from SSH commands (e.g. for verbose commands)", false);
    public static final ConfigKey<Boolean> NO_STDERR_LOGGING = new BasicConfigKey<Boolean>(Boolean.class, "noStderrLogging", "whether to disable logging of stderr from SSH commands (e.g. for verbose commands)", false);
    public static final ConfigKey<String> LOG_PREFIX = ConfigKeys.newStringConfigKey("logPrefix");
    
    public static final ConfigKey<File> LOCAL_TEMP_DIR = SshTool.PROP_LOCAL_TEMP_DIR;

    /** specifies config keys where a change in the value does not require a new SshTool instance,
     * i.e. these can be specified per command on the tool */ 
    public static final Set<ConfigKey<?>> REUSABLE_SSH_PROPS = ImmutableSet.of(STDOUT, STDERR, SCRIPT_DIR);

    public static final Set<HasConfigKey<?>> ALL_SSH_CONFIG_KEYS = 
            ImmutableSet.<HasConfigKey<?>>builder().
                    addAll(ConfigUtils.getStaticKeysOnClass(SshMachineLocation.class)).
                    addAll(ConfigUtils.getStaticKeysOnClass(SshTool.class)).
                    build();
    public static final Set<String> ALL_SSH_CONFIG_KEY_NAMES =
            ImmutableSet.copyOf(Iterables.transform(ALL_SSH_CONFIG_KEYS, new Function<HasConfigKey<?>,String>() {
                @Override
                public String apply(HasConfigKey<?> input) {
                    return input.getConfigKey().getName();
                }
            }));
            
    private transient  Pool<SshTool> vanillaSshToolPool;
    
    public SshMachineLocation() {
        this(MutableMap.of());
    }

    public SshMachineLocation(Map properties) {
        super(properties);
        usedPorts = (usedPorts != null) ? Sets.newLinkedHashSet(usedPorts) : Sets.<Integer>newLinkedHashSet();
        vanillaSshToolPool = buildVanillaPool();
    }

    private BasicPool<SshTool> buildVanillaPool() {
        return BasicPool.<SshTool>builder()
                .name(name+"@"+address+
                        (hasConfig(SSH_HOST) ? "("+getConfig(SSH_HOST)+":"+getConfig(SSH_PORT)+")" : "")+
                        ":"+
                        System.identityHashCode(this))
                .supplier(new Supplier<SshTool>() {
                        @Override public SshTool get() {
                            return connectSsh(Collections.emptyMap());
                        }})
                .viabilityChecker(new Predicate<SshTool>() {
                        @Override public boolean apply(SshTool input) {
                            return input != null && input.isConnected();
                        }})
                .closer(new Function<SshTool,Void>() {
                        @Override public Void apply(SshTool input) {
                            try {
                                input.disconnect();
                            } catch (Exception e) {
                                if (logSsh.isDebugEnabled()) logSsh.debug("On machine "+SshMachineLocation.this+", ssh-disconnect failed", e);
                            }
                            return null;
                        }})
                .build();
    }

    public void configure(Map properties) {
        super.configure(properties);

        // TODO Note that check for addresss!=null is done automatically in super-constructor, in FlagUtils.checkRequiredFields
        // Yikes, dangerous code for accessing fields of sub-class in super-class' constructor! But getting away with it so far!
        
        if (mutexSupport == null) {
        	mutexSupport = new MutexSupport();
        }
        
        boolean deferConstructionChecks = (properties.containsKey("deferConstructionChecks") && TypeCoercions.coerce(properties.get("deferConstructionChecks"), Boolean.class));
        if (!deferConstructionChecks) {
	        if (properties.containsKey("username")) {
	            LOG.warn("Using deprecated ssh machine property 'username': use 'user' instead", new Throwable("source of deprecated ssh 'username' invocation"));
	            user = ""+properties.get("username");
	        }
	        
	        if (name == null) {
	        	name = (truth(user) ? user+"@" : "") + address.getHostName();
	        }
        
	        if (getHostGeoInfo() == null) {
	            Location parentLocation = getParent();
	            if ((parentLocation instanceof HasHostGeoInfo) && ((HasHostGeoInfo)parentLocation).getHostGeoInfo()!=null)
	                setHostGeoInfo( ((HasHostGeoInfo)parentLocation).getHostGeoInfo() );
	            else
	                setHostGeoInfo(HostGeoInfo.fromLocation(this));
	        }
        }
    }

    /** @deprecated temporary Beta method introduced in 0.5.0 */ 
    public void addConfig(Map<String, Object> vals) {
//        configure(vals);
        getConfigBag().putAll(vals);
    }
    
    @Override
    public void close() throws IOException {
        vanillaSshToolPool.close();
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }
    
    public InetAddress getAddress() {
        return address;
    }

    public String getUser() {
        return user;
    }
    
    public int run(String command) {
        return run(MutableMap.of(), command, MutableMap.of());
    }
    public int run(Map props, String command) {
        return run(props, command, MutableMap.of());
    }
    public int run(String command, Map env) {
        return run(MutableMap.of(), command, env);
    }
    public int run(Map props, String command, Map env) {
        return run(props, ImmutableList.of(command), env);
    }

    /**
     * @deprecated in 1.4.1, @see execCommand and execScript
     */
    public int run(List<String> commands) {
        return run(MutableMap.of(), commands, MutableMap.of());
    }
    public int run(Map props, List<String> commands) {
        return run(props, commands, MutableMap.of());
    }
    public int run(List<String> commands, Map env) {
        return run(MutableMap.of(), commands, env);
    }
    public int run(final Map props, final List<String> commands, final Map env) {
        if (commands == null || commands.isEmpty()) return 0;
        return execSsh(props, new Function<SshTool, Integer>() {
            public Integer apply(SshTool ssh) {
                return ssh.execScript(props, commands, env);
            }});
    }

    protected <T> T execSsh(Map props, Function<SshTool,T> task) {
        if (props.isEmpty() || Sets.difference(props.keySet(), REUSABLE_SSH_PROPS).isEmpty()) {
            return vanillaSshToolPool.exec(task);
        } else {
            SshTool ssh = connectSsh(props);
            try {
                return task.apply(ssh);
            } finally {
                ssh.disconnect();
            }
        }
    }

    protected SshTool connectSsh() {
        return connectSsh(ImmutableMap.of());
    }
    
    protected boolean previouslyConnected = false;
    protected SshTool connectSsh(Map props) {
        try {
            if (!truth(user)) user = System.getProperty("user.name");
            
            ConfigBag args = new ConfigBag().
                configure(SshTool.PROP_USER, user).
                // default value of host, overridden if SSH_HOST is supplied
                configure(SshTool.PROP_HOST, address.getHostName()).
                putAll(props);

            for (Map.Entry<String,Object> entry: getAllConfig(true).entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(SshTool.BROOKLYN_CONFIG_KEY_PREFIX)) {
                    key = Strings.removeFromStart(key, SshTool.BROOKLYN_CONFIG_KEY_PREFIX);
                } else if (key.startsWith(SSHCONFIG_PREFIX)) {
                    key = Strings.removeFromStart(key, SSHCONFIG_PREFIX);
                } else if (ALL_SSH_CONFIG_KEY_NAMES.contains(entry.getKey())) {
                    // key should be included, and does not need to be changed
                    
                    // TODO make this config-setting mechanism more universal
                    // currently e.g. it will not admit a tool-specific property.
                    // thinking either we know about the tool here,
                    // or we don't allow unadorned keys to be set
                    // (require use of BROOKLYN_CONFOG_KEY_PREFIX)
                } else {
                    // this key is not applicatble here; ignore it
                    continue;
                }
                args.putStringKey(key, entry.getValue());
            }
            if (LOG.isTraceEnabled()) LOG.trace("creating ssh session for "+args);
            
            // look up tool class
            String sshToolClass = args.get(SshTool.PROP_TOOL_CLASS);
            if (sshToolClass==null) sshToolClass = SshjTool.class.getName();
            SshTool ssh = (SshTool) Class.forName(sshToolClass).getConstructor(Map.class).newInstance(args.getAllConfig());
            
            if (LOG.isTraceEnabled()) LOG.trace("using ssh-tool {} (of type {}); props ", ssh, sshToolClass);
            
            Tasks.setBlockingDetails("Opening ssh connection");
            try { ssh.connect(); } finally { Tasks.setBlockingDetails(null); }
            previouslyConnected = true;
            return ssh;
        } catch (Exception e) {
            if (previouslyConnected) throw Throwables.propagate(e);
            // subsequence connection (above) most likely network failure, our remarks below won't help
            // on first connection include additional information if we can't connect, to help with debugging
            String rootCause = Throwables.getRootCause(e).getMessage();
            throw new IllegalStateException("Cannot establish ssh connection to "+user+" @ "+this+
                    (rootCause!=null && !rootCause.isEmpty() ? " ("+rootCause+")" : "")+". \n"+
                    "Ensure that passwordless and passphraseless ssh access is enabled using standard keys from ~/.ssh or " +
                    "as configured in brooklyn.properties. " +
                    "Check that the target host is accessible, " +
                    "that credentials are correct (location and permissions if using a key), " +
                    "that the SFTP subsystem is available on the remote side, " +
                    "and that there is sufficient random noise in /dev/random on both ends. " +
                    "To debug less common causes, see the original error in the trace or log, and/or enable 'net.schmizz' (sshj) logging."
                    , e);
        }
    }

    /**
     * Convenience for running commands using ssh {@literal exec} mode.
     * @deprecated in 1.4.1, @see execCommand and execScript
     */
    public int exec(List<String> commands) {
        return exec(MutableMap.of(), commands, MutableMap.of());
    }
    public int exec(Map props, List<String> commands) {
        return exec(props, commands, MutableMap.of());
    }
    public int exec(List<String> commands, Map env) {
        return exec(MutableMap.of(), commands, env);
    }
    public int exec(final Map props, final List<String> commands, final Map env) {
        Preconditions.checkNotNull(address, "host address must be specified for ssh");
        if (commands == null || commands.isEmpty()) return 0;
        return execSsh(props, new Function<SshTool, Integer>() {
            public Integer apply(SshTool ssh) {
                return ssh.execCommands(props, commands, env);
            }});
    }
        
    // TODO submitCommands and submitScript which submit objects we can subsequently poll (cf JcloudsSshMachineLocation.submitRunScript)
    
    /** executes a set of commands, directly on the target machine (no wrapping in script).
     * joined using ' ; ' by default.
     * <p>
     * Stdout and stderr will be logged automatically to brooklyn.SSH logger, unless the 
     * flags 'noStdoutLogging' and 'noStderrLogging' are set. To set a logging prefix, use
     * the flag 'logPrefix'.
     * <p>
     * Currently runs the commands in an interactive/login shell
     * by passing each as a line to bash. To terminate early, use:
     * <pre>
     * foo || exit 1
     * </pre>
     * It may be desirable instead, in some situations, to wrap as:
     * <pre>
     * { line1 ; } && { line2 ; } ... 
     * </pre>
     * and run as a single command (possibly not as an interacitve/login
     * shell) causing the script to exit on the first command which fails.
     * <p>
     * Currently this has to be done by the caller.
     * (If desired we can add a flag {@code exitIfAnyNonZero} to support this mode,
     * and/or {@code commandPrepend} and {@code commandAppend} similar to 
     * (currently supported in SshjTool) {@code separator}.) 
     */
    public int execCommands(String summaryForLogging, List<String> commands) {
        return execCommands(MutableMap.<String,Object>of(), summaryForLogging, commands, MutableMap.<String,Object>of());
    }
    public int execCommands(Map<String,?> props, String summaryForLogging, List<String> commands) {
        return execCommands(props, summaryForLogging, commands, MutableMap.<String,Object>of());
    }
    public int execCommands(String summaryForLogging, List<String> commands, Map<String,?> env) {
        return execCommands(MutableMap.<String,Object>of(), summaryForLogging, commands, env);
    }
    public int execCommands(Map<String,?> props, String summaryForLogging, List<String> commands, Map<String,?> env) {
        return execWithLogging(props, summaryForLogging, commands, env, new ExecRunner() {
                @Override public int exec(SshTool ssh, Map<String,?> flags, List<String> cmds, Map<String,?> env) {
                    return ssh.execCommands(flags, cmds, env);
                }});
    }

    /** executes a set of commands, wrapped as a script sent to the remote machine.
     * <p>
     * Stdout and stderr will be logged automatically to brooklyn.SSH logger, unless the 
     * flags 'noStdoutLogging' and 'noStderrLogging' are set. To set a logging prefix, use
     * the flag 'logPrefix'.
     */
    public int execScript(String summaryForLogging, List<String> commands) {
        return execScript(MutableMap.<String,Object>of(), summaryForLogging, commands, MutableMap.<String,Object>of());
    }
    public int execScript(Map<String,?> props, String summaryForLogging, List<String> commands) {
        return execScript(props, summaryForLogging, commands, MutableMap.<String,Object>of());
    }
    public int execScript(String summaryForLogging, List<String> commands, Map<String,?> env) {
        return execScript(MutableMap.<String,Object>of(), summaryForLogging, commands, env);
    }
    public int execScript(Map<String,?> props, String summaryForLogging, List<String> commands, Map<String,?> env) {
        return execWithLogging(props, summaryForLogging, commands, env, new ExecRunner() {
                @Override public int exec(SshTool ssh, Map<String, ?> flags, List<String> cmds, Map<String, ?> env) {
                    return ssh.execScript(flags, cmds, env);
                }});
    }

    protected int execWithLogging(Map<String,?> props, String summaryForLogging, List<String> commands, Map env, final Closure<Integer> execCommand) {
        return execWithLogging(props, summaryForLogging, commands, env, new ExecRunner() {
                @Override public int exec(SshTool ssh, Map<String, ?> flags, List<String> cmds, Map<String, ?> env) {
                    return execCommand.call(ssh, flags, cmds, env);
                }});
    }
    
    @SuppressWarnings("resource")
    protected int execWithLogging(Map<String,?> props, final String summaryForLogging, final List<String> commands, final Map<String,?> env, final ExecRunner execCommand) {
        if (logSsh.isDebugEnabled()) logSsh.debug("{} on machine {}: {}", new Object[] {summaryForLogging, this, commands});
        
        Preconditions.checkNotNull(address, "host address must be specified for ssh");
        if (commands.isEmpty()) {
            logSsh.debug("{} on machine {} ending: no commands to run", summaryForLogging, this);
            return 0;
        }
        
        final ConfigBag execFlags = new ConfigBag().putAll(props);
        // some props get overridden in execFlags, so remove them from the ssh flags
        final ConfigBag sshFlags = new ConfigBag().putAll(props).removeAll(LOG_PREFIX, STDOUT, STDERR);
        
        PipedOutputStream outO = null;
        PipedOutputStream outE = null;
        StreamGobbler gO=null, gE=null;
        try {
        	String logPrefix = execFlags.get(LOG_PREFIX);
        	if (logPrefix == null) {
        		String hostname = getAddress().getHostName();
        		Integer port = execFlags.peek(SshTool.PROP_PORT); 
        		if (port == null) port = getConfig(ConfigUtils.prefixedKey(SshTool.BROOKLYN_CONFIG_KEY_PREFIX, SshTool.PROP_PORT));
        		if (port == null) port = getConfig(ConfigUtils.prefixedKey(SSHCONFIG_PREFIX, SshTool.PROP_PORT));
        		logPrefix = (user != null ? user+"@" : "") + hostname + (port != null ? ":"+port : "");
        	}
        	
            if (!execFlags.get(NO_STDOUT_LOGGING)) {
                PipedInputStream insO = new PipedInputStream();
                outO = new PipedOutputStream(insO);
            
                String stdoutLogPrefix = "["+(logPrefix != null ? logPrefix+":stdout" : "stdout")+"] ";
                gO = new StreamGobbler(insO, execFlags.get(STDOUT), logSsh).setLogPrefix(stdoutLogPrefix);
                gO.start();
                
                execFlags.put(STDOUT, outO);
            }
            
            if (!execFlags.get(NO_STDERR_LOGGING)) {
                PipedInputStream insE = new PipedInputStream();
                outE = new PipedOutputStream(insE);
            
                String stderrLogPrefix = "["+(logPrefix != null ? logPrefix+":stderr" : "stderr")+"] ";
                gE = new StreamGobbler(insE, execFlags.get(STDERR), logSsh).setLogPrefix(stderrLogPrefix);
                gE.start();
                
                execFlags.put(STDERR, outE);
            }
            
            Tasks.setBlockingDetails("SSH executing, "+summaryForLogging);
            try {
                return execSsh(MutableMap.copyOf(sshFlags.getAllConfig()), new Function<SshTool, Integer>() {
                    public Integer apply(SshTool ssh) {
                        int result = execCommand.exec(ssh, MutableMap.copyOf(execFlags.getAllConfig()), commands, env);
                        if (logSsh.isDebugEnabled()) logSsh.debug("{} on machine {} completed: return status {}", new Object[] {summaryForLogging, this, result});
                        return result;
                    }});

            } finally {
                Tasks.setBlockingDetails(null);
            }
            
        } catch (IOException e) {
            if (logSsh.isDebugEnabled()) logSsh.debug("{} on machine {} failed: {}", new Object[] {summaryForLogging, this, e});
            throw Throwables.propagate(e);
        } finally {
            // Must close the pipedOutStreams, otherwise input will never read -1 so StreamGobbler thread would never die
            if (outO!=null) try { outO.flush(); } catch (IOException e) {}
            if (outE!=null) try { outE.flush(); } catch (IOException e) {}
            Closeables.closeQuietly(outO);
            Closeables.closeQuietly(outE);
            
            try {
                if (gE!=null) { gE.join(); }
                if (gO!=null) { gO.join(); }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Throwables.propagate(e);
            }
        }

    }

    public int copyTo(File src, File destination) {
        return copyTo(MutableMap.<String,Object>of(), src, destination);
    }
    public int copyTo(Map<String,?> props, File src, File destination) {
        return copyTo(props, src, destination.getPath());
    }

    public int copyTo(File src, String destination) {
        return copyTo(MutableMap.<String,Object>of(), src, destination);
    }
    public int copyTo(Map<String,?> props, File src, String destination) {
        Preconditions.checkNotNull(address, "Host address must be specified for scp");
        Preconditions.checkArgument(src.exists(), "File %s must exist for scp", src.getPath());
        try {
            return copyTo(props, new FileInputStream(src), src.length(), destination);
        } catch (FileNotFoundException e) {
            throw Throwables.propagate(e);
        }
    }
    public int copyTo(Reader src, String destination) {
        return copyTo(MutableMap.<String,Object>of(), src, destination);
    }
	public int copyTo(Map<String,?> props, Reader src, String destination) {
		return copyTo(props, new ReaderInputStream(src), destination);
	}
    public int copyTo(InputStream src, String destination) {
        return copyTo(MutableMap.<String,Object>of(), src, destination);
    }
    public int copyTo(InputStream src, long filesize, String destination) {
        return copyTo(MutableMap.<String,Object>of(), src, filesize, destination);
    }
    // FIXME the return code is not a reliable indicator of success or failure
    public int copyTo(final Map<String,?> props, final InputStream src, final long filesize, final String destination) {
        if (filesize == -1) {
            return copyTo(props, src, destination);
        } else {
            return execSsh(props, new Function<SshTool,Integer>() {
                public Integer apply(SshTool ssh) {
                    return ssh.copyToServer(props, new KnownSizeInputStream(src, filesize), destination);
//                    return ssh.createFile(props, destination, src, filesize);
                }});
        }
    }
    // FIXME the return code is not a reliable indicator of success or failure
    // Closes input stream before returning
    public int copyTo(final Map<String,?> props, final InputStream src, final String destination) {
        return execSsh(props, new Function<SshTool,Integer>() {
            public Integer apply(SshTool ssh) {
                return ssh.copyToServer(props, src, destination);
            }});
    }

    // FIXME the return code is not a reliable indicator of success or failure
    public int copyFrom(String remote, String local) {
        return copyFrom(MutableMap.<String,Object>of(), remote, local);
    }
    public int copyFrom(final Map<String,?> props, final String remote, final String local) {
        return execSsh(props, new Function<SshTool,Integer>() {
            public Integer apply(SshTool ssh) {
                return ssh.copyFromServer(props, remote, new File(local));
            }});
    }

    /** installs the given URL at the indicated destination.
     * attempts to curl the sourceUrl on the remote machine,
     * then if that fails, loads locally (from classpath or file) and transfers.
     * <p>
     * accepts either a path (terminated with /) or filename for the destination. 
     **/
    public int installTo(ResourceUtils loader, String url, String destination) {
        if (destination.endsWith("/")) {
            String destName = url;
            destName = destName.contains("?") ? destName.substring(0, destName.indexOf("?")) : destName;
            destName = destName.substring(destName.lastIndexOf('/')+1);
            destination = destination + destName;            
        }
        LOG.debug("installing {} to {} on {}, attempting remote curl", new Object[] {url, destination, this});

        try {
            PipedInputStream insO = new PipedInputStream(); OutputStream outO = new PipedOutputStream(insO);
            PipedInputStream insE = new PipedInputStream(); OutputStream outE = new PipedOutputStream(insE);
            new StreamGobbler(insO, null, LOG).setLogPrefix("[curl @ "+address+":stdout] ").start();
            new StreamGobbler(insE, null, LOG).setLogPrefix("[curl @ "+address+":stdout] ").start();
            int result = exec(MutableMap.of("out", outO, "err", outE),
                    ImmutableList.of("curl "+url+" -L --silent --insecure --show-error --fail --connect-timeout 60 --max-time 600 --retry 5 -o "+destination));
            
            if (result!=0 && loader!=null) {
                LOG.debug("installing {} to {} on {}, curl failed, attempting local fetch and copy", new Object[] {url, destination, this});
                result = copyTo(loader.getResourceFromUrl(url), destination);
            }
            if (result==0)
                LOG.debug("installing {} complete; {} on {}", new Object[] {url, destination, this});
            else
                LOG.warn("installing {} failed; {} on {}: {}", new Object[] {url, destination, this, result});
            return result;
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
    
    @Override
    public String toString() {
        return "SshMachineLocation["+name+":"+address+"]";
    }

    @Override
    public String toVerboseString() {
        return Objects.toStringHelper(this).omitNullValues()
                .add("id", getId()).add("name", getDisplayName())
                .add("user", getUser()).add("address", getAddress()).add("port", getConfig(SSH_PORT))
                .add("parentLocation", getParent())
                .toString();
    }

    /**
     * @see #obtainPort(PortRange)
     * @see BasicPortRange#ANY_HIGH_PORT
     */
    public boolean obtainSpecificPort(int portNumber) {
	    // TODO Does not yet check if the port really is free on this machine
        if (usedPorts.contains(portNumber)) {
            return false;
        } else {
            usedPorts.add(portNumber);
            return true;
        }
    }

    public int obtainPort(PortRange range) {
        for (int p: range)
            if (obtainSpecificPort(p)) return p;
         if (LOG.isDebugEnabled()) LOG.debug("unable to find port in {} on {}; returning -1", range, this);
         return -1;
    }

    public void releasePort(int portNumber) {
        usedPorts.remove((Object) portNumber);
    }

    public boolean isSshable() {
        String cmd = "date";
        try {
            int result = run(cmd);
            if (result == 0) {
                return true;
            } else {
                if (LOG.isDebugEnabled()) LOG.debug("Not reachable: {}, executing `{}`, exit code {}", new Object[] {this, cmd, result});
                return false;
            }
        } catch (SshException e) {
            if (LOG.isDebugEnabled()) LOG.debug("Exception checking if "+this+" is reachable; assuming not", e);
            return false;
        } catch (IllegalStateException e) {
            if (LOG.isDebugEnabled()) LOG.debug("Exception checking if "+this+" is reachable; assuming not", e);
            return false;
        } catch (RuntimeException e) {
            if (Exceptions.getFirstThrowableOfType(e, IOException.class) != null) {
                if (LOG.isDebugEnabled()) LOG.debug("Exception checking if "+this+" is reachable; assuming not", e);
                return false;
            } else {
                throw e;
            }
        }
    }
    
    @Override
    public OsDetails getOsDetails() {
        // TODO ssh and find out what we need to know, or use jclouds...
        return BasicOsDetails.Factory.ANONYMOUS_LINUX;
    }

    protected WithMutexes newMutexSupport() { return new MutexSupport(); }
    
    @Override
    public void acquireMutex(String mutexId, String description) throws InterruptedException {
        mutexSupport.acquireMutex(mutexId, description);
    }

    @Override
    public boolean tryAcquireMutex(String mutexId, String description) {
        return mutexSupport.tryAcquireMutex(mutexId, description);
    }

    @Override
    public void releaseMutex(String mutexId) {
        mutexSupport.releaseMutex(mutexId);
    }

    @Override
    public boolean hasMutex(String mutexId) {
        return mutexSupport.hasMutex(mutexId);
    }

    //We want want the SshMachineLocation to be serializable and therefor the pool needs to be dealt with correctly.
    //In this case we are not serializing the pool (we made the field transient) and create a new pool when deserialized.
    //This fix is currently needed for experiments, but isn't used in normal Brooklyn usage.
    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        vanillaSshToolPool = buildVanillaPool();
    }

}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
import java.io.StringReader;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynLogging;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.config.ConfigUtils;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.location.MachineDetails;
import brooklyn.location.MachineLocation;
import brooklyn.location.OsDetails;
import brooklyn.location.PortRange;
import brooklyn.location.PortSupplier;
import brooklyn.management.Task;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.crypto.SecureKeys;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.file.ArchiveUtils;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.KeyTransformingLoadingCache.KeyTransformingSameTypeLoadingCache;
import brooklyn.util.internal.ssh.ShellTool;
import brooklyn.util.internal.ssh.SshException;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.internal.ssh.sshj.SshjTool;
import brooklyn.util.mutex.MutexSupport;
import brooklyn.util.mutex.WithMutexes;
import brooklyn.util.net.Urls;
import brooklyn.util.pool.BasicPool;
import brooklyn.util.pool.Pool;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.stream.KnownSizeInputStream;
import brooklyn.util.stream.ReaderInputStream;
import brooklyn.util.stream.StreamGobbler;
import brooklyn.util.task.BasicTask;
import brooklyn.util.task.ScheduledTask;
import brooklyn.util.task.Tasks;
import brooklyn.util.task.system.internal.ExecWithLoggingHelpers;
import brooklyn.util.task.system.internal.ExecWithLoggingHelpers.ExecRunner;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;

/**
 * Operations on a machine that is accessible via ssh.
 * <p>
 * We expose two ways of running scripts.
 * The execCommands method passes lines to bash and is lightweight but fragile.
 * The execScript method creates a script on the remote machine. It is portable but heavier.
 * <p>
 * Additionally there are routines to copyTo, copyFrom; and installTo (which tries a curl, and falls back to copyTo
 * in event the source is accessible by the caller only).
 */
public class SshMachineLocation extends AbstractLocation implements MachineLocation, PortSupplier, WithMutexes, Closeable {

    /** @deprecated since 0.7.0 shouldn't be public */
    public static final Logger LOG = LoggerFactory.getLogger(SshMachineLocation.class);
    /** @deprecated since 0.7.0 shouldn't be public */
    public static final Logger logSsh = LoggerFactory.getLogger(BrooklynLogging.SSH_IO);

    public static final ConfigKey<Duration> SSH_CACHE_EXPIRY_DURATION = ConfigKeys.newConfigKey(Duration.class,
            "sshCacheExpiryDuration", "Expiry time for unused cached ssh connections", Duration.FIVE_MINUTES);

    @SetFromFlag
    protected String user;

    @SetFromFlag(nullable = false)
    protected InetAddress address;

    @SetFromFlag
    protected transient WithMutexes mutexSupport;

    @SetFromFlag
    private Set<Integer> usedPorts;

    private volatile MachineDetails machineDetails;
    private final Object machineDetailsLock = new Object();

    public static final ConfigKey<String> SSH_HOST = BrooklynConfigKeys.SSH_CONFIG_HOST;
    public static final ConfigKey<Integer> SSH_PORT = BrooklynConfigKeys.SSH_CONFIG_PORT;

    public static final ConfigKey<String> SSH_EXECUTABLE = ConfigKeys.newStringConfigKey("sshExecutable",
            "Allows an `ssh` executable file to be specified, to be used in place of the default (programmatic) java ssh client");
    public static final ConfigKey<String> SCP_EXECUTABLE = ConfigKeys.newStringConfigKey("scpExecutable",
            "Allows an `scp` executable file to be specified, to be used in place of the default (programmatic) java ssh client");

    // TODO remove
    public static final ConfigKey<String> PASSWORD = SshTool.PROP_PASSWORD;
    public static final ConfigKey<String> PRIVATE_KEY_FILE = SshTool.PROP_PRIVATE_KEY_FILE;
    public static final ConfigKey<String> PRIVATE_KEY_DATA = SshTool.PROP_PRIVATE_KEY_DATA;
    public static final ConfigKey<String> PRIVATE_KEY_PASSPHRASE = SshTool.PROP_PRIVATE_KEY_PASSPHRASE;

    public static final ConfigKey<String> SCRIPT_DIR = ConfigKeys.newStringConfigKey(
            "scriptDir", "directory where scripts should be placed and executed on the SSH target machine");
    public static final ConfigKey<Map<String,Object>> SSH_ENV_MAP = new MapConfigKey<Object>(
            Object.class, "env", "environment variables to pass to the remote SSH shell session");

    public static final ConfigKey<Boolean> ALLOCATE_PTY = SshTool.PROP_ALLOCATE_PTY;

    public static final ConfigKey<OutputStream> STDOUT = new BasicConfigKey<OutputStream>(OutputStream.class, "out");
    public static final ConfigKey<OutputStream> STDERR = new BasicConfigKey<OutputStream>(OutputStream.class, "err");
    public static final ConfigKey<Boolean> NO_STDOUT_LOGGING = ConfigKeys.newBooleanConfigKey(
            "noStdoutLogging", "whether to disable logging of stdout from SSH commands (e.g. for verbose commands)", false);
    public static final ConfigKey<Boolean> NO_STDERR_LOGGING = ConfigKeys.newBooleanConfigKey(
            "noStderrLogging", "whether to disable logging of stderr from SSH commands (e.g. for verbose commands)", false);
    public static final ConfigKey<String> LOG_PREFIX = ConfigKeys.newStringConfigKey("logPrefix");

    public static final ConfigKey<String> LOCAL_TEMP_DIR = SshTool.PROP_LOCAL_TEMP_DIR;

    public static final ConfigKey<Boolean> CLOSE_CONNECTION = ConfigKeys.newBooleanConfigKey("close", "Close the SSH connection after use", false);
    public static final ConfigKey<String> UNIQUE_ID = ConfigKeys.newStringConfigKey("unique", "Unique ID for the SSH connection");

    /**
     * Specifies config keys where a change in the value does not require a new SshTool instance,
     * i.e. they can be specified per command on the tool
     */
    // TODO: Fully specify.
    public static final Set<ConfigKey<?>> REUSABLE_SSH_PROPS = ImmutableSet.of(
            STDOUT, STDERR, SCRIPT_DIR, CLOSE_CONNECTION,
            SshTool.PROP_SCRIPT_HEADER, SshTool.PROP_PERMISSIONS, SshTool.PROP_LAST_MODIFICATION_DATE,
            SshTool.PROP_LAST_ACCESS_DATE, SshTool.PROP_OWNER_UID, SshTool.PROP_SSH_RETRY_DELAY);

    public static final Set<HasConfigKey<?>> ALL_SSH_CONFIG_KEYS =
            ImmutableSet.<HasConfigKey<?>>builder()
                    .addAll(ConfigUtils.getStaticKeysOnClass(SshMachineLocation.class))
                    .addAll(ConfigUtils.getStaticKeysOnClass(SshTool.class))
                    .build();

    public static final Set<String> ALL_SSH_CONFIG_KEY_NAMES =
            ImmutableSet.copyOf(Iterables.transform(ALL_SSH_CONFIG_KEYS, new Function<HasConfigKey<?>,String>() {
                @Override
                public String apply(HasConfigKey<?> input) {
                    return input.getConfigKey().getName();
                }
            }));

    private Task<?> cleanupTask;
    private transient LoadingCache<Map<String, ?>, Pool<SshTool>> sshPoolCache;

    public SshMachineLocation() {
        this(MutableMap.of());
    }

    public SshMachineLocation(Map properties) {
        super(properties);
        usedPorts = (usedPorts != null) ? Sets.newLinkedHashSet(usedPorts) : Sets.<Integer>newLinkedHashSet();
    }

    private LoadingCache<Map<String, ?>, Pool<SshTool>> buildSshToolPoolCacheLoader() {
        // TODO: Appropriate numbers for maximum size and expire after access
        // At the moment every SshMachineLocation instance creates its own pool.
        // It might make more sense to create one pool and inject it into all SshMachineLocations.
        Duration expiryDuration = getConfig(SSH_CACHE_EXPIRY_DURATION);
        
        LoadingCache<Map<String, ?>, Pool<SshTool>> delegate = CacheBuilder.newBuilder()
                .maximumSize(10)
                .expireAfterAccess(expiryDuration.toMilliseconds(), TimeUnit.MILLISECONDS)
                .recordStats()
                .removalListener(new RemovalListener<Map<String, ?>, Pool<SshTool>>() {
                    // TODO: Does it matter that this is synchronous? - Can closing pools cause long delays?
                    @Override
                    public void onRemoval(RemovalNotification<Map<String, ?>, Pool<SshTool>> notification) {
                        Pool<SshTool> removed = notification.getValue();
                        if (removed == null) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Pool evicted from SshTool cache is null so we can't call pool.close(). " +
                                        "It's probably already been garbage collected. Eviction cause: {} ",
                                        notification.getCause().name());
                            }
                        } else {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} evicted from SshTool cache. Eviction cause: {}",
                                        removed, notification.getCause().name());
                            }
                            try {
                                removed.close();
                            } catch (IOException e) {
                                if (LOG.isDebugEnabled()) {
                                    LOG.debug("Exception closing "+removed, e);
                                }
                            }
                        }
                    }
                })
                .build(new CacheLoader<Map<String, ?>, Pool<SshTool>>() {
                    public Pool<SshTool> load(Map<String, ?> properties) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} building ssh pool for {} with properties: {}",
                                    new Object[] {this, getSshHostAndPort(), properties});
                        }
                        return buildPool(properties);
                    }
                });

        final Set<String> reusableSshProperties = ImmutableSet.copyOf(
                Iterables.transform(REUSABLE_SSH_PROPS, new Function<ConfigKey<?>, String>() {
                    @Override public String apply(ConfigKey<?> input) {
                        return input.getName();
                    }
                }));
        // Groovy-eclipse compiler refused to compile `KeyTransformingSameTypeLoadingCache.from(...)`
        return new KeyTransformingSameTypeLoadingCache<Map<String, ?>, Pool<SshTool>>(
                delegate,
                new Function<Map<String, ?>, Map<String, ?>>() {
                    @Override
                    public Map<String, ?> apply(@Nullable Map<String, ?> input) {
                        Map<String, Object> copy = new HashMap<String, Object>(input);
                        copy.keySet().removeAll(reusableSshProperties);
                        return copy;
                    }
                });
    }

    private BasicPool<SshTool> buildPool(final Map<String, ?> properties) {
        return BasicPool.<SshTool>builder()
                .name(getDisplayName()+"@"+address+
                        (hasConfig(SSH_HOST, true) ? "("+getConfig(SSH_HOST)+":"+getConfig(SSH_PORT)+")" : "")+
                        ":"+
                        System.identityHashCode(this))
                .supplier(new Supplier<SshTool>() {
                        @Override public SshTool get() {
                            return connectSsh(properties);
                        }})
                .viabilityChecker(new Predicate<SshTool>() {
                        @Override public boolean apply(SshTool input) {
                            return input != null && input.isConnected();
                        }})
                .closer(new Function<SshTool,Void>() {
                        @Override public Void apply(SshTool input) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} closing pool for {}", this, input);
                            }
                            try {
                                input.disconnect();
                            } catch (Exception e) {
                                if (logSsh.isDebugEnabled()) logSsh.debug("On machine "+SshMachineLocation.this+", ssh-disconnect failed", e);
                            }
                            return null;
                        }})
                .build();
    }

    @Override
    public void configure(Map properties) {
        super.configure(properties);

        // TODO Note that check for addresss!=null is done automatically in super-constructor, in FlagUtils.checkRequiredFields
        // Yikes, dangerous code for accessing fields of sub-class in super-class' constructor! But getting away with it so far!

        if (mutexSupport == null) {
            mutexSupport = new MutexSupport();
        }

        boolean deferConstructionChecks = (properties.containsKey("deferConstructionChecks") && TypeCoercions.coerce(properties.get("deferConstructionChecks"), Boolean.class));
        if (!deferConstructionChecks) {
            if (getDisplayName() == null) {
                setDisplayName((truth(user) ? user+"@" : "") + address.getHostName());
            }
        }
    }

    @Override
    public void init() {
        super.init();
        
        sshPoolCache = buildSshToolPoolCacheLoader();

        Callable<Task<?>> cleanupTaskFactory = new Callable<Task<?>>() {
            @Override public Task<Void> call() {
                return new BasicTask<Void>(new Callable<Void>() {
                    @Override public Void call() {
                        try {
                            if (sshPoolCache != null) sshPoolCache.cleanUp();
                            return null;
                        } catch (Exception e) {
                            // Don't rethrow: the behaviour of executionManager is different from a scheduledExecutorService,
                            // if we throw an exception, then our task will never get executed again
                            LOG.warn("Problem cleaning up ssh-pool-cache", e);
                            return null;
                        } catch (Throwable t) {
                            LOG.warn("Problem cleaning up ssh-pool-cache (rethrowing)", t);
                            throw Exceptions.propagate(t);
                        }
                    }});
            }
        };
        
        Duration expiryDuration = getConfig(SSH_CACHE_EXPIRY_DURATION);
        cleanupTask = getManagementContext().getExecutionManager().submit(new ScheduledTask(cleanupTaskFactory).period(expiryDuration));
    }
    
    @Override
    public void close() throws IOException {
        if (sshPoolCache != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} invalidating all entries in ssh pool cache. Final stats: {}", this, sshPoolCache.stats());
            }
            sshPoolCache.invalidateAll();
        }
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
            cleanupTask = null;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    @Override
    public InetAddress getAddress() {
        return address;
    }

    public HostAndPort getSshHostAndPort() {
        String host = getConfig(SSH_HOST);
        if (host == null || Strings.isEmpty(host))
            host = address.getHostName();
        Integer port = getConfig(SSH_PORT);
        if (port == null || port == 0)
            port = 22;
        return HostAndPort.fromParts(host, port);
    }

    public String getUser() {
        if (!truth(user)) {
            if (hasConfig(SshTool.PROP_USER, false)) {
                LOG.warn("User configuration for "+this+" set after deployment; deprecated behaviour may not be supported in future versions");
            }
            return getConfig(SshTool.PROP_USER);
        }
        return user;
    }

    /** port for SSHing */
    public int getPort() {
        return getConfig(SshTool.PROP_PORT);
    }

    protected <T> T execSsh(final Map<String, ?> props, final Function<ShellTool, T> task) {
        if (sshPoolCache == null) {
            // required for uses that instantiate SshMachineLocation directly, so init() will not have been called
            sshPoolCache = buildSshToolPoolCacheLoader();
        }
        Pool<SshTool> pool = sshPoolCache.getUnchecked(props);
        if (LOG.isTraceEnabled()) {
            LOG.trace("{} execSsh got pool: {}", this, pool);
        }

        if (truth(props.get(CLOSE_CONNECTION.getName()))) {
            Function<SshTool, T> close = new Function<SshTool, T>() {
                @Override
                public T apply(SshTool input) {
                    T result = task.apply(input);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} invalidating all sshPoolCache entries: {}", SshMachineLocation.this, sshPoolCache.stats().toString());
                    }
                    sshPoolCache.invalidateAll();
                    sshPoolCache.cleanUp();
                    return result;
                }
            };
            return pool.exec(close);
        } else {
            return pool.exec(task);
        }
    }

    protected SshTool connectSsh() {
        return connectSsh(ImmutableMap.of());
    }

    protected boolean previouslyConnected = false;
    protected SshTool connectSsh(Map props) {
        try {
            if (!truth(user)) {
                String newUser = getUser();
                if (LOG.isTraceEnabled()) LOG.trace("For "+this+", setting user in connectSsh: oldUser="+user+"; newUser="+newUser);
                user = newUser;
            }

            ConfigBag args = new ConfigBag()
                .configure(SshTool.PROP_USER, user)
                // default value of host, overridden if SSH_HOST is supplied
                .configure(SshTool.PROP_HOST, address.getHostName())
                .putAll(props);

            for (Map.Entry<String,Object> entry: getAllConfigBag().getAllConfig().entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(SshTool.BROOKLYN_CONFIG_KEY_PREFIX)) {
                    key = Strings.removeFromStart(key, SshTool.BROOKLYN_CONFIG_KEY_PREFIX);
                } else if (ALL_SSH_CONFIG_KEY_NAMES.contains(entry.getKey())) {
                    // key should be included, and does not need to be changed

                    // TODO make this config-setting mechanism more universal
                    // currently e.g. it will not admit a tool-specific property.
                    // thinking either we know about the tool here,
                    // or we don't allow unadorned keys to be set
                    // (require use of BROOKLYN_CONFIG_KEY_PREFIX)
                } else {
                    // this key is not applicable here; ignore it
                    continue;
                }
                args.putStringKey(key, entry.getValue());
            }
            if (LOG.isTraceEnabled()) LOG.trace("creating ssh session for "+args);
            if (!user.equals(args.get(SshTool.PROP_USER))) {
                LOG.warn("User mismatch configuring ssh for "+this+": preferring user "+args.get(SshTool.PROP_USER)+" over "+user);
                user = args.get(SshTool.PROP_USER);
            }

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

    // TODO submitCommands and submitScript which submit objects we can subsequently poll (cf JcloudsSshMachineLocation.submitRunScript)

    /**
     * Executes a set of commands, directly on the target machine (no wrapping in script).
     * Joined using {@literal ;} by default.
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
        return newExecWithLoggingHelpers().execCommands(props, summaryForLogging, commands, env);
    }

    /**
     * Executes a set of commands, wrapped as a script sent to the remote machine.
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
        return newExecWithLoggingHelpers().execScript(props, summaryForLogging, commands, env);
    }

    protected ExecWithLoggingHelpers newExecWithLoggingHelpers() {
        return new ExecWithLoggingHelpers("SSH") {
            @Override
            protected <T> T execWithTool(MutableMap<String, Object> props, Function<ShellTool, T> function) {
                return execSsh(props, function);
            }
            @Override
            protected void preExecChecks() {
                Preconditions.checkNotNull(address, "host address must be specified for ssh");
            }
            @Override
            protected String constructDefaultLoggingPrefix(ConfigBag execFlags) {
                String hostname = getAddress().getHostName();
                Integer port = execFlags.peek(SshTool.PROP_PORT);
                if (port == null) port = getConfig(ConfigUtils.prefixedKey(SshTool.BROOKLYN_CONFIG_KEY_PREFIX, SshTool.PROP_PORT));
                return (user != null ? user+"@" : "") + hostname + (port != null ? ":"+port : "");
            }
            @Override
            protected String getTargetName() {
                return ""+SshMachineLocation.this;
            }
        }.logger(logSsh);
    }

    protected int execWithLogging(Map<String,?> props, String summaryForLogging, List<String> commands, Map env, final Closure<Integer> execCommand) {
        return newExecWithLoggingHelpers().execWithLogging(props, summaryForLogging, commands, env, new ExecRunner() {
                @Override public int exec(ShellTool ssh, Map<String, ?> flags, List<String> cmds, Map<String, ?> env) {
                    return execCommand.call(ssh, flags, cmds, env);
                }});
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
            return execSsh(props, new Function<ShellTool,Integer>() {
                public Integer apply(ShellTool ssh) {
                    return ((SshTool) ssh).copyToServer(props, new KnownSizeInputStream(src, filesize), destination);
                }});
        }
    }
    // FIXME the return code is not a reliable indicator of success or failure
    // Closes input stream before returning
    public int copyTo(final Map<String,?> props, final InputStream src, final String destination) {
        return execSsh(props, new Function<ShellTool,Integer>() {
            public Integer apply(ShellTool ssh) {
                return ((SshTool)ssh).copyToServer(props, src, destination);
            }});
    }

    // FIXME the return code is not a reliable indicator of success or failure
    public int copyFrom(String remote, String local) {
        return copyFrom(MutableMap.<String,Object>of(), remote, local);
    }
    public int copyFrom(final Map<String,?> props, final String remote, final String local) {
        return execSsh(props, new Function<ShellTool,Integer>() {
            public Integer apply(ShellTool ssh) {
                return ((SshTool)ssh).copyFromServer(props, remote, new File(local));
            }});
    }

    public int installTo(String url, String destPath) {
        return installTo(MutableMap.<String, Object>of(), url, destPath);
    }

    public int installTo(Map<String,?> props, String url, String destPath) {
        return installTo(ResourceUtils.create(this), props, url, destPath);
    }

    public int installTo(ResourceUtils loader, String url, String destPath) {
        return installTo(loader, MutableMap.<String, Object>of(), url, destPath);
    }

    /**
     * Installs the given URL at the indicated destination path.
     * <p>
     * Attempts to curl the source URL on the remote machine,
     * then if that fails, loads locally (from classpath or file) and transfers.
     * <p>
     * Use {@link ArchiveUtils} to handle directories and their contents properly.
     *
     * TODO allow s3://bucket/file URIs for AWS S3 resources
     * TODO use PAX-URL style URIs for maven artifacts
     * TODO use subtasks here for greater visibility?; deprecate in favour of SshTasks.installFromUrl?
     *
     * @param utils A {@link ResourceUtils} that can resolve the source URLs
     * @param url The source URL to be installed
     * @param destPath The file to be created on the destination
     *
     * @see ArchiveUtils#deploy(String, SshMachineLocation, String)
     * @see ArchiveUtils#deploy(String, SshMachineLocation, String, String)
     * @see ResourceUtils#getResourceFromUrl(String)
     */
    public int installTo(ResourceUtils utils, Map<String,?> props, String url, String destPath) {
        LOG.debug("installing {} to {} on {}, attempting remote curl", new Object[] { url, destPath, this });

        try {
            PipedInputStream insO = new PipedInputStream(); OutputStream outO = new PipedOutputStream(insO);
            PipedInputStream insE = new PipedInputStream(); OutputStream outE = new PipedOutputStream(insE);
            StreamGobbler sgsO = new StreamGobbler(insO, null, LOG); sgsO.setLogPrefix("[curl @ "+address+":stdout] ").start();
            StreamGobbler sgsE = new StreamGobbler(insE, null, LOG); sgsE.setLogPrefix("[curl @ "+address+":stdout] ").start();
            Map<String, ?> sshProps = MutableMap.<String, Object>builder().putAll(props).put("out", outO).put("err", outE).build();
            int result = execScript(sshProps, "copying remote resource "+url+" to server",  ImmutableList.of(
                    BashCommands.INSTALL_CURL, // TODO should hold the 'installing' mutex
                    "mkdir -p `dirname '"+destPath+"'`",
                    "curl "+url+" -L --silent --insecure --show-error --fail --connect-timeout 60 --max-time 600 --retry 5 -o '"+destPath+"'"));
            sgsO.close();
            sgsE.close();
            if (result != 0) {
                LOG.debug("installing {} to {} on {}, curl failed, attempting local fetch and copy", new Object[] { url, destPath, this });
                try {
                    Tasks.setBlockingDetails("retrieving resource "+url+" for copying across");
                    InputStream stream = utils.getResourceFromUrl(url);
                    Tasks.setBlockingDetails("copying resource "+url+" to server");
                    result = copyTo(props, stream, destPath);
                } finally {
                    Tasks.setBlockingDetails(null);
                }
            }
            if (result == 0) {
                LOG.debug("installing {} complete; {} on {}", new Object[] { url, destPath, this });
            } else {
                LOG.warn("installing {} failed; {} on {}: {}", new Object[] { url, destPath, this, result });
            }
            return result;
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public String toString() {
        return "SshMachineLocation["+getDisplayName()+":"+address+"]";
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
     * @see PortRanges#ANY_HIGH_PORT
     */
    @Override
    public boolean obtainSpecificPort(int portNumber) {
        synchronized (usedPorts) {
            // TODO Does not yet check if the port really is free on this machine
            if (usedPorts.contains(portNumber)) {
                return false;
            } else {
                usedPorts.add(portNumber);
                return true;
            }
        }
    }

    @Override
    public int obtainPort(PortRange range) {
        synchronized (usedPorts) {
            for (int p: range)
                if (obtainSpecificPort(p)) return p;
            if (LOG.isDebugEnabled()) LOG.debug("unable to find port in {} on {}; returning -1", range, this);
            return -1;
        }
    }

    @Override
    public void releasePort(int portNumber) {
        synchronized (usedPorts) {
            usedPorts.remove((Object) portNumber);
        }
    }

    public boolean isSshable() {
        String cmd = "date";
        try {
            try {
                Socket s = new Socket(getAddress(), getPort());
                s.close();
            } catch (IOException e) {
                if (LOG.isDebugEnabled()) LOG.debug(""+this+" not [yet] reachable (socket "+getAddress()+":"+getPort()+"): "+e);
                return false;
            }
            // this should do execCommands because sftp subsystem might not be available (or sometimes seems to take a while for it to become so?)
            int result = execCommands(MutableMap.<String,Object>of(), "isSshable", ImmutableList.of(cmd));
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
        return getMachineDetails().getOsDetails();
    }

    @Override
    public MachineDetails getMachineDetails() {
        MachineDetails details = machineDetails;
        if (details == null) {
            // Or could just load and store several times
            Tasks.setBlockingDetails("Waiting for machine details");
            try {
                synchronized (machineDetailsLock) {
                    details = machineDetails;
                    if (details == null) {
                        machineDetails = details = BasicMachineDetails.forSshMachineLocation(this);
                    }
                }
            } finally {
                Tasks.resetBlockingDetails();
            }
        }
        return details;
    }

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

    //We want the SshMachineLocation to be serializable and therefore the pool needs to be dealt with correctly.
    //In this case we are not serializing the pool (we made the field transient) and create a new pool when deserialized.
    //This fix is currently needed for experiments, but isn't used in normal Brooklyn usage.
    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        sshPoolCache = buildSshToolPoolCacheLoader();
    }

    /** returns the un-passphrased key-pair info if a key is being used, or else null */
    public KeyPair findKeyPair() {
        String fn = getConfig(SshTool.PROP_PRIVATE_KEY_FILE);
        ResourceUtils r = ResourceUtils.create(this);
        if (fn!=null) return SecureKeys.readPem(r.getResourceFromUrl(fn), getConfig(SshTool.PROP_PRIVATE_KEY_PASSPHRASE));
        String data = getConfig(SshTool.PROP_PRIVATE_KEY_DATA);
        if (data!=null) return SecureKeys.readPem(new ReaderInputStream(new StringReader(data)), getConfig(SshTool.PROP_PRIVATE_KEY_PASSPHRASE));
        if (findPassword()!=null)
            // if above not specified, and password is, use password
            return null;
        // fall back to id_rsa and id_dsa
        if (new File( Urls.mergePaths(System.getProperty("user.home"), ".ssh/id_rsa") ).exists() )
            return SecureKeys.readPem(r.getResourceFromUrl("~/.ssh/id_rsa"), getConfig(SshTool.PROP_PRIVATE_KEY_PASSPHRASE));
        if (new File( Urls.mergePaths(System.getProperty("user.home"), ".ssh/id_dsa") ).exists() )
            return SecureKeys.readPem(r.getResourceFromUrl("~/.ssh/id_dsa"), getConfig(SshTool.PROP_PRIVATE_KEY_PASSPHRASE));
        LOG.warn("Unable to extract any key or passphrase data in request to findKeyPair for "+this);
        return null;
    }

    /** returns the password being used to log in, if a password is being used, or else null */
    public String findPassword() {
        return getConfig(SshTool.PROP_PASSWORD);
    }

}

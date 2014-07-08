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
package brooklyn.util.internal.ssh;

import static brooklyn.entity.basic.ConfigKeys.newConfigKey;
import static brooklyn.entity.basic.ConfigKeys.newStringConfigKey;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.util.stream.KnownSizeInputStream;
import brooklyn.util.time.Duration;

/**
 * Defines the methods available on the various different implementations of SSH,
 * and configuration options which are also generally available.
 * <p>
 * The config keys in this class can be supplied (or their string equivalents, where the flags/props take {@code Map<String,?>})
 * to influence configuration, either for the tool/session itself or for individual commands.
 * <p>
 * To specify some of these properties on a global basis, use the variants of the keys here
 * contained in {@link ConfigKeys}
 * (which are generally {@value #BROOKLYN_CONFIG_KEY_PREFIX} prefixed to the names of keys here).
 */
public interface SshTool extends ShellTool {
    
    /** Public-facing global config keys for Brooklyn are defined in ConfigKeys, 
     * and have this prefix pre-prended to the config keys in this class. 
     * These keys are detected from entity/global config and automatically applied to ssh executions. */
    public static final String BROOKLYN_CONFIG_KEY_PREFIX = "brooklyn.ssh.config.";
    
    public static final ConfigKey<String> PROP_TOOL_CLASS = newStringConfigKey("tool.class", "SshTool implementation to use", null);
    
    public static final ConfigKey<String> PROP_HOST = newStringConfigKey("host", "Host to connect to (required)", null);
    public static final ConfigKey<Integer> PROP_PORT = newConfigKey("port", "Port on host to connect to", 22);
    public static final ConfigKey<String> PROP_USER = newConfigKey("user", "User to connect as", System.getProperty("user.name"));
    public static final ConfigKey<String> PROP_PASSWORD = newStringConfigKey("password", "Password to use to connect", null);
    
    public static final ConfigKey<String> PROP_PRIVATE_KEY_FILE = newStringConfigKey("privateKeyFile", "the path of an ssh private key file; leave blank to use defaults (i.e. ~/.ssh/id_rsa and id_dsa)", null);
    public static final ConfigKey<String> PROP_PRIVATE_KEY_DATA = newStringConfigKey("privateKeyData", "the private ssh key (e.g. contents of an id_rsa or id_dsa file)", null);
    public static final ConfigKey<String> PROP_PRIVATE_KEY_PASSPHRASE = newStringConfigKey("privateKeyPassphrase", "the passphrase for the ssh private key", null);
    public static final ConfigKey<Boolean> PROP_STRICT_HOST_KEY_CHECKING = newConfigKey("strictHostKeyChecking", "whether to check the remote host's identification; defaults to false", false);
    public static final ConfigKey<Boolean> PROP_ALLOCATE_PTY = newConfigKey("allocatePTY", "whether to allocate PTY (vt100); if true then stderr is sent to stdout, but sometimes required for sudo'ing due to requiretty", false);

    public static final ConfigKey<Long> PROP_CONNECT_TIMEOUT = newConfigKey("connectTimeout", "Timeout in millis when establishing an SSH connection; if 0 then uses default (usually 30s)", 0L);
    public static final ConfigKey<Long> PROP_SESSION_TIMEOUT = newConfigKey("sessionTimeout", "Timeout in millis for an ssh session; if 0 then uses default", 0L);
    public static final ConfigKey<Integer> PROP_SSH_TRIES = newConfigKey("sshTries", "Max number of times to attempt ssh operations", 4);
    public static final ConfigKey<Long> PROP_SSH_TRIES_TIMEOUT = newConfigKey("sshTriesTimeout", "Time limit for attempting retries; will not interrupt tasks, but stops retrying after a total amount of elapsed time", Duration.TWO_MINUTES.toMilliseconds());
    public static final ConfigKey<Long> PROP_SSH_RETRY_DELAY = newConfigKey("sshRetryDelay", "Time (in milliseconds) before first ssh-retry, after which it will do exponential backoff", 50L);

    // NB -- items above apply for _session_ (a tool), below apply for a _call_
    // TODO would be nice to track which arguments are used, so we can indicate whether extras are supplied

    public static final ConfigKey<String> PROP_PERMISSIONS = newConfigKey("permissions", "Default permissions for files copied/created on remote machine; must be four-digit octal string, default '0644'", "0644");
    public static final ConfigKey<Long> PROP_LAST_MODIFICATION_DATE = newConfigKey("lastModificationDate", "Last-modification-date to be set on files copied/created (should be UTC/1000, ie seconds since 1970; default 0 usually means current)", 0L);
    public static final ConfigKey<Long> PROP_LAST_ACCESS_DATE = newConfigKey("lastAccessDate", "Last-access-date to be set on files copied/created (should be UTC/1000, ie seconds since 1970; default 0 usually means lastModificationDate)", 0L);
    public static final ConfigKey<Integer> PROP_OWNER_UID = newConfigKey("ownerUid", "Default owner UID (not username) for files created on remote machine; default is unset", -1);
    
    // TODO remove unnecessary "public static final" modifiers
    
    // TODO Could define the following in SshMachineLocation, or some such?
    //public static ConfigKey<String> PROP_LOG_PREFIX = newStringKey("logPrefix", "???", ???);
    //public static ConfigKey<Boolean> PROP_NO_STDOUT_LOGGING = newStringKey("noStdoutLogging", "???", ???);
    //public static ConfigKey<Boolean> PROP_NO_STDOUT_LOGGING = newStringKey("noStdoutLogging", "???", ???);

    /**
     * @throws SshException
     */
    public void connect();

    /**
     * @deprecated since 0.7.0; (since much earlier) this ignores the argument in favour of {@link #PROP_SSH_TRIES}
     * 
     * @param maxAttempts
     * @throws SshException
     */
    public void connect(int maxAttempts);

    public void disconnect();

    public boolean isConnected();

    /**
     * @see super{@link #execScript(Map, List, Map)}
     * @throws SshException If failed to connect
     */
    @Override
    public int execScript(Map<String,?> props, List<String> commands, Map<String,?> env);

    /**
     * @see #execScript(Map, List, Map)
     */
    @Override
    public int execScript(Map<String,?> props, List<String> commands);

    /**
     * @see super{@link #execCommands(Map, List, Map)}
     * @throws SshException If failed to connect
     */
    @Override
    public int execCommands(Map<String,?> properties, List<String> commands, Map<String,?> env);

    /**
     * @see #execCommands(Map, List, Map)
     */
    @Override
    public int execCommands(Map<String,?> properties, List<String> commands);

    /**
     * Copies the file to the server at the given path.
     * If path is null, empty, '.', '..', or ends with '/' then file name is used.
     * <p>
     * The file will not preserve the permission of last _access_ date.
     * 
     * Optional properties are:
     * <ul>
     *   <li>'permissions' (e.g. "0644") - see {@link #PROP_PERMISSIONS}
     *   <li>'lastModificationDate' see {@link #PROP_LAST_MODIFICATION_DATE}; not supported by all SshTool implementations
     *   <li>'lastAccessDate' see {@link #PROP_LAST_ACCESS_DATE}; not supported by all SshTool implementations
     * </ul>
     * 
     * @return exit code (not supported by all SshTool implementations, usually throwing on error;
     * sometimes possibly returning 0 even on error (?) )
     */
    public int copyToServer(Map<String,?> props, File localFile, String pathAndFileOnRemoteServer);

    /**
     * Closes the given input stream before returning.
     * Consider using {@link KnownSizeInputStream} for efficiency when the size of the stream is known.
     * 
     * @see #copyToServer(Map, File, String)
     */
    public int copyToServer(Map<String,?> props, InputStream contents, String pathAndFileOnRemoteServer);

    /**
     * @see #copyToServer(Map, File, String)
     */
    public int copyToServer(Map<String,?> props, byte[] contents, String pathAndFileOnRemoteServer);

    /**
     * Copies the file from the server at the given path.
     *
     * @return exit code (not supported by all SshTool implementations, usually throwing on error;
     * sometimes possibly returning 0 even on error (?) )
     */
    public int copyFromServer(Map<String,?> props, String pathAndFileOnRemoteServer, File local);

    // TODO might be more efficicent than copyFrom by way of temp file
//    /**
//     * Reads from the file at the given path on the remote server.
//     */
//    public InputStream streamFromServer(Map<String,?> props, String pathAndFileOnRemoteServer);

}

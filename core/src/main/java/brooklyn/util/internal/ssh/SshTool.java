package brooklyn.util.internal.ssh;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicConfigKey.StringConfigKey;

/**
 * 
 */
public interface SshTool {

    /* config which can be supplied by user to configure the ssh connection.
     * NB: public-facing values are including in ConfigKeys */
    
    /** Public-facing config keys are defined in ConfigKeys, and have this prefix pre-prended to the keys below. */
    public static final String BROOKLYN_CONFIG_KEY_PREFIX = "brooklyn.ssh.config.";
    
    public static final ConfigKey<String> PROP_TOOL_CLASS = new StringConfigKey("tool.class", "SshTool implementation to use", null);
    
    public static final ConfigKey<String> PROP_HOST = new StringConfigKey("host", "Host to connect to (required)", null);
    public static final ConfigKey<Integer> PROP_PORT = new BasicConfigKey<Integer>(Integer.class, "port", "Port on host to connect to", 22);
    public static final ConfigKey<String> PROP_USER = new StringConfigKey("user", "User to connect as", null);
    public static final ConfigKey<String> PROP_PASSWORD = new StringConfigKey("user", "Password to use to connect", null);
    
    public static final ConfigKey<OutputStream> PROP_OUT_STREAM = new BasicConfigKey<OutputStream>(OutputStream.class, "out", "Stream to which to capture stdout");
    public static final ConfigKey<OutputStream> PROP_ERR_STREAM = new BasicConfigKey<OutputStream>(OutputStream.class, "err", "Stream to which to capture stderr");
    
    public static final ConfigKey<String> PROP_SEPARATOR = new StringConfigKey("separator", "string to insert between caller-supplied commands being executed as commands", " ; ");
    
    public static final ConfigKey<String> PROP_SCRIPT_DIR = new StringConfigKey("scriptDir", "directory where scripts should be copied", "/tmp");
    public static final ConfigKey<String> PROP_SCRIPT_HEADER = new StringConfigKey("scriptHeader", "lines to insert at the start of scripts generated for caller-supplied commands for script execution", "#!/bin/bash -e\n");
    public static final ConfigKey<String> PROP_DIRECT_HEADER = new StringConfigKey("directHeader", "commands to run remotely before any caller-supplied commands for direct execution", "exec bash -e");

    public static final ConfigKey<String> PROP_PRIVATE_KEY_FILE = new StringConfigKey("privateKeyFile", "the path of an ssh private key file; leave blank to use defaults (i.e. ~/.ssh/id_rsa and id_dsa)", null);
    public static final ConfigKey<String> PROP_PRIVATE_KEY_DATA = new StringConfigKey("privateKeyData", "the private ssh key (e.g. contents of an id_rsa.pub or id_dsa.pub file)", null);
    public static final ConfigKey<String> PROP_PRIVATE_KEY_PASSPHRASE = new StringConfigKey("privateKeyPassphrase", "the passphrase for the ssh private key", null);
    public static final ConfigKey<Boolean> PROP_STRICT_HOST_KEY_CHECKING = new BasicConfigKey<Boolean>(Boolean.class, "strictHostKeyChecking", "whether to check the remote host's identification; defaults to false", false);
    public static final ConfigKey<Boolean> PROP_ALLOCATE_PTY = new BasicConfigKey<Boolean>(Boolean.class, "allocatePTY", "whether to allocate PTY (vt100); if true then stderr is sent to stdout, but sometimes required for sudo'ing due to requiretty", false);

    public static final ConfigKey<Integer> PROP_CONNECT_TIMEOUT = new BasicConfigKey<Integer>(Integer.class, "connectTimeout", "The timeout when establishing an SSH connection; if 0 then uses default", 0);
    public static final ConfigKey<Integer> PROP_SESSION_TIMEOUT = new BasicConfigKey<Integer>(Integer.class, "sessionTimeout", "The timeout for an ssh session; if 0 then uses default", 0);
    public static final ConfigKey<Integer> PROP_SSH_TRIES = new BasicConfigKey<Integer>(Integer.class, "sshTries", "Max number of attempts to connect when doing ssh operations", 4);
    public static final ConfigKey<Integer> PROP_SSH_TRIES_TIMEOUT = new BasicConfigKey<Integer>(Integer.class, "sshTriesTimeout", "Timeout when attempting to connect for ssh operations; so if too slow trying sshTries times, will abort anyway", 2*60*1000);
    public static final ConfigKey<Long> PROP_SSH_RETRY_DELAY = new BasicConfigKey<Long>(Long.class, "sshRetryDelay", "Time (in milliseconds) before first ssh-retry, after which it will do exponential backoff", 50L);

    public static final ConfigKey<String> PROP_PERMISSIONS = new StringConfigKey("permissions", "Default permissions for files copied/created on remote machine; must be four-digit octal string, default '0644'", "0644");
    public static final ConfigKey<Long> PROP_LAST_MODIFICATION_DATE = new BasicConfigKey<Long>(Long.class, "lastModificationDate", "Last-modification-date to be set on files copied/created (should be UTC/1000, ie seconds since 1970; defaults to current)", 0L);
    public static final ConfigKey<Long> PROP_LAST_ACCESS_DATE = new BasicConfigKey<Long>(Long.class, "lastAccessDate", "Last-access-date to be set on files copied/created (should be UTC/1000, ie seconds since 1970; defaults to lastModificationDate)", 0L);

    // FIXME Defined/used only in SshMachineLocation?
    //public static ConfigKey<String> PROP_LOG_PREFIX = new StringConfigKey("logPrefix", "???", ???);
    //public static ConfigKey<Boolean> PROP_NO_STDOUT_LOGGING = new StringConfigKey("noStdoutLogging", "???", ???);
    //public static ConfigKey<Boolean> PROP_NO_STDOUT_LOGGING = new StringConfigKey("noStdoutLogging", "???", ???);

    /**
     * @deprecated since 0.4; use PROP_PRIVATE_KEY_FILE; if this contains more than one element then it will fail.
     */
    public static final ConfigKey<List<String>> PROP_KEY_FILES = new BasicConfigKey(List.class, "keyFiles", "DEPRECATED: see privateKeyFile", Collections.<String>emptyList());

    /**
     * @deprecated since 0.4; use PROP_PRIVATE_KEY_DATA instead
     */
    @Deprecated
    public static final ConfigKey<String> PROP_PRIVATE_KEY = new StringConfigKey("privateKey", "DEPRECATED: see privateKeyData", null);

    /**
     * @throws SshException
     */
    public void connect();

    /**
     * @param maxAttempts
     * @throws SshException
     */
    public void connect(int maxAttempts);

    public void disconnect();

    public boolean isConnected();

    /**
     * Executes the set of commands in a shell script; optional property 'out'
     * should be an output stream. Blocks until completion (unless property
     * 'block' set as false).
     * <p>
     * values in environment parameters are wrapped in double quotes, with double quotes escaped 
     * 
     * @return exit status of script
     * @throws SshException
     */
    public int execScript(Map<String,?> props, List<String> commands, Map<String,?> env);

    /**
     * @see execScript(Map, List, Map)
     */
    public int execScript(Map<String,?> props, List<String> commands);

    /** @deprecated @see execScript(Map, List, Map) */
    public int execShell(Map<String,?> props, List<String> commands);
    /** @deprecated @see execScript(Map, List, Map) */
    public int execShell(Map<String,?> props, List<String> commands, Map<String,?> env);

    /**
     * Executes the set of commands using ssh exec, ";" separated (overridable
     * with property 'separator'.
     *
     * Optional properties 'out' and 'err' should be streams.
     * <p>
     * This is generally simpler/preferable to shell, but is not suitable if you need 
     * env values whare are only set on a fully-fledged shell.
     * 
     * @return exit status
     * @throws SshException
     */
    public int execCommands(Map<String,?> properties, List<String> commands, Map<String,?> env);

    /**
     * @see execuCommands(Map, List, Map)
     */
    public int execCommands(Map<String,?> properties, List<String> commands);

    /**
     * @see #createFile(Map, String, InputStream, long)
     */
    public int transferFileTo(Map<String,?> props, InputStream input, String pathAndFileOnRemoteServer);
    
    /**
     * @see #createFile(Map, String, InputStream, long)
     */
    public int transferFileFrom(Map<String,?> props, String pathAndFileOnRemoteServer, String pathAndFileOnLocalServer);

    /**
     * Creates the given file with the given contents.
     * 
     * Properties can be:
     * <ul>
     * <li>permissions (must be four-digit octal string, default '0644');
     * <li>lastModificationDate (should be UTC/1000, ie seconds since 1970; defaults to current);
     * <li>lastAccessDate (again UTC/1000; defaults to lastModificationDate);
     * </ul>
     * If neither lastXxxDate set it does not send that line (unless property ptimestamp set true)
     * 
     * Closes the input stream before returning.
     * 
     * @param props
     * @param pathAndFileOnRemoteServer
     * @param input
     * @param size
     * @throws SshException
     */
    public int createFile(Map<String,?> props, String pathAndFileOnRemoteServer, InputStream input, long size);

    /**
     * @see #createFile(Map, String, InputStream, long)
     */
    public int createFile(Map<String,?> props, String pathAndFileOnRemoteServer, String contents);

    /**
     * @see #createFile(Map, String, InputStream, long)
     */
    public int createFile(Map<String,?> props, String pathAndFileOnRemoteServer, byte[] contents);

    /**
     * Copies file, but won't preserve permission of last _access_ date. 
     * If path is null, empty, '.', '..', or ends with '/' then file name is used.
     * <p>
     * To set permissions (or override mod date) use for example 'permissions:"0644"',
     *
     * @see #createFile(Map, String, InputStream, long)
     */
    public int copyToServer(Map<String,?> props, File f, String pathAndFileOnRemoteServer);
}

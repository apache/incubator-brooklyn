package brooklyn.util.internal;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicConfigKey.StringConfigKey;
import brooklyn.util.internal.ssh.SshException;

/**
 * 
 */
public interface SshTool {

    /* config which can be supplied by user to configure the ssh connection.
     * NB: public-facing values are including in ConfigKeys */
    
    /** Public-facing config keys are defined in ConfigKeys, and have this prefix pre-prended to the keys below. */
    public static String BROOKLYN_CONFIG_KEY_PREFIX = "brooklyn.ssh.config.";
    
    public static ConfigKey<String> PROP_HOST = new StringConfigKey("host", "Host to connect to (required)", null);
    public static ConfigKey<Integer> PROP_PORT = new BasicConfigKey<Integer>(Integer.class, "port", "Port on host to connect to", 22);
    public static ConfigKey<String> PROP_USER = new StringConfigKey("user", "User to connect as", null);
    public static ConfigKey<String> PROP_PASSWORD = new StringConfigKey("user", "Password to use to connect", null);
    
    public static ConfigKey<OutputStream> PROP_OUT_STREAM = new BasicConfigKey<OutputStream>(OutputStream.class, "out", "Stream to which to capture stdout");
    public static ConfigKey<OutputStream> PROP_ERR_STREAM = new BasicConfigKey<OutputStream>(OutputStream.class, "err", "Stream to which to capture stderr");
    
    public static ConfigKey<String> PROP_SEPARATOR = new StringConfigKey("separator", "string to insert between caller-supplied commands being executed as commands", " ; ");
    
    public static ConfigKey<String> PROP_SCRIPT_DIR = new StringConfigKey("scriptDir", "directory where scripts should be copied", "/tmp");
    public static ConfigKey<String> PROP_SCRIPT_HEADER = new StringConfigKey("scriptHeader", "lines to insert at the start of scripts generated for caller-supplied commands for script execution", "#!/bin/bash -e\n");
    public static ConfigKey<String> PROP_DIRECT_HEADER = new StringConfigKey("directHeader", "commands to run remotely before any caller-supplied commands for direct execution", "exec bash -e");

    
    /* more flags TODO */

//    warnOnDeprecated(props, "privateKey", "privateKeyData");
//    privateKeyData = getOptionalVal(props, "privateKey", String.class, privateKeyData);
//    privateKeyData = getOptionalVal(props, "privateKeyData", String.class, privateKeyData);
//    privateKeyPassphrase = getOptionalVal(props, "privateKeyPassphrase", String.class, privateKeyPassphrase);
//    
//    // for backwards compatibility accept keyFiles and privateKey
//    // but sshj accepts only a single privateKeyFile; leave blank to use defaults (i.e. ~/.ssh/id_rsa and id_dsa)
//    warnOnDeprecated(props, "keyFiles", null);
//    privateKeyFiles.addAll(getOptionalVal(props, "keyFiles", List.class, Collections.emptyList()));
//    String privateKeyFile = getOptionalVal(props, "privateKeyFile", String.class, null);
//    if (privateKeyFile != null) privateKeyFiles.add(privateKeyFile);
//    
//    strictHostKeyChecking = getOptionalVal(props, "strictHostKeyChecking", Boolean.class, strictHostKeyChecking);
//    allocatePTY = getOptionalVal(props, "allocatePTY", Boolean.class, allocatePTY);
//    connectTimeout = getOptionalVal(props, "connectTimeout", Integer.class, connectTimeout);
//    sessionTimeout = getOptionalVal(props, "sessionTimeout", Integer.class, sessionTimeout);
//    sshTries = getOptionalVal(props, "sshTries", Integer.class, sshTries);
//    sshTriesTimeout = getOptionalVal(props, "sshTriesTimeout", Integer.class, sshTriesTimeout);
//    sshRetryDelay = getOptionalVal(props, "sshRetryDelay", Long.class, sshRetryDelay);

//    String permissions = getOptionalVal(props, "permissions", String.class, "0644");
//    permissionsMask = Integer.parseInt(permissions, 8);
//    lastModificationDate = getOptionalVal(props, "lastModificationDate", Long.class, 0L);
//    lastAccessDate = getOptionalVal(props, "lastAccessDate", Long.class, 0L);

    // also might look at this in SshMachineLocation:
//  final Map<String,Object> sshFlags = MutableMap.<String,Object>builder().putAll(props).removeAll("logPrefix", "out", "err").build();
//  Object port = config.get("sshconfig.port");
//if (!truth(execFlags.get("noStdoutLogging"))) {


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

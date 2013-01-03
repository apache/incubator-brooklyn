package brooklyn.util.internal;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 
 */
public interface SshTool {

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

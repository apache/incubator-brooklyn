package brooklyn.util.internal;

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Map
import java.util.logging.Level

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.base.Preconditions
import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session

/**
 * This class is a wrapper for the Jsch {@link Session} object.
 *
 * FIXME OS X failure, if no recent command line ssh
 * <pre>
 * ssh_askpass: exec(/usr/libexec/ssh-askpass): No such file or directory
 * Permission denied, please try again.
 * ssh_askpass: exec(/usr/libexec/ssh-askpass): No such file or directory
 * Received disconnect from ::1: 2: Too many authentication failures for alex
 * </pre>
 *
 * @see http://wiki.jsch.org/index.php?Manual%2FExamples%2FJschExecExample
 */
public class SshJschTool {
    private static final Logger log = LoggerFactory.getLogger(SshJschTool.class)
    
	static {
	   java.util.logging.Logger.getLogger(".level").setLevel(Level.FINEST)
	}
       
    String host
    String user = System.getProperty('user.name')
    int port = 22
    List<String> keyFiles = ['~/.ssh/id_dsa','~/.ssh/id_rsa']
    String privateKey
    String publicKey
    Map config = [StrictHostKeyChecking:'no']

    private JSch jsch;
    private Session session;

    public SshJschTool(Map properties = [:]) {
        Preconditions.checkArgument properties.containsKey('host'), "properties must contain a host key"
        Preconditions.checkArgument properties.get('host') instanceof String, "host value must be a string"
        Preconditions.checkArgument properties.get('host').length() > 0, "host value must not be an empty string"
        host = properties.remove('host')

        if (properties.user) {
            Preconditions.checkArgument properties.user instanceof String, "user value must be a string"
            user = properties.remove('user')
        }

        if (properties.port) {
            Preconditions.checkArgument properties.port instanceof Integer, "port value must be an integer"
            port = properties.remove('port')
        }

        if (properties.keyFiles) {
            Preconditions.checkArgument properties.keyFiles instanceof Collection, "keyFiles value must be a Collection"
            keyFiles = properties.remove('keyFiles')
        }

        if (properties.publicKey && properties.privateKey) {
            Preconditions.checkNotNull properties.publicKey
            Preconditions.checkNotNull properties.privateKey
            publicKey = properties.remove('publicKey')
            privateKey = properties.remove('privateKey')
        }

        config << properties
    }

    /**
     * Tidies up fields and config, e.g. replacing leading '~' with
     * System.getProperty('user.home'), replacing 'user@host' in host by setting
     * username.
     */
    public void tidy() {
        initJsch();
        if (host && host==~ /[^@]+@[^@]+/) {
            (user,host) = (host=~/([^@]+)@([^@]+)/)[0][1,2]
        }
        keyFiles = keyFiles.collect { it.startsWith('~')? (System.getProperty('user.home')+it.substring(1)) : it }
    }

    public void initJsch() {
        if (!jsch) jsch=new JSch();
    }

    public JSch getJsch() { jsch }
    public Session getSession() { session }

    public void connect() {
        if (session && session.isConnected()) throw new IllegalStateException("already connected to "+session)
        if (!host) throw new IllegalStateException("host must be specified")

        tidy()

        if (publicKey && privateKey) {
            jsch.addIdentity(privateKey, publicKey, null)
        }
        keyFiles.each { if (new File(it).exists()) { jsch.addIdentity(it) } }
        session = jsch.getSession(user, host, port)
        session.setConfig((Hashtable) config)

        try {
            session.connect()
        } catch (Exception e) {
            throw new IllegalStateException("Cannot connect to $user@$host", e);
        }
    }

    public void disconnect() {
        if (session && session.isConnected()) session.disconnect()
        session = null
    }

    public boolean isConnected() {
        session && session.isConnected()
    }

    protected void assertConnected() {
        if (!session) throw new IllegalStateException("no ssh session yet; invoke connect() before using")
        if (!isConnected()) throw new IllegalStateException("ssh session disconnected")
        assert session && session.isConnected() : ""
    }

    public static void block(Channel c, int pollPeriodMillis=50) {
        //TODO would much prefer to join the thread, but that isn't exposed!
        while (!c.isClosed()) synchronized (c) { c.wait(pollPeriodMillis) }
    }

    
    /**
     * Executes the set of commands in a shell; optional property 'out'
     * should be an output stream. Blocks until completion (unless property
     * 'block' set as false).
     * <p>
     * values in environment parameters are wrapped in double quotes, with double quotes escaped 
     * 
     * @return exit status of script
     *  
     */
    public int execShell(Map properties=[:], List<String> commands, Map env=[:]) {
        assertConnected()
        ChannelShell channel=session.openChannel("shell");
        lastChannel = channel
        if (properties.out) {
            channel.setOutputStream(properties.out, true)
        }
        long pause = properties.pause ?: 250

        def allCmds = []
        allCmds.add "exec bash -e"
        allCmds.addAll env.collect { String key, String value ->
			def ve = value.replaceAll("\\\"", "\\\\\\\"");
			"export $key=\"${ve}\"" }
        allCmds.addAll commands
		//explicit exit, in case it wasn't in the script above, because we run in blocking interactive mode
        allCmds.add 'exit $?'

        PipedOutputStream out = new PipedOutputStream()
        channel.setInputStream new PipedInputStream(out)
        channel.connect()

        try {
            allCmds.each {
                log.info "[{}] {}", host, it
                byte[] data = (it+"\n").getBytes("UTF-8")
                out.write(data)
                Thread.sleep pause
            }
        } catch (IOException ioe) {
			if (channel.getExitStatus()==0)
            	log.debug "Caught an IOException ({}) - the script has probably exited early", ioe.message
			else
				log.warn "Caught an IOException ({}) - the script may have exited early", ioe.message
        }

        if (properties.block==null || properties.block) {
            block(channel)
        }

        channel.getExitStatus()
    }

    /** convenience for the last channel used, in case it is needed */
    public Channel lastChannel
    
    /**
     * Executes the set of commands using ssh exec, ";" separated (overridable
     * with property 'separator'.
     *
     * Optional properties 'out' and 'err' should be streams.
     * This is generally preferable to shell because it captures both
     * streams and doesn't need an explicit exit, but may cause problems if you
     * are doing funny escaping or need env values which are only set on a
     * full-fledged shell;
     * returns exit status (if blocking)
     */
    public int execCommands(Map properties=[:], List<String> commands, Map env=[:]) {
        assertConnected()
        ChannelExec channel=session.openChannel("exec");
        lastChannel = channel;
        if (properties.out) {
            channel.setOutputStream(properties.out, true)
        }
        if (properties.err) {
            channel.setErrStream(properties.err, true)
        }
        String separator = properties.separator ?: "; "
        StringBuffer run = []
        env.each { key, value -> run.append("export $key=\"$value\"").append(separator) }
        commands.each { run.append(it).append(separator) }
        log.trace "Running command {}", run.toString()
        channel.setCommand  run.toString()

        channel.connect()
        if (properties.block==null || properties.block) {
            block(channel)
        }

        channel.getExitStatus()
    }


    static int checkAck(InputStream ins) throws IOException {
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //          -1 ???
        int b = ins.read()

        if (b==0) return 0
        if (b==1 || b==2) {
            StringBuffer sb = new StringBuffer()
            int c
            while (c!='\n') {
                c = ins.read()
                sb.append((char)c);
            }

            if (b==1) throw new IOException("ssh server indicated error: "+sb)
            if (b==2) throw new IOException("ssh server indicated fatal error: "+sb)
        }
        throw new IOException("ssh server failed to ack appropriately ("+b+")")
    }
    
    /**
     * @see #createFile(Map, String, InputStream, long)
     */
    public int transferFileTo(Map p=[:], InputStream input, String pathAndFileOnRemoteServer) {
        assertConnected()
        ChannelSftp channel=session.openChannel("sftp")
        lastChannel = channel
        channel.connect()
        channel.put(input, pathAndFileOnRemoteServer, p.permissions ?: 0644)
        int modified = p.lastModificationDate ?: System.currentTimeMillis()/1000
        channel.setMtime(pathAndFileOnRemoteServer, modified)
        channel.disconnect()
        channel.getExitStatus()
    }
    
    /**
     * @see #createFile(Map, String, InputStream, long)
     */
    public int transferFileFrom(Map p=[:], String pathAndFileOnRemoteServer, String pathAndFileOnLocalServer) {
        assertConnected()
        ChannelSftp channel=session.openChannel("sftp")
        lastChannel = channel
        channel.connect()
        channel.get(pathAndFileOnRemoteServer, pathAndFileOnLocalServer)
        channel.disconnect()
        channel.getExitStatus()
    }

    /**
     * Properties can be:
     * <ul>
     * <li>permissions (must be four-digit octal string, default '0644');
     * <li>lastModificationDate (should be UTC/1000, ie seconds since 1970; defaults to current);
     * <li>lastAccessDate (again UTC/1000; defaults to lastModificationDate);
     * </ul>
     * If neither lastXxxDate set it does not send that line (unless property ptimestamp set true)
     *
     * @param p
     * @param pathAndFileOnRemoteServer
     * @param input
     * @param size
     */
    public int createFile(Map p=[:], String pathAndFileOnRemoteServer, InputStream input, long size) {
        assertConnected()
        ChannelExec channel=session.openChannel("exec");
        lastChannel = channel;

        int targetSepIndex = pathAndFileOnRemoteServer.lastIndexOf('/');
        String targetName = pathAndFileOnRemoteServer.substring(targetSepIndex+1)
        String targetPath = targetSepIndex>=0 ? pathAndFileOnRemoteServer.substring(0, targetSepIndex) : "."

        boolean ptimestamp = (p.timestamp!=null ? p.timestamp : p.lastModificationDate || p.lastAccessDate);
        String command = "scp " + (ptimestamp ? "-p " :"") + "-t "+targetPath
        channel.setCommand command
        channel.connect()
        InputStream fromChannel = channel.getInputStream()
        OutputStream toChannel = channel.getOutputStream()
        checkAck(fromChannel)

        if (p.lastModificationDate || p.lastAccessDate) {
            long lmd = p.lastModificationDate ?: System.currentTimeMillis()/1000
            long lad = p.lastAccessDate ?: lmd
            toChannel << "T "+lmd+" 0 "+lad+" 0\n"
            toChannel.flush()
            checkAck(fromChannel)
        }
        // send "C0644 filesize filename", where filename should not include '/'
        toChannel << "C"+(p.permissions ?: '0644') + " "+size+" "+targetName+"\n"
        toChannel.flush()
        checkAck(fromChannel)

        byte[] buf = new byte[1024]
        while (size > 0) {
            int numRead = input.read(buf, 0, (int) (size > 1024 ? 1024 : size))
            if (numRead <= 0) throw new IOException("error reading from input when copying to "
                                                    + pathAndFileOnRemoteServer+" at "+session);
            size -= numRead
            toChannel.write buf, 0, numRead
        }
        toChannel.write 0
        toChannel.flush()
        checkAck(fromChannel)
        toChannel.close()
        channel.disconnect()
        channel.getExitStatus()
    }

    /**
     * Creates the given file with the given contents.
     *
     * Permissions specified using 'permissions:0755'.
     */
    public int createFile(Map p=[:], String pathAndFileOnRemoteServer, String contents) {
        byte[] b = contents.getBytes()
        createFile(p, pathAndFileOnRemoteServer, new ByteArrayInputStream(b), b.length)
    }

    /** Creates the given file with the given contents.
     *
     * Permissions specified using 'permissions:0755'.
     */
    public int createFile(Map p=[:], String pathAndFileOnRemoteServer, byte[] contents) {
        createFile(p,
                   pathAndFileOnRemoteServer,
                   new ByteArrayInputStream(contents),
                   contents.length)
    }

    /**
     * Copies file.
     *
     * (but won't preserve permission of last _access_ date since these not
     * available in java (last mod date is fine). If path is null, empty, '.',
     * '..', or ends with '/' then file name is used.
     * <p>
     * To set permissions (or override mod date) use 'permissions:"0644"',
     * as described at {@link #createFile(Map, InputStream, int, String)}
     *
     * @param file
     * @param pathAndFileOnRemoteServer
     */
    public int copyToServer(Map p=[:], File f, String pathAndFileOnRemoteServer=null) {
        def p2 = [lastModificationDate:f.lastModified()]
        p2 << p
        String fn = pathAndFileOnRemoteServer
        if (fn==null) fn="";
        if (fn=="." || fn=="..") fn+="/";
        if (!fn || fn.isEmpty() || fn.endsWith("/")) fn+=f.getName();

        createFile(p2, fn, new FileInputStream(f), f.size())
    }
}

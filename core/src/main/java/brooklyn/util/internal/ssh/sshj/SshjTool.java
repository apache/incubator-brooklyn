package brooklyn.util.internal.ssh.sshj;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.getCausalChain;
import static com.google.common.collect.Iterables.any;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.PTYMode;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.connection.channel.direct.Session.Shell;
import net.schmizz.sshj.connection.channel.direct.SessionChannel;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.xfer.InMemorySourceFile;

import org.apache.commons.io.input.ProxyInputStream;
import org.bouncycastle.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.internal.ssh.BackoffLimitedRetryHandler;
import brooklyn.util.internal.ssh.SshAbstractTool;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.stream.InputStreamSupplier;
import brooklyn.util.stream.KnownSizeInputStream;
import brooklyn.util.stream.StreamGobbler;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Time;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.Ints;

/**
 * For ssh and scp-style commands, using the sshj library.
 * <p>
 * The implementation is based on a combination of the existing brooklyn SshJschTool,
 * and the jclouds SshjSshClient.
 * <p>
 * Not thread-safe. Use a different SshjTool for each concurrent thread. 
 * If passing from one thread to another, ensure code goes through a synchronized block.
 */
public class SshjTool extends SshAbstractTool implements SshTool {

    private static final Logger LOG = LoggerFactory.getLogger(SshjTool.class);

    protected final int sshTriesTimeout;
    protected final int sshTries;
    protected final BackoffLimitedRetryHandler backoffLimitedRetryHandler;

    private class CloseFtpChannelOnCloseInputStream extends ProxyInputStream {
        private final SFTPClient sftp;

        private CloseFtpChannelOnCloseInputStream(InputStream proxy, SFTPClient sftp) {
            super(proxy);
            this.sftp = sftp;
        }

        @Override
        public void close() throws IOException {
            super.close();
            closeWhispering(sftp, this);
        }
    }

    private final SshjClientConnection sshClientConnection;

    public static SshjToolBuilder builder() {
        return new SshjToolBuilder();
    }
    
    public static class SshjToolBuilder extends Builder<SshjTool, SshjToolBuilder> {
    }
    
    public static class Builder<T extends SshjTool, B extends Builder<T,B>> extends AbstractSshToolBuilder<T,B> {
        protected int connectTimeout;
        protected int sessionTimeout;
        protected int sshTries = 4;  //allow 4 tries by default, much safer
        protected int sshTriesTimeout = 2*60*1000;  //allow 2 minutesby default (so if too slow trying sshTries times, abort anyway)
        protected long sshRetryDelay = 50L;
        
        @Override
        public B from(Map<String,?> props) {
            super.from(props);
            sshTries = getOptionalVal(props, PROP_SSH_TRIES);
            sshTriesTimeout = getOptionalVal(props, PROP_SSH_TRIES_TIMEOUT);
            sshRetryDelay = getOptionalVal(props, PROP_SSH_RETRY_DELAY);
            connectTimeout = getOptionalVal(props, PROP_CONNECT_TIMEOUT);
            sessionTimeout = getOptionalVal(props, PROP_SESSION_TIMEOUT);
            return self();
        }
        public B connectTimeout(int val) {
            this.connectTimeout = val; return self();
        }
        public B sessionTimeout(int val) {
            this.sessionTimeout = val; return self();
        }
        public B sshRetries(int val) {
            this.sshTries = val; return self();
        }
        public B sshRetriesTimeout(int val) {
            this.sshTriesTimeout = val; return self();
        }
        public B sshRetryDelay(long val) {
            this.sshRetryDelay = val; return self();
        }
        @Override
        @SuppressWarnings("unchecked")
        public T build() {
            return (T) new SshjTool(this);
        }
    }

    public SshjTool(Map<String,?> map) {
        this(builder().from(map));
    }
    
    protected SshjTool(Builder<?,?> builder) {
        super(builder);
        
        sshTries = builder.sshTries;
        sshTriesTimeout = builder.sshTriesTimeout;
        backoffLimitedRetryHandler = new BackoffLimitedRetryHandler(sshTries, builder.sshRetryDelay);

        sshClientConnection = SshjClientConnection.builder()
                .hostAndPort(HostAndPort.fromParts(host, port))
                .username(user)
                .password(password)
                .privateKeyPassphrase(privateKeyPassphrase)
                .privateKeyData(privateKeyData)
                .privateKeyFile(privateKeyFile)
                .strictHostKeyChecking(strictHostKeyChecking)
                .connectTimeout(builder.connectTimeout)
                .sessionTimeout(builder.sessionTimeout)
                .build();
        
        if (LOG.isTraceEnabled()) LOG.trace("Created SshTool {} ({})", this, System.identityHashCode(this));
    }
    
    @Override
    public void connect() {
        try {
            if (LOG.isTraceEnabled()) LOG.trace("Connecting SshjTool {} ({})", this, System.identityHashCode(this));
            acquire(sshClientConnection);
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) LOG.debug(toString()+" failed to connect (rethrowing)", e);
            throw propagate(e, "failed to connect");
        }
    }

    @Override
    public void connect(int maxAttempts) {
        connect(); // FIXME Should callers instead configure sshTries? But that would apply to all ssh attempts
    }

    @Override
    public void disconnect() {
        if (LOG.isTraceEnabled()) LOG.trace("Disconnecting SshjTool {} ({})", this, System.identityHashCode(this));
        try {
            sshClientConnection.clear();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public boolean isConnected() {
        return sshClientConnection.isConnected() && sshClientConnection.isAuthenticated();
    }
    
    @Override
    public int copyToServer(java.util.Map<String,?> props, byte[] contents, String pathAndFileOnRemoteServer) {
        return copyToServer(props, newInputStreamSupplier(contents), contents.length, pathAndFileOnRemoteServer);
    }
    
    @Override
    public int copyToServer(Map<String,?> props, InputStream contents, String pathAndFileOnRemoteServer) {
        /* sshj needs to:
         *   1) to know the length of the InputStream to copy the file to perform copy; and
         *   2) re-read the input stream on retry if the first attempt fails.
         * For now, write it to a file, unless caller supplies a KnownSizeInputStream
         * 
         * (We could have a switch where we hold it in memory if less than some max size,
         * but most the routines should supply a string or byte array or similar,
         * so we probably don't come here too often.)
         */
        if (contents instanceof KnownSizeInputStream) {
            return copyToServer(props, Suppliers.ofInstance(contents), ((KnownSizeInputStream)contents).length(), pathAndFileOnRemoteServer);
        } else {
            File tempFile = writeTempFile(contents);
            try {
                return copyToServer(props, tempFile, pathAndFileOnRemoteServer);
            } finally {
                tempFile.delete();
            }
        }
    }
    
    @Override
    public int copyToServer(Map<String,?> props, File localFile, String pathAndFileOnRemoteServer) {
        return copyToServer(props, newInputStreamSupplier(localFile), (int)localFile.length(), pathAndFileOnRemoteServer);
    }
    
    private int copyToServer(Map<String,?> props, Supplier<InputStream> contentsSupplier, long length, String pathAndFileOnRemoteServer) {
        acquire(new PutFileAction(props, pathAndFileOnRemoteServer, contentsSupplier, length));
        return 0; // TODO Can we assume put will have thrown exception if failed? Rather than exit code != 0?
    }


    @Override
    public int copyFromServer(Map<String,?> props, String pathAndFileOnRemoteServer, File localFile) {
        InputStream contents = acquire(new GetFileAction(pathAndFileOnRemoteServer));
        try {
            Files.copy(new InputStreamSupplier(contents), localFile);
            return 0; // TODO Can we assume put will have thrown exception if failed? Rather than exit code != 0?
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * This creates a script containing the user's commands, copies it to the remote server, and
     * executes the script. The script is then deleted.
     * <p>
     * Executing commands directly is fraught with dangers! Here are other options, and their problems:
     * <ul>
     *   <li>Use execCommands, rather than shell.
     *       The user's environment will not be setup normally (e.g. ~/.bash_profile will not have been sourced)
     *       so things like wget may not be on the PATH.
     *   <li>Send the stream of commands to the shell.
     *       But characters being sent can be lost.
     *       Try the following (e.g. in an OS X terminal):
     *        - sleep 5
     *        - <paste a command that is 1000s of characters long>
     *       Only the first 1024 characters appear. The rest are lost.
     *       If sending a stream of commands, you need to be careful not send the next (big) command while the
     *       previous one is still executing.
     *   <li>Send a stream to the shell, but spot when the previous command has completed.
     *       e.g. by looking for the prompt (but what if the commands being executed change the prompt?)
     *       e.g. by putting every second command as "echo <uid>", and waiting for the stdout.
     *       This gets fiddly...
     * </ul>
     * 
     * So on balance, the script-based approach seems most reliable, even if there is an overhead
     * of separate message(s) for copying the file!
     */
    @Override
    public int execScript(Map<String,?> props, List<String> commands, Map<String,?> env) {
        OutputStream out = getOptionalVal(props, PROP_OUT_STREAM);
        OutputStream err = getOptionalVal(props, PROP_ERR_STREAM);
        String scriptDir = getOptionalVal(props, PROP_SCRIPT_DIR);
        Boolean noExtraOutput = getOptionalVal(props, PROP_NO_EXTRA_OUTPUT);
        Boolean runAsRoot = getOptionalVal(props, PROP_RUN_AS_ROOT);
        
        String scriptPath = scriptDir+"/brooklyn-"+System.currentTimeMillis()+"-"+Identifiers.makeRandomId(8)+".sh";
        
        String scriptContents = toScript(props, commands, env);
        if (LOG.isTraceEnabled()) LOG.trace("Running shell command at {} as script: {}", host, scriptContents);
        copyToServer(ImmutableMap.of("permissions", "0700"), scriptContents.getBytes(), scriptPath);
        
        return asInt(acquire(new ShellAction(buildRunScriptCommand(scriptPath, noExtraOutput, runAsRoot), out, err)), -1);
    }

    public int execShellDirect(Map<String,?> props, List<String> commands, Map<String,?> env) {
        OutputStream out = getOptionalVal(props, PROP_OUT_STREAM);
        OutputStream err = getOptionalVal(props, PROP_ERR_STREAM);
        
        List<String> cmdSequence = toCommandSequence(commands, env);
        List<String> allcmds = ImmutableList.<String>builder()
                .add(getOptionalVal(props, PROP_DIRECT_HEADER))
                .addAll(cmdSequence)
                .add("exit $?")
                .build();
        
        if (LOG.isTraceEnabled()) LOG.trace("Running shell command at {}: {}", host, allcmds);
        
        Integer result = acquire(new ShellAction(allcmds, out, err));
        if (LOG.isTraceEnabled()) LOG.trace("Running shell command at {} completed: return status {}", host, result);
        return asInt(result, -1);
    }

    @Override
    public int execCommands(Map<String,?> props, List<String> commands, Map<String,?> env) {
        if (props.containsKey("blocks") && props.get("blocks") == Boolean.FALSE) {
            throw new IllegalArgumentException("Cannot exec non-blocking: command="+commands);
        }
        OutputStream out = getOptionalVal(props, PROP_OUT_STREAM);
        OutputStream err = getOptionalVal(props, PROP_ERR_STREAM);
        String separator = getOptionalVal(props, PROP_SEPARATOR);

        List<String> allcmds = toCommandSequence(commands, env);
        String singlecmd = Joiner.on(separator).join(allcmds);

        if (getOptionalVal(props, PROP_RUN_AS_ROOT)==Boolean.TRUE) {
            LOG.warn("Cannot run as root when executing as command; run as a script instead (will run as normal user): "+singlecmd);
        }
        
        if (LOG.isTraceEnabled()) LOG.trace("Running command at {}: {}", host, singlecmd);
        
        Command result = acquire(new ExecAction(singlecmd, out, err));
        if (LOG.isTraceEnabled()) LOG.trace("Running command at {} completed: exit code {}", host, result.getExitStatus());
        // can be null if no exit status is received (observed on kill `ps aux | grep thing-to-grep-for | awk {print $2}`
        if (result.getExitStatus()==null) LOG.warn("Null exit status running at {}: {}", host, singlecmd);
        
        return asInt(result.getExitStatus(), -1);
    }

    protected void checkConnected() {
        if (!isConnected()) {
            throw new IllegalStateException(String.format("(%s) ssh not connected!", toString()));
        }
    }

    protected void backoffForAttempt(int retryAttempt, String message) {
        backoffLimitedRetryHandler.imposeBackoffExponentialDelay(retryAttempt, message);
    }

    protected <T, C extends SshAction<T>> T acquire(C connection) {
        Stopwatch stopwatch = new Stopwatch().start();
        
        for (int i = 0; i < sshTries; i++) {
            try {
                connection.clear();
                if (LOG.isTraceEnabled()) LOG.trace(">> ({}) acquiring {}", toString(), connection);
                T returnVal = connection.create();
                if (LOG.isTraceEnabled()) LOG.trace("<< ({}) acquired {}", toString(), returnVal);
                return returnVal;
            } catch (Exception e) {
                // uninformative net.schmizz.sshj.connection.ConnectionException: 
                //    Request failed (reason=UNKNOWN) may mean remote Subsytem is disabled (e.g. for FTP)
                // if key is missing, get a UserAuth error
                String errorMessage = String.format("(%s) error acquiring %s", toString(), connection);
                String fullMessage = String.format("%s (attempt %s/%s, in time %s/%s)", 
                        errorMessage, (i+1), sshTries, Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS)), 
                        (sshTriesTimeout > 0 ? Time.makeTimeStringRounded(sshTriesTimeout) : "unlimited"));
                try {
                    disconnect();
                } catch (Exception e2) {
                    LOG.debug("<< ("+toString()+") error closing connection: "+e+" / "+e2, e);
                }
                if (i + 1 == sshTries) {
                    LOG.debug("<< {} (rethrowing, out of retries): {}", fullMessage, e.getMessage());
                    throw propagate(e, fullMessage + "; out of retries");
                } else if (sshTriesTimeout > 0 && stopwatch.elapsed(TimeUnit.MILLISECONDS) > sshTriesTimeout) {
                    LOG.debug("<< {} (rethrowing, out of time): {}", fullMessage, e.getMessage());
                    throw propagate(e, fullMessage + "; out of time");
                } else {
                    if (LOG.isDebugEnabled()) LOG.debug("<< {}: {}", fullMessage, e.getMessage());
                    backoffForAttempt(i + 1, errorMessage + ": " + e.getMessage());
                    if (connection != sshClientConnection)
                        connect();
                    continue;
                }
            }
        }
        assert false : "should not reach here";
        return null;
    }

    private final SshAction<SFTPClient> sftpConnection = new SshAction<SFTPClient>() {

        private SFTPClient sftp;

        @Override
        public void clear() {
            closeWhispering(sftp, this);
            sftp = null;
        }

        @Override
        public SFTPClient create() throws IOException {
            checkConnected();
            sftp = sshClientConnection.ssh.newSFTPClient();
            return sftp;
        }

        @Override
        public String toString() {
            return "SFTPClient()";
        }
    };

    private class GetFileAction implements SshAction<InputStream> {
        private final String path;
        private SFTPClient sftp;

        GetFileAction(String path) {
            this.path = checkNotNull(path, "path");
        }

        @Override
        public void clear() throws IOException {
            closeWhispering(sftp, this);
            sftp = null;
        }

        @Override
        public InputStream create() throws Exception {
            sftp = acquire(sftpConnection);
            return new CloseFtpChannelOnCloseInputStream(
                    sftp.getSFTPEngine().open(path).getInputStream(), sftp);
        }

        @Override
        public String toString() {
            return "Payload(path=[" + path + "])";
        }
    };

    private class PutFileAction implements SshAction<Void> {
        // TODO See SshJschTool.createFile: it does whacky stuff when copying; do we need that here as well?
        // TODO support backup as a property?
        
        private final String path;
        private SFTPClient sftp;
        private final int permissionsMask;
        private final long lastModificationDate;
        private final long lastAccessDate;
        private final Supplier<InputStream> contentsSupplier;
        private final Integer length;
        
        PutFileAction(Map<String,?> props, String path, Supplier<InputStream> contentsSupplier, long length) {
            String permissions = getOptionalVal(props, PROP_PERMISSIONS, "0644");
            long lastModificationDateVal = getOptionalVal(props, PROP_LAST_MODIFICATION_DATE, 0L);
            long lastAccessDateVal = getOptionalVal(props, PROP_LAST_ACCESS_DATE, 0L);
            if (lastAccessDateVal <= 0 ^ lastModificationDateVal <= 0) {
                lastAccessDateVal = Math.max(lastAccessDateVal, lastModificationDateVal);
                lastModificationDateVal = Math.max(lastAccessDateVal, lastModificationDateVal);
            }
            this.permissionsMask = Integer.parseInt(permissions, 8);
            this.lastAccessDate = lastAccessDateVal;
            this.lastModificationDate = lastModificationDateVal;
            this.path = checkNotNull(path, "path");
            this.contentsSupplier = checkNotNull(contentsSupplier, "contents");
            this.length = Ints.checkedCast(checkNotNull((long)length, "size"));
        }

        @Override
        public void clear() {
            closeWhispering(sftp, this);
            sftp = null;
        }

        @Override
        public Void create() throws Exception {
            final AtomicReference<InputStream> inputStreamRef = new AtomicReference<InputStream>();
            sftp = acquire(sftpConnection);
            try {
                sftp.put(new InMemorySourceFile() {
                    @Override public String getName() {
                        return path;
                    }
                    @Override public long getLength() {
                        return length;
                    }
                    @Override public InputStream getInputStream() throws IOException {
                        InputStream contents = contentsSupplier.get();
                        inputStreamRef.set(contents);
                        return contents;
                    }
                }, path);
                sftp.chmod(path, permissionsMask);
                if (lastAccessDate > 0) {
                    sftp.setattr(path, new FileAttributes.Builder()
                            .withAtimeMtime(lastAccessDate, lastModificationDate)
                            .build());
                }
            } finally {
                closeWhispering(inputStreamRef.get(), this);
            }
            return null;
        }

        @Override
        public String toString() {
            return "Put(path=[" + path + " "+length+"])";
        }
    };

    @VisibleForTesting
    Predicate<String> causalChainHasMessageContaining(final Exception from) {
        return new Predicate<String>() {

            @Override
            public boolean apply(final String input) {
                return any(getCausalChain(from), new Predicate<Throwable>() {

                    @Override
                    public boolean apply(Throwable arg0) {
                        return (arg0.toString().indexOf(input) != -1)
                                || (arg0.getMessage() != null && arg0.getMessage().indexOf(input) != -1);
                    }

                });
            }

        };
    }
    
    protected void allocatePTY(Session s) throws ConnectionException, TransportException {
        // this was set as the default, but it makes output harder to read
        // and causes stderr to be sent to stdout;
        // but some systems requiretty for sudoing
        if (allocatePTY)
            s.allocatePTY("vt100", 80, 24, 0, 0, Collections.<PTYMode, Integer> emptyMap());
//            s.allocatePTY("dumb", 80, 24, 0, 0, Collections.<PTYMode, Integer> emptyMap());
    }

    protected SshAction<Session> newSessionAction() {

        return new SshAction<Session>() {

            private Session session = null;

            @Override
            public void clear() throws TransportException, ConnectionException {
                closeWhispering(session, this);
                session = null;
            }

            @Override
            public Session create() throws Exception {
                checkConnected();
                session = sshClientConnection.ssh.startSession();
                allocatePTY(session);
                return session;
            }

            @Override
            public String toString() {
                return "Session()";
            }
        };

    }

    class ExecAction implements SshAction<Command> {
        private final String command;
        
        private Session session;
        private Shell shell;
        private StreamGobbler outgobbler;
        private StreamGobbler errgobbler;
        private OutputStream out;
        private OutputStream err;

        ExecAction(String command, OutputStream out, OutputStream err) {
            this.command = checkNotNull(command, "command");
            this.out = out;
            this.err = err;
        }

        @Override
        public void clear() throws TransportException, ConnectionException {
            closeWhispering(session, this);
            closeWhispering(shell, this);
            closeWhispering(outgobbler, this);
            closeWhispering(errgobbler, this);
            session = null;
            shell = null;
        }

        @Override
        public Command create() throws Exception {
            try {
                session = acquire(newSessionAction());
                
                Command output = session.exec(checkNotNull(command, "command"));
                
                if (out != null) {
                    outgobbler = new StreamGobbler(output.getInputStream(), out, (Logger)null);
                    outgobbler.start();
                }
                if (err != null) {
                    errgobbler = new StreamGobbler(output.getErrorStream(), err, (Logger)null);
                    errgobbler.start();
                }
                try {
                    output.join(sshClientConnection.getSessionTimeout(), TimeUnit.MILLISECONDS);
                    return output;
                    
                } finally {
                    // wait for all stdout/stderr to have been re-directed
                    try {
                        if (outgobbler != null) outgobbler.join();
                        if (errgobbler != null) errgobbler.join();
                    } catch (InterruptedException e) {
                        LOG.warn("Interrupted gobbling streams from ssh: "+command, e);
                        Thread.currentThread().interrupt();
                    }
                }
                
            } finally {
                clear();
            }
        }

        @Override
        public String toString() {
            return "Exec(command=[" + command + "])";
        }
    }

    class ShellAction implements SshAction<Integer> {
        private final List<String> commands;
        
        private Session session;
        private Shell shell;
        private StreamGobbler outgobbler;
        private StreamGobbler errgobbler;
        private OutputStream out;
        private OutputStream err;

        ShellAction(List<String> commands, OutputStream out, OutputStream err) {
            this.commands = checkNotNull(commands, "commands");
            this.out = out;
            this.err = err;
        }

        @Override
        public void clear() throws TransportException, ConnectionException {
            closeWhispering(session, this);
            closeWhispering(shell, this);
            closeWhispering(outgobbler, this);
            closeWhispering(errgobbler, this);
            session = null;
            shell = null;
        }

        @Override
        public Integer create() throws Exception {
            try {
                session = acquire(newSessionAction());
                
                shell = session.startShell();
                
                if (out != null) {
                    InputStream outstream = shell.getInputStream();
                    outgobbler = new StreamGobbler(outstream, out, (Logger)null);
                    outgobbler.start();
                }
                if (err != null) {
                    InputStream errstream = shell.getErrorStream();
                    errgobbler = new StreamGobbler(errstream, err, (Logger)null);
                    errgobbler.start();
                }
                
                OutputStream output = shell.getOutputStream();

                for (CharSequence cmd : commands) {
                    try {
                        output.write(Strings.toUTF8ByteArray(cmd+"\n"));
                        output.flush();
                    } catch (ConnectionException e) {
                        if (!shell.isOpen()) {
                            // shell is closed; presumably the user command did `exit`
                            if (LOG.isDebugEnabled()) LOG.debug("Shell closed to {} when executing {}", SshjTool.this.toString(), commands);
                            break;
                        } else {
                            throw e;
                        }
                    }
                }
                // workaround attempt for SSHJ deadlock - https://github.com/shikhar/sshj/issues/105
                synchronized (shell.getOutputStream()) {
                    shell.sendEOF();
                }
                closeWhispering(output, this);
                
                try {
                    int timeout = sshClientConnection.getSessionTimeout();
                    long timeoutEnd = System.currentTimeMillis() + timeout;
                    Exception last = null;
                    do {
                        if (!shell.isOpen() && ((SessionChannel)session).getExitStatus()!=null)
                            // shell closed, and exit status returned
                            break;
                        boolean endBecauseReturned =
                            // if either condition is satisfied, then wait 1s in hopes the other does, then return
                            (!shell.isOpen() || ((SessionChannel)session).getExitStatus()!=null);
                        try {
                            shell.join(1000, TimeUnit.MILLISECONDS);
                        } catch (ConnectionException e) { last = e; }
                        if (endBecauseReturned)
                            // shell is still open, ie some process is running
                            // but we have a result code, so main shell is finished
                            // we waited one second extra to allow any background process 
                            // which is nohupped to really be in the background (#162)
                            // now let's bail out
                            break;
                    } while (timeout<=0 || System.currentTimeMillis() < timeoutEnd);
                    if (shell.isOpen() && ((SessionChannel)session).getExitStatus()==null) {
                        LOG.debug("Timeout ({}) in SSH shell to {}", sshClientConnection.getSessionTimeout(), this);
                        // we timed out, or other problem -- reproduce the error
                        throw last;
                    }
                    return ((SessionChannel)session).getExitStatus();
                } finally {
                    // wait for all stdout/stderr to have been re-directed
                    closeWhispering(shell, this);
                    shell = null;
                    try {
                        if (outgobbler != null) outgobbler.join();
                        if (errgobbler != null) errgobbler.join();
                    } catch (InterruptedException e) {
                        LOG.warn("Interrupted gobbling streams from ssh: "+commands, e);
                        Thread.currentThread().interrupt();
                    }
                }
                
            } finally {
                clear();
            }
        }

        @Override
        public String toString() {
            return "Shell(command=[" + commands + "])";
        }
    }

    private Supplier<InputStream> newInputStreamSupplier(final byte[] contents) {
        return new Supplier<InputStream>() {
            @Override public InputStream get() {
                return new ByteArrayInputStream(contents);
            }
        };
    }

    private Supplier<InputStream> newInputStreamSupplier(final File file) {
        return new Supplier<InputStream>() {
            @Override public InputStream get() {
                try {
                    return new FileInputStream(file);
                } catch (FileNotFoundException e) {
                    throw Exceptions.propagate(e);
                }
            }
        };
    }

//    protected Payload toPayload(byte[] input) {
//        return new ByteArrayPayload(input);
//    }
//    
//    protected Payload toPayload(File input) {
//        return new FilePayload(input);
//    }
//    
//    protected Payload toPayload(InputStream input, long length) {
//        InputStreamPayload payload = new InputStreamPayload(new LimitInputStream(input, length));
//        payload.getContentMetadata().setContentLength(length);
//        return payload;
//    }
}

/**
 * Licensed to jclouds, Inc. (jclouds) under one or more
 * contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  jclouds licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.util.internal.ssh;

import static brooklyn.util.NetworkUtils.checkPortValid;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.getCausalChain;
import static com.google.common.collect.Iterables.any;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
import org.jclouds.io.InputSuppliers;
import org.jclouds.io.Payload;
import org.jclouds.io.Payloads;
import org.jclouds.io.payloads.ByteArrayPayload;
import org.jclouds.io.payloads.FilePayload;
import org.jclouds.io.payloads.InputStreamPayload;
import org.jclouds.io.payloads.StringPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.IdGenerator;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.internal.SshTool;
import brooklyn.util.internal.StreamGobbler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.LimitInputStream;
import com.google.common.net.HostAndPort;

/**
 * For ssh and scp-style commands, using the sshj library.
 * <p>
 * The implementation is based on a combination of the existing brooklyn SshJschTool,
 * and the jclouds SshjSshClient.
 * <p>
 * Not thread-safe. Use a different SshjTool for each concurrent thread. 
 * If passing from one thread to another, ensure code goes through a synchronized block.
 */
public class SshjTool implements SshTool {

    private static final Logger LOG = LoggerFactory.getLogger(SshjTool.class);

    private class CloseFtpChannelOnCloseInputStream extends ProxyInputStream {

        private final SFTPClient sftp;

        private CloseFtpChannelOnCloseInputStream(InputStream proxy, SFTPClient sftp) {
            super(proxy);
            this.sftp = sftp;
        }

        @Override
        public void close() throws IOException {
            super.close();
            if (sftp != null)
                sftp.close();
        }
    }

    public static interface SshAction<T> {
        void clear() throws Exception;

        T create() throws Exception;
    }


    private final String toString;

    private final int sshTries;

    private final SshjClientConnection sshClientConnection;

    private final BackoffLimitedRetryHandler backoffLimitedRetryHandler;

    private final String host;
    private final String user;
    private final String password;
    private final int port;
    private String privateKey;
    private File privateKeyFile;
    private boolean strictHostKeyChecking;

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String host;
        private String user = System.getProperty("user.name");
        private String password;
        private int port = 22;
        private String privateKey;
        private Set<String> privateKeyFiles = Sets.newLinkedHashSet();
        private boolean strictHostKeyChecking = false;
        private int connectTimeout;
        private int sessionTimeout;
        private int sshTries = 1;
        private long sshRetryDelay = 50L;
        
        @SuppressWarnings("unchecked")
        public Builder from(Map<String,?> props) {
            host = getMandatoryVal(props, "host", String.class);
            port = getOptionalVal(props, "port", Integer.class, port);
            user = getOptionalVal(props, "user", String.class, user);
            password = getOptionalVal(props, "password", String.class, password);
            strictHostKeyChecking = getOptionalVal(props, "strictHostKeyChecking", Boolean.class, strictHostKeyChecking);
            connectTimeout = getOptionalVal(props, "connectTimeout", Integer.class, connectTimeout);
            sessionTimeout = getOptionalVal(props, "sessionTimeout", Integer.class, sessionTimeout);
            sshTries = getOptionalVal(props, "sshTries", Integer.class, sshTries);
            sshRetryDelay = getOptionalVal(props, "sshRetryDelay", Long.class, sshRetryDelay);

            privateKey = getOptionalVal(props, "privateKey", String.class, privateKey);

            // for backwards compatibility accept keyFiles and privateKey
            // but sshj accepts only a single privateKeyFile; leave blank to use defaults (i.e. ~/.ssh/id_rsa and id_dsa)
            privateKeyFiles.addAll(getOptionalVal(props, "keyFiles", List.class, Collections.emptyList()));
            String privateKeyFile = getOptionalVal(props, "privateKeyFile", String.class, null);
            if (privateKeyFile != null) privateKeyFiles.add(privateKeyFile);

            return this;
        }
        public Builder host(String val) {
            this.host = val; return this;
        }
        public Builder user(String val) {
            this.user = val; return this;
        }
        public Builder password(String val) {
            this.password = val; return this;
        }
        public Builder port(int val) {
            this.port = val; return this;
        }
        public Builder privateKey(String val) {
            this.privateKey = val; return this;
        }
        public Builder privateKeyFile(String val) {
            this.privateKeyFiles.add(val); return this;
        }
        public Builder connectTimeout(int val) {
            this.connectTimeout = val; return this;
        }
        public Builder sessionTimeout(int val) {
            this.sessionTimeout = val; return this;
        }
        public Builder sshRetries(int val) {
            this.sshTries = val; return this;
        }
        public Builder sshRetryDelay(long val) {
            this.sshRetryDelay = val; return this;
        }
        public SshjTool build() {
            return new SshjTool(this);
        }
    }

    public SshjTool(Map<String,?> map) {
        this(builder().from(map));
    }
    
    private SshjTool(Builder builder) {
        // TODO Does this need to be ported from SshJschTool?
//        if (host && host==~ /[^@]+@[^@]+/) {
//            (user,host) = (host=~/([^@]+)@([^@]+)/)[0][1,2]
//        }

        host = checkNotNull(builder.host, "host");
        port = builder.port;
        user = builder.user;
        password = builder.password;
        strictHostKeyChecking = builder.strictHostKeyChecking;
        sshTries = builder.sshTries ;
        backoffLimitedRetryHandler = new BackoffLimitedRetryHandler(sshTries, builder.sshRetryDelay);
        privateKey = builder.privateKey;
        
        if (builder.privateKeyFiles.size() > 1) {
            throw new IllegalArgumentException("sshj supports only a single private key-file; " +
                    "for defaults of ~/.ssh/id_rsa and ~/.ssh/id_dsa leave blank");
        } else if (builder.privateKeyFiles.size() == 1) {
            String privateKeyFileStr = Iterables.get(builder.privateKeyFiles, 0);
            String ammendedKeyFile = privateKeyFileStr.startsWith("~") ? (System.getProperty("user.home")+privateKeyFileStr.substring(1)) : privateKeyFileStr;
            privateKeyFile = new File(ammendedKeyFile);
        } else {
            privateKeyFile = null;
        }
        
        checkArgument(host.length() > 0, "host value must not be an empty string");
        checkPortValid(port, "ssh port");

        toString = String.format("%s@%s:%d", user, host, port);

        sshClientConnection = SshjClientConnection.builder()
                .hostAndPort(HostAndPort.fromParts(host, port))
                .username(user)
                .password(password)
                .privateKey(privateKey)
                .privateKeyFile(privateKeyFile)
                .strictHostKeyChecking(strictHostKeyChecking)
                .connectTimeout(builder.connectTimeout)
                .sessionTimeout(builder.sessionTimeout)
                .build();
    }
    
    public String getHostAddress() {
        return this.host;
    }

    public String getUsername() {
        return this.user;
    }

    @Override
    public void connect() {
        try {
            acquire(sshClientConnection);
        } catch (Exception e) {
            throw propagate(e, "failed to connect");
        }
    }

    @Override
    public void connect(int maxAttempts) {
        connect(); // FIXME Should callers instead configure sshTries? But that would apply to all ssh attempts
    }

    @Override
    public void disconnect() {
        if (LOG.isDebugEnabled()) LOG.debug("Disconnecting {}", toString());
        try {
            sshClientConnection.clear();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public boolean isConnected() {
        return sshClientConnection.isConnected();
    }
    
    @Override
    public int transferFileTo(Map<String,?> props, InputStream input, String pathAndFileOnRemoteServer) {
        return createFile(props, pathAndFileOnRemoteServer, toPayload(input));
    }
    
    @Override
    public int createFile(Map<String,?> props, String pathAndFileOnRemoteServer, InputStream input, long size) {
        return createFile(props, pathAndFileOnRemoteServer, toPayload(input, size));
    }

    /**
     * Creates the given file with the given contents.
     *
     * Permissions specified using 'permissions:0755'.
     */
    @Override
    public int createFile(Map<String,?> props, String pathAndFileOnRemoteServer, String contents) {
        return createFile(props, pathAndFileOnRemoteServer, new StringPayload(contents));
    }

    /** Creates the given file with the given contents.
     *
     * Permissions specified using 'permissions:0755'.
     */
    @Override
    public int createFile(Map<String,?> props, String pathAndFileOnRemoteServer, byte[] contents) {
        return createFile(props, pathAndFileOnRemoteServer, new ByteArrayPayload(contents));
    }

    @Override
    public int copyToServer(Map<String,?> props, File f, String pathAndFileOnRemoteServer) {
        return createFile(props, pathAndFileOnRemoteServer, new FilePayload(f));
    }

    @Override
    public int transferFileFrom(Map<String,?> props, String pathAndFileOnRemoteServer, String pathAndFileOnLocalServer) {
        Payload payload = acquire(new GetFileAction(pathAndFileOnRemoteServer));
        try {
            Files.copy(InputSuppliers.of(payload.getInput()), new File(pathAndFileOnLocalServer));
            return 0; // TODO Can we assume put will have thrown exception if failed? Rather than exit code != 0?
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private int createFile(Map<String,?> props, String pathAndFileOnRemoteServer, Payload payload) {
        acquire(new PutFileAction(props, pathAndFileOnRemoteServer, payload));
        return 0; // TODO Can we assume put will have thrown exception if failed? Rather than exit code != 0?
    }

    @Override
    public int execShell(Map<String,?> props, List<String> commands) {
        return execShell(props, commands, Collections.<String,Object>emptyMap());
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
    public int execShell(Map<String,?> props, List<String> commands, Map<String,?> env) {
        OutputStream out = getOptionalVal(props, "out", OutputStream.class, null);
        OutputStream err = getOptionalVal(props, "err", OutputStream.class, null);
        String scriptDir = getOptionalVal(props, "scriptDir", String.class, "/tmp");
        String scriptPath = scriptDir+"/brooklyn-"+System.currentTimeMillis()+"-"+IdGenerator.makeRandomId(8)+".sh";
        
        String scriptContents = toScript(commands, env);
        
        if (LOG.isTraceEnabled()) LOG.trace("Running shell command as script {}", scriptContents);
        
        createFile(ImmutableMap.of("permissions", "0700"), scriptPath, scriptContents);
        
        List<String> cmds = ImmutableList.of(
                scriptPath,
                "RESULT=$?",
                "rm -f "+scriptPath, // use "-f" because some systems have "rm" aliased to "rm -i"
                "exit $RESULT");
        
        Integer result = acquire(new ShellAction(cmds, out, err));
        return result != null ? result : -1;
    }

    public int execShellDirect(Map<String,?> props, List<String> commands, Map<String,?> env) {
        OutputStream out = getOptionalVal(props, "out", OutputStream.class, null);
        OutputStream err = getOptionalVal(props, "err", OutputStream.class, null);
        
        List<String> cmdSequence = toCommandSequence(commands, env);
        List<String> allcmds = ImmutableList.<String>builder()
                .add("exec bash -e")
                .addAll(cmdSequence)
                .add("exit $?")
                .build();
        
        if (LOG.isTraceEnabled()) LOG.trace("Running shell command {}", allcmds);
        
        Integer result = acquire(new ShellAction(allcmds, out, err));
        return result != null ? result : -1;
    }

    @Override
    public int execCommands(Map<String,?> props, List<String> commands) {
        return execCommands(props, commands, Collections.<String,Object>emptyMap());
    }

    @Override
    public int execCommands(Map<String,?> props, List<String> commands, Map<String,?> env) {
        if (props.containsKey("blocks") && props.get("blocks") == Boolean.FALSE) {
            throw new IllegalArgumentException("Cannot exec non-blocking: command="+commands);
        }
        OutputStream out = getOptionalVal(props, "out", OutputStream.class, null);
        OutputStream err = getOptionalVal(props, "err", OutputStream.class, null);
        String separator = getOptionalVal(props, "separator", String.class, "; ");

        List<String> allcmds = toCommandSequence(commands, env);
        String singlecmd = Joiner.on(separator).join(allcmds);

        if (LOG.isTraceEnabled()) LOG.trace("Running command {}", singlecmd);
        
        Command result = acquire(new ExecAction(singlecmd, out, err));
        return result.getExitStatus();
    }

    private String toScript(List<String> commands, Map<String,?> env) {
        List<String> allcmds = toCommandSequence(commands, env);
        
        StringBuilder result = new StringBuilder();
        result.append("#!/bin/bash -e"+"\n");
        
        for (String cmd : allcmds) {
            result.append(cmd+"\n");
        }
        
        return result.toString();
    }

    /**
     * Merges the commands and env, into a single set of commands. Also escapes the commands as required.
     * 
     * Not all ssh servers handle "env", so instead convert env into exported variables
     */
    private List<String> toCommandSequence(List<String> commands, Map<String,?> env) {
        List<String> result = new ArrayList<String>(env.size()+commands.size());
        
        for (Entry<String,?> entry : env.entrySet()) {
            String escapedVal = entry.getValue().toString().replaceAll("\\\"", "\\\\\\\"");
            result.add("export "+entry.getKey()+"=\""+escapedVal+"\"");
        }
        
        for (CharSequence cmd : commands) { // objects in commands can be groovy GString so can't treat as String here
            result.add(cmd.toString());
        }

        return result;
    }

    private void checkConnected() {
        if (!isConnected()) {
            throw new IllegalStateException(String.format("(%s) ssh not connected!", toString()));
        }
    }

    private void backoffForAttempt(int retryAttempt, String message) {
        backoffLimitedRetryHandler.imposeBackoffExponentialDelay(200L, 2, retryAttempt, sshTries, message);
    }

    protected <T, C extends SshAction<T>> T acquire(C connection) {
        String errorMessage = String.format("(%s) error acquiring %s", toString(), connection);
        for (int i = 0; i < sshTries; i++) {
            try {
                connection.clear();
                if (LOG.isDebugEnabled()) LOG.debug(">> ({}) acquiring {}", toString(), connection);
                T returnVal = connection.create();
                if (LOG.isTraceEnabled()) LOG.trace("<< ({}) acquired {}", toString(), returnVal);
                return returnVal;
            } catch (Exception from) {
                try {
                    disconnect();
                } catch (Exception e1) {
                    LOG.warn("<< ("+toString()+") error closing connection", from);
                }
                if (i + 1 == sshTries) {
                    throw propagate(from, errorMessage + " (out of retries - max " + sshTries + ")");
                } else {
                    LOG.info("<< " + errorMessage + " (attempt " + (i + 1) + " of " + sshTries + "): " + from.getMessage());
                    backoffForAttempt(i + 1, errorMessage + ": " + from.getMessage());
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
            if (sftp != null)
                try {
                    sftp.close();
                } catch (IOException e) {
                    Throwables.propagate(e);
                }
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

    private class GetFileAction implements SshAction<Payload> {
        private final String path;
        private SFTPClient sftp;

        GetFileAction(String path) {
            this.path = checkNotNull(path, "path");
        }

        @Override
        public void clear() throws IOException {
            if (sftp != null)
                sftp.close();
        }

        @Override
        public Payload create() throws Exception {
            sftp = acquire(sftpConnection);
            return Payloads.newInputStreamPayload(new CloseFtpChannelOnCloseInputStream(
                    sftp.getSFTPEngine().open(path).getInputStream(), sftp));
        }

        @Override
        public String toString() {
            return "Payload(path=[" + path + "])";
        }
    };

    private class PutFileAction implements SshAction<Void> {
        // TODO See SshJschTool.createFile: it does whacky stuff when copying; do we need that here as well?
        
        private final String path;
        private final Payload contents;
        private SFTPClient sftp;
        private int permissionsMask;
        private long lastModificationDate;
        private long lastAccessDate;
        
        PutFileAction(Map<String,?> props, String path, Payload contents) {
            String permissions = getOptionalVal(props, "permissions", String.class, "0644");
            permissionsMask = Integer.parseInt(permissions, 8);
            lastModificationDate = getOptionalVal(props, "lastModificationDate", Long.class, 0L);
            lastAccessDate = getOptionalVal(props, "lastAccessDate", Long.class, 0L);
            if (lastAccessDate <= 0 ^ lastModificationDate <= 0) {
                lastAccessDate = Math.max(lastAccessDate, lastModificationDate);
                lastModificationDate = Math.max(lastAccessDate, lastModificationDate);
            }
            this.path = checkNotNull(path, "path");
            this.contents = checkNotNull(contents, "contents");
        }

        @Override
        public void clear() {
            if (sftp != null)
                try {
                    sftp.close();
                } catch (IOException e) {
                    Throwables.propagate(e);
                }
        }

        @Override
        public Void create() throws Exception {
            sftp = acquire(sftpConnection);
            try {
                sftp.put(new InMemorySourceFile() {
                    @Override public String getName() {
                        return path;
                    }
                    @Override public long getLength() {
                        return contents.getContentMetadata().getContentLength();
                    }
                    @Override public InputStream getInputStream() throws IOException {
                        return checkNotNull(contents.getInput(), "inputstream for path %s", path);
                    }
                }, path);
                sftp.chmod(path, permissionsMask);
                if (lastAccessDate > 0) {
                    sftp.setattr(path, new FileAttributes.Builder()
                            .withAtimeMtime(lastAccessDate, lastModificationDate)
                            .build());
                }
            } finally {
                contents.release();
            }
            return null;
        }

        @Override
        public String toString() {
            return "Put(path=[" + path + "])";
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

    private SshException propagate(Exception e, String message) throws SshException {
        message += ": " + e.getMessage();
        LOG.error("<< " + message, e);
        throw new SshException("(" + toString() + ") " + message, e);
    }

    @Override
    public String toString() {
        return toString;
    }

    protected SshAction<Session> newSessionAction() {

        return new SshAction<Session>() {

            private Session session = null;

            @Override
            public void clear() throws TransportException, ConnectionException {
                if (session != null)
                    session.close();
            }

            @Override
            public Session create() throws Exception {
                checkConnected();
                session = sshClientConnection.ssh.startSession();
                session.allocatePTY("vt100", 80, 24, 0, 0, Collections.<PTYMode, Integer> emptyMap());
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
            if (session != null) {
                session.close();
            }
            if (shell != null) {
                shell.close();
            }
            if (outgobbler != null) {
                outgobbler.shutdown();
            }
            if (errgobbler != null) {
                errgobbler.shutdown();
            }
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
            if (session != null) {
                session.close();
            }
            if (shell != null) {
                shell.close();
            }
            if (outgobbler != null) {
                outgobbler.shutdown();
            }
            if (errgobbler != null) {
                errgobbler.shutdown();
            }
        }

        @Override
        public Integer create() throws Exception {
            try {
                session = acquire(newSessionAction());
                
                shell = session.startShell();
                
                if (out != null) {
                    InputStream outstream = shell.getInputStream();
                    outgobbler = new StreamGobbler(outstream, out, LOG);//(Logger)null);
                    outgobbler.start();
                }
                if (out != null) {
                    InputStream errstream = shell.getErrorStream();
                    errgobbler = new StreamGobbler(errstream, err, LOG);//(Logger)null);
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
                            LOG.debug("Shell closed to {} when executing {}", SshjTool.this.toString(), commands);
                            break;
                        } else {
                            throw e;
                        }
                    }
                }
                shell.sendEOF();
                
                try {
                    shell.join(sshClientConnection.getSessionTimeout(), TimeUnit.MILLISECONDS);
                    return ((SessionChannel)session).getExitStatus();
                    
                } finally {
                    // wait for all stdout/stderr to have been re-directed
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

    private Payload toPayload(InputStream input, long length) {
        InputStreamPayload payload = new InputStreamPayload(new LimitInputStream(input, length));
        payload.getContentMetadata().setContentLength(length);
        return payload;
    }
    
    private Payload toPayload(InputStream input) {
        /*
         * TODO sshj needs to know the length of the InputStream to copy the file:
         *   java.lang.NullPointerException
         *     at brooklyn.util.internal.ssh.SshjTool$PutFileAction$1.getLength(SshjTool.java:574)
         *     at net.schmizz.sshj.sftp.SFTPFileTransfer$Uploader.upload(SFTPFileTransfer.java:174)
         *     at net.schmizz.sshj.sftp.SFTPFileTransfer$Uploader.access$100(SFTPFileTransfer.java:162)
         *     at net.schmizz.sshj.sftp.SFTPFileTransfer.upload(SFTPFileTransfer.java:61)
         *     at net.schmizz.sshj.sftp.SFTPClient.put(SFTPClient.java:248)
         *     at brooklyn.util.internal.ssh.SshjTool$PutFileAction.create(SshjTool.java:569)
         * 
         * Unfortunately that requires consuming the input stream to find out! We can't just do:
         *   new InputStreamPayload(input)
         * 
         * This is nasty: we have to hold the entire file in-memory.
         * It's worth a look at changing sshj to not need the length, if possible.
         */
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ByteStreams.copy(input, byteArrayOutputStream);
            return new ByteArrayPayload(byteArrayOutputStream.toByteArray());
        } catch (IOException e) {
            LOG.warn("Error consuming stream", e);
            throw Throwables.propagate(e);
        }
    }
    
    private static <T> T getMandatoryVal(Map<String,?> map, String key, Class<T> clazz) {
        checkArgument(map.containsKey(key), "must contain key '"+key+"'");
        return TypeCoercions.coerce(map.get(key), clazz);
    }
    
    private static <T> T getOptionalVal(Map<String,?> map, String key, Class<T> clazz, T defaultVal) {
        if (map.containsKey(key)) {
            return TypeCoercions.coerce(map.get(key), clazz);
        } else {
            return defaultVal;
        }
    }
}

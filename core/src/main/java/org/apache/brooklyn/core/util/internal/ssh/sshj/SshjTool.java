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
package org.apache.brooklyn.core.util.internal.ssh.sshj;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.getCausalChain;
import static com.google.common.collect.Iterables.any;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

import org.apache.brooklyn.core.internal.BrooklynFeatureEnablement;
import org.apache.brooklyn.core.util.internal.ssh.BackoffLimitedRetryHandler;
import org.apache.brooklyn.core.util.internal.ssh.ShellTool;
import org.apache.brooklyn.core.util.internal.ssh.SshAbstractTool;
import org.apache.brooklyn.core.util.internal.ssh.SshTool;
import org.apache.commons.io.input.ProxyInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.RuntimeTimeoutException;
import brooklyn.util.io.FileUtil;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.stream.KnownSizeInputStream;
import brooklyn.util.stream.StreamGobbler;
import brooklyn.util.stream.Streams;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CountingOutputStream;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.Ints;

/**
 * For ssh and scp-style commands, using the sshj library.
 */
public class SshjTool extends SshAbstractTool implements SshTool {

    /*
     * TODO synchronization of connect/disconnect needs revisited!
     * Saw SshjToolIntegrationTest.testExecBigConcurrentCommand fail with:
     *     Caused by: java.lang.AssertionError
     *         at net.schmizz.sshj.SSHClient.auth(SSHClient.java:204)
     * i.e. another thread had called disconnect just before the failing thread
     * did SSHClient.auth.
     * Having multiple threads call connect/disconnect is going to be brittle. With
     * our retries we can get away with it usually, but it's not good!
     *
     * TODO need to upgrade sshj version from 0.8.1 to 0.9, but jclouds 1.7.2 still 
     * relies on 0.8.1. In 0.9, it fixes the https://github.com/shikhar/sshj/issues/89
     * so does not throw AssertionError.
     */

    private static final Logger LOG = LoggerFactory.getLogger(SshjTool.class);

    protected final int sshTries;
    protected final long sshTriesTimeout;
    protected final BackoffLimitedRetryHandler backoffLimitedRetryHandler;

    /** Terminal type name for {@code allocatePTY} option. */
    final static String TERM = "vt100"; // "dumb"
    
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
        protected long connectTimeout;
        protected long sessionTimeout;
        protected int sshTries = 4;  //allow 4 tries by default, much safer
        protected long sshTriesTimeout = 2*60*1000;  //allow 2 minutes by default (so if too slow trying sshTries times, abort anyway)
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
    @Deprecated // see super
    public void connect(int maxAttempts) {
        connect(); // FIXME Should callers instead configure sshTries? But that would apply to all ssh attempts
    }

    @Override
    public void disconnect() {
        if (LOG.isTraceEnabled()) LOG.trace("Disconnecting SshjTool {} ({})", this, System.identityHashCode(this));
        try {
            Stopwatch perfStopwatch = Stopwatch.createStarted();
            sshClientConnection.clear();
            if (LOG.isTraceEnabled()) LOG.trace("SSH Performance: {} disconnect took {}", sshClientConnection.getHostAndPort(), Time.makeTimeStringRounded(perfStopwatch));
        } catch (Exception e) {
            throw Exceptions.propagate(e);
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
            FileUtil.copyTo(contents, localFile);
            return 0; // TODO Can we assume put will have thrown exception if failed? Rather than exit code != 0?
        } finally {
            Streams.closeQuietly(contents);
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
     * 
     * Another consideration is long-running scripts. On some clouds when executing a script that takes 
     * several minutes, we have seen it fail with -1 (e.g. 1 in 20 times). This suggests the ssh connection
     * is being dropped. To avoid this problem, we can execute the script asynchronously, writing to files
     * the stdout/stderr/pid/exitStatus. We then periodically poll to retrieve the contents of these files.
     * Use {@link #PROP_EXEC_ASYNC} to force this mode of execution.
     */
    @Override
    public int execScript(final Map<String,?> props, final List<String> commands, final Map<String,?> env) {
        Boolean execAsync = getOptionalVal(props, PROP_EXEC_ASYNC);
        if (Boolean.TRUE.equals(execAsync) && BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_SSH_ASYNC_EXEC)) {
            return execScriptAsyncAndPoll(props, commands, env);
        } else {
            if (Boolean.TRUE.equals(execAsync)) {
                if (LOG.isDebugEnabled()) LOG.debug("Ignoring ssh exec-async configuration, because feature is disabled");
            }
            return new ToolAbstractExecScript(props) {
                public int run() {
                    String scriptContents = toScript(props, commands, env);
                    if (LOG.isTraceEnabled()) LOG.trace("Running shell command at {} as script: {}", host, scriptContents);
                    copyToServer(ImmutableMap.of("permissions", "0700"), scriptContents.getBytes(), scriptPath);
                    return asInt(acquire(new ShellAction(buildRunScriptCommand(), out, err, execTimeout)), -1);
                }
            }.run();
        }
    }

    /**
     * Executes the script in the background (`nohup ... &`), and then executes other ssh commands to poll for the
     * stdout, stderr and exit code of that original process (which will each have been written to separate files).
     * 
     * The polling is a "long poll". That is, it executes a long-running ssh command to retrieve the stdout, etc.
     * If that long-poll command fails, then we just execute another one to pick up from where it left off.
     * This means we do not need to execute many ssh commands (which are expensive), but can still return promptly
     * when the command completes.
     * 
     * Much of this was motivated by https://issues.apache.org/jira/browse/BROOKLYN-106, which is no longer
     * an issue. The retries (e.g. in the upload-script) are arguably overkill given that {@link #acquire(SshAction)}
     * will already retry. However, leaving this in place as it could prove useful when working with flakey
     * networks in the future.
     * 
     * TODO There are (probably) issues with this method when using {@link ShellTool#PROP_RUN_AS_ROOT}.
     * I (Aled) saw the .pid file having an owner of root:root, and a failure message in stderr of:
     *   -bash: line 3: /tmp/brooklyn-20150113-161203056-XMEo-move_install_dir_from_user_to_.pid: Permission denied
     */
    protected int execScriptAsyncAndPoll(final Map<String,?> props, final List<String> commands, final Map<String,?> env) {
        return new ToolAbstractAsyncExecScript(props) {
            private int maxConsecutiveSshFailures = 3;
            private Duration maxDelayBetweenPolls = Duration.seconds(20);
            private Duration pollTimeout = getOptionalVal(props, PROP_EXEC_ASYNC_POLLING_TIMEOUT, Duration.FIVE_MINUTES);
            private int iteration = 0;
            private int consecutiveSshFailures = 0;
            private int stdoutCount = 0;
            private int stderrCount = 0;
            private Stopwatch timer;
            
            public int run() {
                timer = Stopwatch.createStarted();
                final String scriptContents = toScript(props, commands, env);
                if (LOG.isTraceEnabled()) LOG.trace("Running shell command at {} as async script: {}", host, scriptContents);
                
                // Upload script; try repeatedly because have seen timeout intermittently on vcloud-director (BROOKLYN-106 related).
                boolean uploadSuccess = Repeater.create("async script upload on "+SshjTool.this.toString()+" (for "+getSummary()+")")
                        .backoffTo(maxDelayBetweenPolls)
                        .limitIterationsTo(3)
                        .rethrowException()
                        .until(new Callable<Boolean>() {
                            @Override
                            public Boolean call() throws Exception {
                                iteration++;
                                if (LOG.isDebugEnabled()) {
                                    String msg = "Uploading (iteration="+iteration+") for async script on "+SshjTool.this.toString()+" (for "+getSummary()+")";
                                    if (iteration == 1) {
                                        LOG.trace(msg);
                                    } else {
                                        LOG.debug(msg);
                                    }
                                }
                                copyToServer(ImmutableMap.of("permissions", "0700"), scriptContents.getBytes(), scriptPath);
                                return true;
                            }})
                        .run();
                
                if (!uploadSuccess) {
                    // Unexpected! Should have either returned true or have rethrown the exception; should never get false.
                    String msg = "Unexpected state: repeated failure for async script upload on "+SshjTool.this.toString()+" ("+getSummary()+")";
                    LOG.warn(msg+"; rethrowing");
                    throw new IllegalStateException(msg);
                }
                
                // Execute script asynchronously
                int execResult = asInt(acquire(new ShellAction(buildRunScriptCommand(), out, err, execTimeout)), -1);
                if (execResult != 0) return execResult;

                // Long polling to get the status
                try {
                    final AtomicReference<Integer> result = new AtomicReference<Integer>();
                    boolean success = Repeater.create("async script long-poll on "+SshjTool.this.toString()+" (for "+getSummary()+")")
                            .backoffTo(maxDelayBetweenPolls)
                            .limitTimeTo(execTimeout)
                            .until(new Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                    iteration++;
                                    if (LOG.isDebugEnabled()) LOG.debug("Doing long-poll (iteration="+iteration+") for async script to complete on "+SshjTool.this.toString()+" (for "+getSummary()+")");
                                    Integer exitstatus = longPoll();
                                    result.set(exitstatus);
                                    return exitstatus != null;
                                }})
                            .run();
                    
                    if (!success) {
                        // Timed out
                        String msg = "Timeout for async script to complete on "+SshjTool.this.toString()+" ("+getSummary()+")";
                        LOG.warn(msg+"; rethrowing");
                        throw new TimeoutException(msg);
                    }
                    
                    return result.get();
                    
                } catch (Exception e) {
                    LOG.debug("Problem polling for async script on "+SshjTool.this.toString()+" (for "+getSummary()+"); rethrowing after deleting temporary files", e);
                    throw Exceptions.propagate(e);
                } finally {
                    // Delete the temporary files created (and the `tail -c` commands that might have been left behind by long-polls).
                    // Using pollTimeout so doesn't wait forever, but waits for a reasonable (configurable) length of time.
                    // TODO also execute this if the `buildRunScriptCommand` fails, as that might have left files behind?
                    try {
                        int execDeleteResult = asInt(acquire(new ShellAction(deleteTemporaryFilesCommand(), out, err, pollTimeout)), -1);
                        if (execDeleteResult != 0) {
                            LOG.debug("Problem deleting temporary files of async script on "+SshjTool.this.toString()+" (for "+getSummary()+"): exit status "+execDeleteResult);
                        }
                    } catch (Exception e) {
                        Exceptions.propagateIfFatal(e);
                        LOG.debug("Problem deleting temporary files of async script on "+SshjTool.this.toString()+" (for "+getSummary()+"); continuing", e);
                    }
                }
            }
            
            Integer longPoll() throws IOException {
                // Long-polling to get stdout, stderr + exit status of async task.
                // If our long-poll disconnects, we will just re-execute.
                // We wrap the stdout/stderr so that we can get the size count. 
                // If we disconnect, we will pick up from that char of the stream.
                // TODO Additional stdout/stderr written by buildLongPollCommand() could interfere, 
                //      causing us to miss some characters.
                Duration nextPollTimeout = Duration.min(pollTimeout, Duration.millis(execTimeout.toMilliseconds()-timer.elapsed(TimeUnit.MILLISECONDS)));
                CountingOutputStream countingOut = (out == null) ? null : new CountingOutputStream(out);
                CountingOutputStream countingErr = (err == null) ? null : new CountingOutputStream(err);
                List<String> pollCommand = buildLongPollCommand(stdoutCount, stderrCount, nextPollTimeout);
                Duration sshJoinTimeout = nextPollTimeout.add(Duration.TEN_SECONDS);
                ShellAction action = new ShellAction(pollCommand, countingOut, countingErr, sshJoinTimeout);
                
                int longPollResult;
                try {
                    longPollResult = asInt(acquire(action, 3, nextPollTimeout), -1);
                } catch (RuntimeTimeoutException e) {
                    if (LOG.isDebugEnabled()) LOG.debug("Long-poll timed out on "+SshjTool.this.toString()+" (for "+getSummary()+"): "+e);
                    return null;
                }
                stdoutCount += (countingOut == null) ? 0 : countingOut.getCount();
                stderrCount += (countingErr == null) ? 0 : countingErr.getCount();
                
                if (longPollResult == 0) {
                    if (LOG.isDebugEnabled()) LOG.debug("Long-poll succeeded (exit status 0) on "+SshjTool.this.toString()+" (for "+getSummary()+")");
                    return longPollResult; // success
                    
                } else if (longPollResult == -1) {
                    // probably a connection failure; try again
                    if (LOG.isDebugEnabled()) LOG.debug("Long-poll received exit status -1; will retry on "+SshjTool.this.toString()+" (for "+getSummary()+")");
                    return null;

                } else if (longPollResult == 125) {
                    // 125 is the special code for timeout in long-poll (see buildLongPollCommand).
                    // However, there is a tiny chance that the underlying command might have returned that exact exit code!
                    // Don't treat a timeout as a "consecutiveSshFailure".
                    if (LOG.isDebugEnabled()) LOG.debug("Long-poll received exit status "+longPollResult+"; most likely timeout; retrieving actual status on "+SshjTool.this.toString()+" (for "+getSummary()+")");
                    return retrieveStatusCommand();

                } else {
                    // want to double-check whether this is the exit-code from the async process, or
                    // some unexpected failure in our long-poll command.
                    if (LOG.isDebugEnabled()) LOG.debug("Long-poll received exit status "+longPollResult+"; retrieving actual status on "+SshjTool.this.toString()+" (for "+getSummary()+")");
                    Integer result = retrieveStatusCommand();
                    if (result != null) {
                        return result;
                    }
                }
                    
                consecutiveSshFailures++;
                if (consecutiveSshFailures > maxConsecutiveSshFailures) {
                    LOG.warn("Aborting on "+consecutiveSshFailures+" consecutive ssh connection errors (return -1) when polling for async script to complete on "+SshjTool.this.toString()+" ("+getSummary()+")");
                    return -1;
                } else {
                    LOG.info("Retrying after ssh connection error when polling for async script to complete on "+SshjTool.this.toString()+" ("+getSummary()+")");
                    return null;
                }
            }
            
            Integer retrieveStatusCommand() throws IOException {
                // want to double-check whether this is the exit-code from the async process, or
                // some unexpected failure in our long-poll command.
                ByteArrayOutputStream statusOut = new ByteArrayOutputStream();
                ByteArrayOutputStream statusErr = new ByteArrayOutputStream();
                int statusResult = asInt(acquire(new ShellAction(buildRetrieveStatusCommand(), statusOut, statusErr, execTimeout)), -1);
                
                if (statusResult == 0) {
                    // The status we retrieved really is valid; return it.
                    // TODO How to ensure no additional output in stdout/stderr when parsing below?
                    String statusOutStr = new String(statusOut.toByteArray()).trim();
                    if (Strings.isEmpty(statusOutStr)) {
                        // suggests not yet completed; will retry with long-poll
                        if (LOG.isDebugEnabled()) LOG.debug("Long-poll retrieved status directly; command successful but no result available on "+SshjTool.this.toString()+" (for "+getSummary()+")");
                        return null;
                    } else {
                        if (LOG.isDebugEnabled()) LOG.debug("Long-poll retrieved status directly; returning '"+statusOutStr+"' on "+SshjTool.this.toString()+" (for "+getSummary()+")");
                        int result = Integer.parseInt(statusOutStr);
                        return result;
                    }

                } else if (statusResult == -1) {
                    // probably a connection failure; try again with long-poll
                    if (LOG.isDebugEnabled()) LOG.debug("Long-poll retrieving status directly received exit status -1; will retry on "+SshjTool.this.toString()+" (for "+getSummary()+")");
                    return null;
                    
                } else {
                    if (out != null) {
                        out.write(toUTF8ByteArray("retrieving status failed with exit code "+statusResult+" (stdout follow)"));
                        out.write(statusOut.toByteArray());
                    }
                    if (err != null) {
                        err.write(toUTF8ByteArray("retrieving status failed with exit code "+statusResult+" (stderr follow)"));
                        err.write(statusErr.toByteArray());
                    }
                    
                    if (LOG.isDebugEnabled()) LOG.debug("Long-poll retrieving status failed; returning "+statusResult+" on "+SshjTool.this.toString()+" (for "+getSummary()+")");
                    return statusResult;
                }
            }
        }.run();
    }
    
    public int execShellDirect(Map<String,?> props, List<String> commands, Map<String,?> env) {
        OutputStream out = getOptionalVal(props, PROP_OUT_STREAM);
        OutputStream err = getOptionalVal(props, PROP_ERR_STREAM);
        Duration execTimeout = getOptionalVal(props, PROP_EXEC_TIMEOUT);
        
        List<String> cmdSequence = toCommandSequence(commands, env);
        List<String> allcmds = ImmutableList.<String>builder()
                .add(getOptionalVal(props, PROP_DIRECT_HEADER))
                .addAll(cmdSequence)
                .add("exit $?")
                .build();
        
        if (LOG.isTraceEnabled()) LOG.trace("Running shell command at {}: {}", host, allcmds);
        
        Integer result = acquire(new ShellAction(allcmds, out, err, execTimeout));
        if (LOG.isTraceEnabled()) LOG.trace("Running shell command at {} completed: return status {}", host, result);
        return asInt(result, -1);
    }

    @Override
    public int execCommands(Map<String,?> props, List<String> commands, Map<String,?> env) {
        if (Boolean.FALSE.equals(props.get("blocks"))) {
            throw new IllegalArgumentException("Cannot exec non-blocking: command="+commands);
        }
        
        // If async is set, then do it as execScript
        Boolean execAsync = getOptionalVal(props, PROP_EXEC_ASYNC);
        if (Boolean.TRUE.equals(execAsync) && BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_SSH_ASYNC_EXEC)) {
            return execScriptAsyncAndPoll(props, commands, env);
        }

        OutputStream out = getOptionalVal(props, PROP_OUT_STREAM);
        OutputStream err = getOptionalVal(props, PROP_ERR_STREAM);
        String separator = getOptionalVal(props, PROP_SEPARATOR);
        Duration execTimeout = getOptionalVal(props, PROP_EXEC_TIMEOUT);

        List<String> allcmds = toCommandSequence(commands, env);
        String singlecmd = Joiner.on(separator).join(allcmds);

        if (Boolean.TRUE.equals(getOptionalVal(props, PROP_RUN_AS_ROOT))) {
            LOG.warn("Cannot run as root when executing as command; run as a script instead (will run as normal user): "+singlecmd);
        }
        
        if (LOG.isTraceEnabled()) LOG.trace("Running command at {}: {}", host, singlecmd);
        
        Command result = acquire(new ExecAction(singlecmd, out, err, execTimeout));
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

    protected <T, C extends SshAction<T>> T acquire(C action) {
        return acquire(action, sshTries, sshTriesTimeout == 0 ? Duration.PRACTICALLY_FOREVER : Duration.millis(sshTriesTimeout));
    }
    
    protected <T, C extends SshAction<T>> T acquire(C action, int sshTries, Duration sshTriesTimeout) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        
        for (int i = 0; i < sshTries; i++) {
            try {
                action.clear();
                if (LOG.isTraceEnabled()) LOG.trace(">> ({}) acquiring {}", toString(), action);
                Stopwatch perfStopwatch = Stopwatch.createStarted();
                
                T returnVal;
                try {
                    returnVal = action.create();
                } catch (AssertionError e) {
                    /*
                     * TODO In net.schmizz.sshj.SSHClient.auth(SSHClient.java:204) throws AssertionError
                     * if not connected. This can happen if another thread has called disconnect
                     * concurrently. This is changed in sshj v0.9.0 to instead throw an IllegalStateException.
                     * 
                     * For now, we'll retry. See "TODO" at top of class about synchronization.
                     */
                    throw new IllegalStateException("Problem in "+toString()+" for "+action, e);
                }
                
                if (LOG.isTraceEnabled()) LOG.trace("<< ({}) acquired {}", toString(), returnVal);
                if (LOG.isTraceEnabled()) LOG.trace("SSH Performance: {} {} took {}", new Object[] {
                        sshClientConnection.getHostAndPort(), 
                        action.getClass().getSimpleName() != null ? action.getClass().getSimpleName() : action, 
                        Time.makeTimeStringRounded(perfStopwatch)});
                return returnVal;
            } catch (Exception e) {
                // uninformative net.schmizz.sshj.connection.ConnectionException: 
                //    Request failed (reason=UNKNOWN) may mean remote Subsytem is disabled (e.g. for FTP)
                // if key is missing, get a UserAuth error
                String errorMessage = String.format("(%s) error acquiring %s", toString(), action);
                String fullMessage = String.format("%s (attempt %s/%s, in time %s/%s)", 
                        errorMessage, (i+1), sshTries, Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS)), 
                        (sshTriesTimeout.equals(Duration.PRACTICALLY_FOREVER) ? "unlimited" : Time.makeTimeStringRounded(sshTriesTimeout)));
                try {
                    disconnect();
                } catch (Exception e2) {
                    LOG.debug("<< ("+toString()+") error closing connection: "+e+" / "+e2, e);
                }
                if (i + 1 == sshTries) {
                    LOG.debug("<< {} (rethrowing, out of retries): {}", fullMessage, e.getMessage());
                    throw propagate(e, fullMessage + "; out of retries");
                } else if (sshTriesTimeout.isShorterThan(stopwatch)) {
                    LOG.debug("<< {} (rethrowing, out of time - max {}): {}", new Object[] { fullMessage, Time.makeTimeStringRounded(sshTriesTimeout), e.getMessage() });
                    throw new RuntimeTimeoutException(fullMessage + "; out of time", e);
                } else {
                    if (LOG.isDebugEnabled()) LOG.debug("<< {}: {}", fullMessage, e.getMessage());
                    backoffForAttempt(i + 1, errorMessage + ": " + e.getMessage());
                    if (action != sshClientConnection)
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
    }

    private class PutFileAction implements SshAction<Void> {
        // TODO support backup as a property?
        
        private SFTPClient sftp;
        private final String path;
        private final int permissionsMask;
        private final long lastModificationDate;
        private final long lastAccessDate;
        private final int uid;
        private final Supplier<InputStream> contentsSupplier;
        private final Integer length;
        
        PutFileAction(Map<String,?> props, String path, Supplier<InputStream> contentsSupplier, long length) {
            String permissions = getOptionalVal(props, PROP_PERMISSIONS);
            long lastModificationDateVal = getOptionalVal(props, PROP_LAST_MODIFICATION_DATE);
            long lastAccessDateVal = getOptionalVal(props, PROP_LAST_ACCESS_DATE);
            if (lastAccessDateVal <= 0 ^ lastModificationDateVal <= 0) {
                lastAccessDateVal = Math.max(lastAccessDateVal, lastModificationDateVal);
                lastModificationDateVal = Math.max(lastAccessDateVal, lastModificationDateVal);
            }
            this.permissionsMask = Integer.parseInt(permissions, 8);
            this.lastAccessDate = lastAccessDateVal;
            this.lastModificationDate = lastModificationDateVal;
            this.uid = getOptionalVal(props, PROP_OWNER_UID);
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
                if (uid != -1) {
                    sftp.chown(path, uid);
                }
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
    }

    // TODO simpler not to use predicates
    @VisibleForTesting
    Predicate<String> causalChainHasMessageContaining(final Exception from) {
        return new Predicate<String>() {
            @Override
            public boolean apply(final String input) {
                return any(getCausalChain(from), new Predicate<Throwable>() {
                    @Override
                    public boolean apply(Throwable throwable) {
                        return (throwable.toString().contains(input))
                                || (throwable.getMessage() != null && throwable.getMessage().contains(input));
                    }
                });
            }
        };
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
                if (allocatePTY) {
                    session.allocatePTY(TERM, 80, 24, 0, 0, Collections.<PTYMode, Integer> emptyMap());
                }
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
        private final OutputStream out;
        private final OutputStream err;
        private final Duration timeout;
        
        private Session session;
        private Shell shell;
        private StreamGobbler outgobbler;
        private StreamGobbler errgobbler;
        
        ExecAction(String command, OutputStream out, OutputStream err, Duration timeout) {
            this.command = checkNotNull(command, "command");
            this.out = out;
            this.err = err;
            Duration sessionTimeout = (sshClientConnection.getSessionTimeout() == 0) 
                    ? Duration.PRACTICALLY_FOREVER 
                    : Duration.millis(sshClientConnection.getSessionTimeout());
            this.timeout = (timeout == null) ? sessionTimeout : Duration.min(timeout, sessionTimeout);
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
                    output.join((int)Math.min(timeout.toMilliseconds(), Integer.MAX_VALUE), TimeUnit.MILLISECONDS);
                    return output;
                    
                } finally {
                    // wait for all stdout/stderr to have been re-directed
                    try {
                        // Don't use forever (i.e. 0) because BROOKLYN-106: ssh hangs
                        long joinTimeout = 10*1000;
                        if (outgobbler != null) outgobbler.join(joinTimeout);
                        if (errgobbler != null) errgobbler.join(joinTimeout);
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
        @VisibleForTesting
        final List<String> commands;
        @VisibleForTesting
        final OutputStream out;
        @VisibleForTesting
        final OutputStream err;
        
        private Session session;
        private Shell shell;
        private StreamGobbler outgobbler;
        private StreamGobbler errgobbler;
        private Duration timeout;

        ShellAction(List<String> commands, OutputStream out, OutputStream err, Duration timeout) {
            this.commands = checkNotNull(commands, "commands");
            this.out = out;
            this.err = err;
            Duration sessionTimeout = (sshClientConnection.getSessionTimeout() == 0) 
                    ? Duration.PRACTICALLY_FOREVER 
                    : Duration.millis(sshClientConnection.getSessionTimeout());
            this.timeout = (timeout == null) ? sessionTimeout : Duration.min(timeout, sessionTimeout);
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
                        output.write(toUTF8ByteArray(cmd+"\n"));
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
                
                boolean timedOut = false;
                try {
                    long timeoutMillis = Math.min(timeout.toMilliseconds(), Integer.MAX_VALUE);
                    long timeoutEnd = System.currentTimeMillis() + timeoutMillis;
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
                        } catch (ConnectionException e) {
                            last = e;
                        }
                        if (endBecauseReturned) {
                            // shell is still open, ie some process is running
                            // but we have a result code, so main shell is finished
                            // we waited one second extra to allow any background process 
                            // which is nohupped to really be in the background (#162)
                            // now let's bail out
                            break;
                        }
                    } while (System.currentTimeMillis() < timeoutEnd);
                    if (shell.isOpen() && ((SessionChannel)session).getExitStatus()==null) {
                        LOG.debug("Timeout ({}) in SSH shell to {}", timeout, this);
                        // we timed out, or other problem -- reproduce the error.
                        // The shell.join should always have thrown ConnectionExceptoin (looking at code of
                        // AbstractChannel), but javadoc of Channel doesn't explicity say that so play it safe.
                        timedOut = true;
                        throw (last != null) ? last : new TimeoutException("Timeout after "+timeout+" executing "+this);
                    }
                    return ((SessionChannel)session).getExitStatus();
                } finally {
                    // wait for all stdout/stderr to have been re-directed
                    closeWhispering(shell, this);
                    shell = null;
                    try {
                        // Don't use forever (i.e. 0) because BROOKLYN-106: ssh hangs
                        long joinTimeout = (timedOut) ? 1000 : 10*1000;
                        if (outgobbler != null) {
                            outgobbler.join(joinTimeout);
                            outgobbler.close();
                        }
                        if (errgobbler != null) {
                            errgobbler.join(joinTimeout);
                            errgobbler.close();
                        }
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

    private byte[] toUTF8ByteArray(String string) {
        return org.bouncycastle.util.Strings.toUTF8ByteArray(string);
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

}

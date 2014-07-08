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
package brooklyn.util.internal.ssh.process;

import static brooklyn.entity.basic.ConfigKeys.newConfigKey;
import static brooklyn.entity.basic.ConfigKeys.newStringConfigKey;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.internal.ssh.ShellAbstractTool;
import brooklyn.util.internal.ssh.ShellTool;
import brooklyn.util.internal.ssh.SshException;
import brooklyn.util.os.Os;
import brooklyn.util.stream.StreamGobbler;
import brooklyn.util.text.Strings;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

/** Implementation of {@link ShellTool} which runs locally. */
public class ProcessTool extends ShellAbstractTool implements ShellTool {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessTool.class);

    // applies to calls
    
    public static final ConfigKey<Boolean> PROP_LOGIN_SHELL = newConfigKey("loginShell", "Causes the commands to be invoked with bash arguments to forcea  login shell", Boolean.FALSE);

    public static final ConfigKey<String> PROP_DIRECTORY = newStringConfigKey("directory", "the working directory, for executing commands", null);
    
    public ProcessTool() {
        this(null);
    }
    
    public ProcessTool(Map<String,?> flags) {
        super(getOptionalVal(flags, PROP_LOCAL_TEMP_DIR));
        if (flags!=null) {
            MutableMap<String, Object> flags2 = MutableMap.copyOf(flags);
            // TODO should remember other flags here?  (e.g. NO_EXTRA_OUTPUT, RUN_AS_ROOT, etc)
            flags2.remove(PROP_LOCAL_TEMP_DIR.getName());
            if (!flags2.isEmpty())
                LOG.warn(""+this+" ignoring unsupported constructor flags: "+flags);
        }
    }

    @Override
    public int execScript(final Map<String,?> props, final List<String> commands, final Map<String,?> env) {
        return new ToolAbstractExecScript(props) {
            public int run() {
                try {
                    String directory = getOptionalVal(props, PROP_DIRECTORY);
                    File directoryDir = (directory != null) ? new File(Os.tidyPath(directory)) : null;
                    
                    String scriptContents = toScript(props, commands, env);

                    if (LOG.isTraceEnabled()) LOG.trace("Running shell process (process) as script:\n{}", scriptContents);
                    File to = new File(scriptPath);
                    Files.createParentDirs(to);
                    Files.copy(ByteStreams.newInputStreamSupplier(scriptContents.getBytes()), to);

                    List<String> cmds = buildRunScriptCommand();
                    cmds.add(0, "chmod +x "+scriptPath);
                    return asInt(execProcesses(cmds, null, directoryDir, out, err, separator, getOptionalVal(props, PROP_LOGIN_SHELL), this), -1);
                } catch (IOException e) {
                    throw Throwables.propagate(e);
                }
            }
        }.run();
    }

    @Override
    public int execCommands(Map<String,?> props, List<String> commands, Map<String,?> env) {
        if (Boolean.FALSE.equals(props.get("blocks"))) {
            throw new IllegalArgumentException("Cannot exec non-blocking: command="+commands);
        }
        OutputStream out = getOptionalVal(props, PROP_OUT_STREAM);
        OutputStream err = getOptionalVal(props, PROP_ERR_STREAM);
        String separator = getOptionalVal(props, PROP_SEPARATOR);
        String directory = getOptionalVal(props, PROP_DIRECTORY);
        File directoryDir = (directory != null) ? new File(Os.tidyPath(directory)) : null;

        List<String> allcmds = toCommandSequence(commands, null);

        String singlecmd = Joiner.on(separator).join(allcmds);
        if (Boolean.TRUE.equals(getOptionalVal(props, PROP_RUN_AS_ROOT))) {
            LOG.warn("Cannot run as root when executing as command; run as a script instead (will run as normal user): "+singlecmd);
        }
        if (LOG.isTraceEnabled()) LOG.trace("Running shell command (process): {}", singlecmd);
        
        return asInt(execProcesses(allcmds, env, directoryDir, out, err, separator, getOptionalVal(props, PROP_LOGIN_SHELL), this), -1);
    }

    /**
     * as {@link #execProcesses(List, Map, OutputStream, OutputStream, String, boolean, Object)} but not using a login shell
     * @deprecated since 0.7; use {@link #execProcesses(List, Map, File, OutputStream, OutputStream, String, boolean, Object)}
     */
    @Deprecated
    public static int execProcesses(List<String> cmds, Map<String,?> env, OutputStream out, OutputStream err, String separator, Object contextForLogging) {
        return execProcesses(cmds, env, (File)null, out, err, separator, false, contextForLogging);
    }

    /**
     * @deprecated since 0.7; use {@link #execProcesses(List, Map, File, OutputStream, OutputStream, String, boolean, Object)}
     */
    @Deprecated
    public static int execProcesses(List<String> cmds, Map<String,?> env, OutputStream out, OutputStream err, String separator, boolean asLoginShell, Object contextForLogging) {
        return execProcesses(cmds, env, (File)null, out, err, separator, asLoginShell, contextForLogging);
    }
    
    /** executes a set of commands by sending them as a single process to `bash -c` 
     * (single command argument of all the commands, joined with separator)
     * <p>
     * consequence of this is that you should not normally need to escape things oddly in your commands, 
     * type them just as you would into a bash shell (if you find exceptions please note them here!)
     */
    public static int execProcesses(List<String> cmds, Map<String,?> env, File directory, OutputStream out, OutputStream err, String separator, boolean asLoginShell, Object contextForLogging) {
        MutableList<String> commands = new MutableList<String>().append("bash");
        if (asLoginShell) commands.append("-l");
        commands.append("-c", Strings.join(cmds, Preconditions.checkNotNull(separator, "separator")));
        return execSingleProcess(commands, env, directory, out, err, contextForLogging);
    }
    
    /**
     * @deprecated since 0.7; use {@link #execSingleProcess(List, Map, File, OutputStream, OutputStream, Object)}
     */
    @Deprecated
    public static int execSingleProcess(List<String> cmdWords, Map<String,?> env, OutputStream out, OutputStream err, Object contextForLogging) {
        return execSingleProcess(cmdWords, env, (File)null, out, err, contextForLogging);
    }
    
    /** executes a single process made up of the given command words (*not* bash escaped);
     * should be portable across OS's */
    public static int execSingleProcess(List<String> cmdWords, Map<String,?> env, File directory, OutputStream out, OutputStream err, Object contextForLogging) {
        StreamGobbler errgobbler = null;
        StreamGobbler outgobbler = null;
        
        ProcessBuilder pb = new ProcessBuilder(cmdWords);
        if (env!=null) {
            for (Map.Entry<String,?> kv: env.entrySet()) pb.environment().put(kv.getKey(), String.valueOf(kv.getValue())); 
        }
        if (directory != null) {
            pb.directory(directory);
        }
        
        try {
            Process p = pb.start();
            
            if (out != null) {
                InputStream outstream = p.getInputStream();
                outgobbler = new StreamGobbler(outstream, out, (Logger) null);
                outgobbler.start();
            }
            if (err != null) {
                InputStream errstream = p.getErrorStream();
                errgobbler = new StreamGobbler(errstream, err, (Logger) null);
                errgobbler.start();
            }
            
            int result = p.waitFor();
            
            if (outgobbler != null) outgobbler.blockUntilFinished();
            if (errgobbler != null) errgobbler.blockUntilFinished();
            
            if (result==255)
                // this is not definitive, but tests (and code?) expects throw exception if can't connect;
                // only return exit code when it is exit code from underlying process;
                // we have no way to distinguish 255 from ssh failure from 255 from the command run through ssh ...
                // but probably 255 is from CLI ssh
                throw new SshException("exit code 255 from CLI ssh; probably failed to connect");
            
            return result;
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        } finally {
            closeWhispering(outgobbler, contextForLogging, "execProcess");
            closeWhispering(errgobbler, contextForLogging, "execProcess");
        }
    }

}

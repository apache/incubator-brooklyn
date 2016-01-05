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
package org.apache.brooklyn.util.core.task.system.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.core.internal.ssh.ShellAbstractTool;
import org.apache.brooklyn.util.core.internal.ssh.ShellTool;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.stream.StreamGobbler;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Throwables;

public abstract class ExecWithLoggingHelpers {

    public static final ConfigKey<OutputStream> STDOUT = SshMachineLocation.STDOUT;
    public static final ConfigKey<OutputStream> STDERR = SshMachineLocation.STDERR;
    public static final ConfigKey<Boolean> NO_STDOUT_LOGGING = SshMachineLocation.NO_STDOUT_LOGGING;
    public static final ConfigKey<Boolean> NO_STDERR_LOGGING = SshMachineLocation.NO_STDERR_LOGGING;
    public static final ConfigKey<String> LOG_PREFIX = SshMachineLocation.LOG_PREFIX;

    protected final String shortName;
    protected Logger commandLogger = null;
    
    public interface ExecRunner {
        public int exec(ShellTool ssh, Map<String,?> flags, List<String> cmds, Map<String,?> env);
    }

    protected abstract <T> T execWithTool(MutableMap<String, Object> toolCreationAndConnectionProperties, Function<ShellTool, T> runMethodOnTool);
    protected abstract void preExecChecks();
    protected abstract String getTargetName();
    protected abstract String constructDefaultLoggingPrefix(ConfigBag execFlags);

    /** takes a very short name for use in blocking details, e.g. SSH or Process */
    public ExecWithLoggingHelpers(String shortName) {
        this.shortName = shortName;
    }

    public ExecWithLoggingHelpers logger(Logger commandLogger) {
        this.commandLogger = commandLogger;
        return this;
    }
    
    public int execScript(Map<String,?> props, String summaryForLogging, List<String> commands, Map<String,?> env) {
        // TODO scriptHeader are the extra commands we expect the SshTool/ShellTool to add.
        // Would be better if could get this from the ssh-tool, rather than assuming it will behave as
        // we expect.
        String scriptHeader = ShellAbstractTool.getOptionalVal(props, ShellTool.PROP_SCRIPT_HEADER);
        
        return execWithLogging(props, summaryForLogging, commands, env, scriptHeader, new ExecRunner() {
                @Override public int exec(ShellTool ssh, Map<String, ?> flags, List<String> cmds, Map<String, ?> env) {
                    return ssh.execScript(flags, cmds, env);
                }});
    }

    protected static <T> T getOptionalVal(Map<String,?> map, ConfigKey<T> keyC) {
        if (keyC==null) return null;
        String key = keyC.getName();
        if (map!=null && map.containsKey(key)) {
            return TypeCoercions.coerce(map.get(key), keyC.getTypeToken());
        } else {
            return keyC.getDefaultValue();
        }
    }

    public int execCommands(Map<String,?> props, String summaryForLogging, List<String> commands, Map<String,?> env) {
        return execWithLogging(props, summaryForLogging, commands, env, new ExecRunner() {
                @Override public int exec(ShellTool tool, Map<String,?> flags, List<String> cmds, Map<String,?> env) {
                    return tool.execCommands(flags, cmds, env);
                }});
    }

    public int execWithLogging(Map<String,?> props, final String summaryForLogging, final List<String> commands,
            final Map<String,?> env, final ExecRunner execCommand) {
        return execWithLogging(props, summaryForLogging, commands, env, null, execCommand);
    }
    
    @SuppressWarnings("resource")
    public int execWithLogging(Map<String,?> props, final String summaryForLogging, final List<String> commands,
            final Map<String,?> env, String expectedCommandHeaders, final ExecRunner execCommand) {
        if (commandLogger!=null && commandLogger.isDebugEnabled()) {
            String allcmds = (Strings.isBlank(expectedCommandHeaders) ? "" : expectedCommandHeaders + " ; ") + Strings.join(commands, " ; ");
            commandLogger.debug("{}, initiating "+shortName.toLowerCase()+" on machine {}{}: {}",
                    new Object[] {summaryForLogging, getTargetName(),
                    env!=null && !env.isEmpty() ? " (env "+env+")": "", allcmds});
        }

        if (commands.isEmpty()) {
            if (commandLogger!=null && commandLogger.isDebugEnabled())
                commandLogger.debug("{}, on machine {}, ending: no commands to run", summaryForLogging, getTargetName());
            return 0;
        }

        final ConfigBag execFlags = new ConfigBag().putAll(props);
        // some props get overridden in execFlags, so remove them from the tool flags
        final ConfigBag toolFlags = new ConfigBag().putAll(props).removeAll(
                LOG_PREFIX, STDOUT, STDERR, ShellTool.PROP_NO_EXTRA_OUTPUT);

        execFlags.configure(ShellTool.PROP_SUMMARY, summaryForLogging);
        
        PipedOutputStream outO = null;
        PipedOutputStream outE = null;
        StreamGobbler gO=null, gE=null;
        try {
            preExecChecks();
            
            String logPrefix = execFlags.get(LOG_PREFIX);
            if (logPrefix==null) logPrefix = constructDefaultLoggingPrefix(execFlags);

            if (!execFlags.get(NO_STDOUT_LOGGING)) {
                PipedInputStream insO = new PipedInputStream();
                outO = new PipedOutputStream(insO);

                String stdoutLogPrefix = "["+(logPrefix != null ? logPrefix+":stdout" : "stdout")+"] ";
                gO = new StreamGobbler(insO, execFlags.get(STDOUT), commandLogger).setLogPrefix(stdoutLogPrefix);
                gO.start();

                execFlags.put(STDOUT, outO);
            }

            if (!execFlags.get(NO_STDERR_LOGGING)) {
                PipedInputStream insE = new PipedInputStream();
                outE = new PipedOutputStream(insE);

                String stderrLogPrefix = "["+(logPrefix != null ? logPrefix+":stderr" : "stderr")+"] ";
                gE = new StreamGobbler(insE, execFlags.get(STDERR), commandLogger).setLogPrefix(stderrLogPrefix);
                gE.start();

                execFlags.put(STDERR, outE);
            }

            Tasks.setBlockingDetails(shortName+" executing, "+summaryForLogging);
            try {
                return execWithTool(MutableMap.copyOf(toolFlags.getAllConfig()), new Function<ShellTool, Integer>() {
                    public Integer apply(ShellTool tool) {
                        int result = execCommand.exec(tool, MutableMap.copyOf(execFlags.getAllConfig()), commands, env);
                        if (commandLogger!=null && commandLogger.isDebugEnabled()) 
                            commandLogger.debug("{}, on machine {}, completed: return status {}",
                                    new Object[] {summaryForLogging, getTargetName(), result});
                        return result;
                    }});

            } finally {
                Tasks.setBlockingDetails(null);
            }

        } catch (IOException e) {
            if (commandLogger!=null && commandLogger.isDebugEnabled()) 
                commandLogger.debug("{}, on machine {}, failed: {}", new Object[] {summaryForLogging, getTargetName(), e});
            throw Throwables.propagate(e);
        } finally {
            // Must close the pipedOutStreams, otherwise input will never read -1 so StreamGobbler thread would never die
            if (outO!=null) try { outO.flush(); } catch (IOException e) {}
            if (outE!=null) try { outE.flush(); } catch (IOException e) {}
            Streams.closeQuietly(outO);
            Streams.closeQuietly(outE);

            try {
                if (gE!=null) { gE.join(); }
                if (gO!=null) { gO.join(); }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Throwables.propagate(e);
            }
        }

    }

}

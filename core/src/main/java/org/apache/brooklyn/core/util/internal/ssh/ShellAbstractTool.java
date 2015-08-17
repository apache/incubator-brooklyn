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
package org.apache.brooklyn.core.util.internal.ssh;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.brooklyn.core.util.flags.TypeCoercions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.util.collections.MutableList;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.StringEscapes.BashStringEscapes;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

public abstract class ShellAbstractTool implements ShellTool {

    private static final Logger LOG = LoggerFactory.getLogger(ShellAbstractTool.class);

    protected final File localTempDir;

    public ShellAbstractTool(String localTempDir) {
        this(localTempDir == null ? null : new File(Os.tidyPath(localTempDir)));
    }
    
    public ShellAbstractTool(File localTempDir) {
        if (localTempDir == null) {
            localTempDir = new File(Os.tmp(), "tmpssh-"+Os.user());
            if (!localTempDir.exists()) localTempDir.mkdir();
            Os.deleteOnExitEmptyParentsUpTo(localTempDir, new File(Os.tmp()));
        }
        this.localTempDir = localTempDir;
    }
    
    public ShellAbstractTool() {
        this((File)null);
    }
    
    protected static void warnOnDeprecated(Map<String, ?> props, String deprecatedKey, String correctKey) {
        if (props.containsKey(deprecatedKey)) {
            if (correctKey != null && props.containsKey(correctKey)) {
                Object dv = props.get(deprecatedKey);
                Object cv = props.get(correctKey);
                if (!Objects.equal(cv, dv)) {
                    LOG.warn("SshTool detected deprecated key '"+deprecatedKey+"' with different value ("+dv+") "+
                            "than new key '"+correctKey+"' ("+cv+"); ambiguous which will be used");
                } else {
                    // ignore, the deprecated key populated for legacy reasons
                }
            } else {
                Object dv = props.get(deprecatedKey);
                LOG.warn("SshTool detected deprecated key '"+deprecatedKey+"' used, with value ("+dv+")");     
            }
        }
    }

    protected static Boolean hasVal(Map<String,?> map, ConfigKey<?> keyC) {
        String key = keyC.getName();
        return map.containsKey(key);
    }
    
    protected static <T> T getMandatoryVal(Map<String,?> map, ConfigKey<T> keyC) {
        String key = keyC.getName();
        checkArgument(map.containsKey(key), "must contain key '"+keyC+"'");
        return TypeCoercions.coerce(map.get(key), keyC.getTypeToken());
    }
    
    public static <T> T getOptionalVal(Map<String,?> map, ConfigKey<T> keyC) {
        if (keyC==null) return null;
        String key = keyC.getName();
        if (map!=null && map.containsKey(key) && map.get(key) != null) {
            return TypeCoercions.coerce(map.get(key), keyC.getTypeToken());
        } else {
            return keyC.getDefaultValue();
        }
    }

    /** returns the value of the key if specified, otherwise defaultValue */
    protected static <T> T getOptionalVal(Map<String,?> map, ConfigKey<T> keyC, T defaultValue) {
        String key = keyC.getName();
        if (map!=null && map.containsKey(key) && map.get(key) != null) {
            return TypeCoercions.coerce(map.get(key), keyC.getTypeToken());
        } else {
            return defaultValue;
        }
    }

    protected void closeWhispering(Closeable closeable, Object context) {
        closeWhispering(closeable, this, context);
    }
    
    /**
     * Similar to Guava's Closeables.closeQuitely, except logs exception at debug with context in message.
     */
    protected static void closeWhispering(Closeable closeable, Object context1, Object context2) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                if (LOG.isDebugEnabled()) {
                    String msg = String.format("<< exception during close, for %s -> %s (%s); continuing.", 
                            context1, context2, closeable);
                    if (LOG.isTraceEnabled())
                        LOG.debug(msg + ": " + e);
                    else
                        LOG.trace(msg, e);
                }
            }
        }
    }

    protected File writeTempFile(InputStream contents) {
        File tempFile = Os.writeToTempFile(contents, localTempDir, "sshcopy", "data");
        tempFile.setReadable(false, false);
        tempFile.setReadable(true, true);
        tempFile.setWritable(false);
        tempFile.setExecutable(false);
        return tempFile;
    }

    protected File writeTempFile(String contents) {
        return writeTempFile(contents.getBytes());
    }

    protected File writeTempFile(byte[] contents) {
        return writeTempFile(new ByteArrayInputStream(contents));
    }

    protected String toScript(Map<String,?> props, List<String> commands, Map<String,?> env) {
        List<String> allcmds = toCommandSequence(commands, env);
        StringBuilder result = new StringBuilder();
        result.append(getOptionalVal(props, PROP_SCRIPT_HEADER)).append('\n');
        
        for (String cmd : allcmds) {
            result.append(cmd).append('\n');
        }
        
        return result.toString();
    }

    /**
     * Merges the commands and env, into a single set of commands. Also escapes the commands as required.
     * 
     * Not all ssh servers handle "env", so instead convert env into exported variables
     */
    protected List<String> toCommandSequence(List<String> commands, Map<String,?> env) {
        List<String> result = new ArrayList<String>((env!=null ? env.size() : 0) + commands.size());
        
        if (env!=null) {
            for (Entry<String,?> entry : env.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    LOG.warn("env key-values must not be null; ignoring: key="+entry.getKey()+"; value="+entry.getValue());
                    continue;
                }
                String escapedVal = BashStringEscapes.escapeLiteralForDoubleQuotedBash(entry.getValue().toString());
                result.add("export "+entry.getKey()+"=\""+escapedVal+"\"");
            }
        }
        for (CharSequence cmd : commands) { // objects in commands can be groovy GString so can't treat as String here
            result.add(cmd.toString());
        }

        return result;
    }

    @Override
    public int execScript(Map<String,?> props, List<String> commands) {
        return execScript(props, commands, Collections.<String,Object>emptyMap());
    }

    @Override
    public int execCommands(Map<String,?> props, List<String> commands) {
        return execCommands(props, commands, Collections.<String,Object>emptyMap());
    }

    protected static int asInt(Integer input, int valueIfInputNull) {
        return input != null ? input : valueIfInputNull;
    }

    protected abstract class ToolAbstractExecScript {
        protected final Map<String, ?> props;
        protected final String separator;
        protected final OutputStream out;
        protected final OutputStream err;
        protected final String scriptDir;
        protected final Boolean runAsRoot;
        protected final Boolean noExtraOutput;
        protected final Boolean noDeleteAfterExec;
        protected final String scriptNameWithoutExtension;
        protected final String scriptPath;
        protected final Duration execTimeout;

        public ToolAbstractExecScript(Map<String,?> props) {
            this.props = props;
            this.separator = getOptionalVal(props, PROP_SEPARATOR);
            this.out = getOptionalVal(props, PROP_OUT_STREAM);
            this.err = getOptionalVal(props, PROP_ERR_STREAM);
            
            this.scriptDir = getOptionalVal(props, PROP_SCRIPT_DIR);
            this.runAsRoot = getOptionalVal(props, PROP_RUN_AS_ROOT);
            this.noExtraOutput = getOptionalVal(props, PROP_NO_EXTRA_OUTPUT);
            this.noDeleteAfterExec = getOptionalVal(props, PROP_NO_DELETE_SCRIPT);
            this.execTimeout = getOptionalVal(props, PROP_EXEC_TIMEOUT);
            
            String summary = getOptionalVal(props, PROP_SUMMARY);
            if (summary!=null) {
                summary = Strings.makeValidFilename(summary);
                if (summary.length()>30) 
                    summary = summary.substring(0,30);
            }
            this.scriptNameWithoutExtension = "brooklyn-"+
                    Time.makeDateStampString()+"-"+Identifiers.makeRandomId(4)+
                    (Strings.isBlank(summary) ? "" : "-"+summary);
            this.scriptPath = Os.mergePathsUnix(scriptDir, scriptNameWithoutExtension+".sh");
        }

        /** builds the command to run the given script;
         * note that some modes require \$RESULT passed in order to access a variable, whereas most just need $ */
        protected List<String> buildRunScriptCommand() {
            MutableList.Builder<String> cmds = MutableList.<String>builder()
                    .add((runAsRoot ? BashCommands.sudo(scriptPath) : scriptPath) + " < /dev/null")
                    .add("RESULT=$?");
            if (noExtraOutput==null || !noExtraOutput)
                cmds.add("echo Executed "+scriptPath+", result $RESULT"); 
            if (noDeleteAfterExec!=Boolean.TRUE) {
                // use "-f" because some systems have "rm" aliased to "rm -i"
                // use "< /dev/null" to guarantee doesn't hang
                cmds.add("rm -f "+scriptPath+" < /dev/null");
            }
            cmds.add("exit $RESULT");
            return cmds.build();
        }

        protected String getSummary() {
            String summary = getOptionalVal(props, PROP_SUMMARY);
            return (summary != null) ? summary : scriptPath; 
        }

        public abstract int run();
    }
    
    protected abstract class ToolAbstractAsyncExecScript extends ToolAbstractExecScript {
        protected final String stdoutPath;
        protected final String stderrPath;
        protected final String exitStatusPath;
        protected final String pidPath;

        public ToolAbstractAsyncExecScript(Map<String,?> props) {
            super(props);

            stdoutPath = Os.mergePathsUnix(scriptDir, scriptNameWithoutExtension + ".stdout");
            stderrPath = Os.mergePathsUnix(scriptDir, scriptNameWithoutExtension + ".stderr");
            exitStatusPath = Os.mergePathsUnix(scriptDir, scriptNameWithoutExtension + ".exitstatus");
            pidPath = Os.mergePathsUnix(scriptDir, scriptNameWithoutExtension + ".pid");
        }

        /**
         * Builds the command to run the given script, asynchronously.
         * The executed command will return immediately, but the output from the script
         * will continue to be written 
         * note that some modes require \$RESULT passed in order to access a variable, whereas most just need $ */
        @Override
        protected List<String> buildRunScriptCommand() {
            String touchCmd = String.format("touch %s %s %s %s", stdoutPath, stderrPath, exitStatusPath, pidPath);
            String cmd = String.format("nohup sh -c \"( %s > %s 2> %s < /dev/null ) ; echo \\$? > %s \" > /dev/null 2>&1 < /dev/null &", scriptPath, stdoutPath, stderrPath, exitStatusPath);
            MutableList.Builder<String> cmds = MutableList.<String>builder()
                    .add(runAsRoot ? BashCommands.sudo(touchCmd) : touchCmd)
                    .add(runAsRoot ? BashCommands.sudo(cmd) : cmd)
                    .add("echo $! > "+pidPath)
                    .add("RESULT=$?");
            if (noExtraOutput==null || !noExtraOutput) {
                cmds.add("echo Executing async "+scriptPath);
            }
            cmds.add("exit $RESULT");
            return cmds.build();
        }

        /**
         * Builds the command to retrieve the exit status of the command, written to stdout.
         */
        protected List<String> buildRetrieveStatusCommand() {
            // Retrieve exit status from file (writtent to stdout), if populated;
            // if not found and pid still running, then return empty string; else exit code 1.
            List<String> cmdParts = ImmutableList.of(
                    "# Retrieve status", // comment is to aid testing - see SshjToolAsyncStubIntegrationTest
                    "if test -s "+exitStatusPath+"; then",
                    "    cat "+exitStatusPath,
                    "elif test -s "+pidPath+"; then",
                    "    pid=`cat "+pidPath+"`",
                    "    if ! ps -p $pid > /dev/null < /dev/null; then",
                    "        # no exit status, and not executing; give a few seconds grace in case just about to write exit status",
                    "        sleep 3",
                    "        if test -s "+exitStatusPath+"; then",
                    "            cat "+exitStatusPath+"",
                    "        else",
                    "            echo \"No exit status in "+exitStatusPath+", and pid in "+pidPath+" ($pid) not executing\"",
                    "            exit 1",
                    "        fi",
                    "    fi",
                    "else",
                    "    echo \"No exit status in "+exitStatusPath+", and "+pidPath+" is empty\"",
                    "    exit 1",
                    "fi"+"\n");
            String cmd = Joiner.on("\n").join(cmdParts);

            MutableList.Builder<String> cmds = MutableList.<String>builder()
                    .add((runAsRoot ? BashCommands.sudo(cmd) : cmd))
                    .add("RESULT=$?");
            cmds.add("exit $RESULT");
            return cmds.build();
        }

        /**
         * Builds the command to retrieve the stdout and stderr of the async command.
         * An offset can be given, to only retrieve data starting at a particular character (indexed from 0).
         */
        protected List<String> buildRetrieveStdoutAndStderrCommand(int stdoutPosition, int stderrPosition) {
            // Note that `tail -c +1` means start at the *first* character (i.e. start counting from 1, not 0)
            String catStdoutCmd = "tail -c +"+(stdoutPosition+1)+" "+stdoutPath+" 2> /dev/null";
            String catStderrCmd = "tail -c +"+(stderrPosition+1)+" "+stderrPath+" 2>&1 > /dev/null";
            MutableList.Builder<String> cmds = MutableList.<String>builder()
                    .add((runAsRoot ? BashCommands.sudo(catStdoutCmd) : catStdoutCmd))
                    .add((runAsRoot ? BashCommands.sudo(catStderrCmd) : catStderrCmd))
                    .add("RESULT=$?");
            cmds.add("exit $RESULT");
            return cmds.build();
        }

        /**
         * Builds the command to retrieve the stdout and stderr of the async command.
         * An offset can be given, to only retrieve data starting at a particular character (indexed from 0).
         */
        protected List<String> buildLongPollCommand(int stdoutPosition, int stderrPosition, Duration timeout) {
            long maxTime = Math.max(1, timeout.toSeconds());
            
            // Note that `tail -c +1` means start at the *first* character (i.e. start counting from 1, not 0)
            List<String> waitForExitStatusParts = ImmutableList.of(
                    //Should be careful here because any output will be part of the stdout/stderr streams
                    "# Long poll", // comment is to aid testing - see SshjToolAsyncStubIntegrationTest
                    // disown to avoid Terminated message after killing the process
                    // redirect error output to avoid "file truncated" messages
                    "tail -c +"+(stdoutPosition+1)+" -f "+stdoutPath+" 2> /dev/null & export TAIL_STDOUT_PID=$!; disown",
                    "tail -c +"+(stderrPosition+1)+" -f "+stderrPath+" 1>&2 2> /dev/null & export TAIL_STDERR_PID=$!; disown",
                    "EXIT_STATUS_PATH="+exitStatusPath,
                    "PID_PATH="+pidPath,
                    "MAX_TIME="+maxTime,
                    "COUNTER=0",
                    "while [ \"$COUNTER\" -lt $MAX_TIME ]; do",
                    "    if test -s $EXIT_STATUS_PATH; then",
                    "        EXIT_STATUS=`cat $EXIT_STATUS_PATH`",
                    "        kill ${TAIL_STDERR_PID} ${TAIL_STDOUT_PID} 2> /dev/null",
                    "        exit $EXIT_STATUS",
                    "    elif test -s $PID_PATH; then",
                    "        PID=`cat $PID_PATH`",
                    "        if ! ps -p $PID > /dev/null 2>&1 < /dev/null; then",
                    "            # no exit status, and not executing; give a few seconds grace in case just about to write exit status",
                    "            sleep 3",
                    "            if test -s $EXIT_STATUS_PATH; then",
                    "                EXIT_STATUS=`cat $EXIT_STATUS_PATH`",
                    "                kill ${TAIL_STDERR_PID} ${TAIL_STDOUT_PID} 2> /dev/null",
                    "                exit $EXIT_STATUS",
                    "            else",
                    "                echo \"No exit status in $EXIT_STATUS_PATH, and pid in $PID_PATH ($PID) not executing\"",
                    "                kill ${TAIL_STDERR_PID} ${TAIL_STDOUT_PID} 2> /dev/null",
                    "                exit 126",
                    "            fi",
                    "        fi",
                    "    fi",
                    "    # No exit status in $EXIT_STATUS_PATH; keep waiting",
                    "    sleep 1",
                    "    COUNTER+=1",
                    "done",
                    "kill ${TAIL_STDERR_PID} ${TAIL_STDOUT_PID} 2> /dev/null",
                    "exit 125"+"\n");
            String waitForExitStatus = Joiner.on("\n").join(waitForExitStatusParts);

            return ImmutableList.of(runAsRoot ? BashCommands.sudo(waitForExitStatus) : waitForExitStatus);
        }

        protected List<String> deleteTemporaryFilesCommand() {
            ImmutableList.Builder<String> cmdParts = ImmutableList.builder();
            
            if (!Boolean.TRUE.equals(noDeleteAfterExec)) {
                // use "-f" because some systems have "rm" aliased to "rm -i"
                // use "< /dev/null" to guarantee doesn't hang
                cmdParts.add(
                        "rm -f "+scriptPath+" "+stdoutPath+" "+stderrPath+" "+exitStatusPath+" "+pidPath+" < /dev/null");
            }
            
            // If the buildLongPollCommand didn't complete properly then it might have left tail command running;
            // ensure they are killed.
            cmdParts.add(
                    //ignore error output for the case where there are no running processes and kill is called without arguments
                    "ps aux | grep \"tail -c\" | grep \""+stdoutPath+"\" | grep -v grep | awk '{ printf $2 }' | xargs kill 2> /dev/null",
                    "ps aux | grep \"tail -c\" | grep \""+stderrPath+"\" | grep -v grep | awk '{ printf $2 }' | xargs kill 2> /dev/null");

            String cmd = Joiner.on("\n").join(cmdParts.build());
            
            return ImmutableList.of(runAsRoot ? BashCommands.sudo(cmd) : cmd);
        }

        @Override
        public abstract int run();
    }
}

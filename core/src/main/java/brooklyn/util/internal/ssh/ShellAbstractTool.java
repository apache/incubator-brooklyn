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
package brooklyn.util.internal.ssh;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.util.collections.MutableList;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.StringEscapes.BashStringEscapes;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Time;

import com.google.common.base.Objects;

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
    
    protected static <T> T getOptionalVal(Map<String,?> map, ConfigKey<T> keyC) {
        if (keyC==null) return null;
        String key = keyC.getName();
        if (map!=null && map.containsKey(key)) {
            return TypeCoercions.coerce(map.get(key), keyC.getTypeToken());
        } else {
            return keyC.getDefaultValue();
        }
    }

    /** returns the value of the key if specified, otherwise defaultValue */
    protected static <T> T getOptionalVal(Map<String,?> map, ConfigKey<T> keyC, T defaultValue) {
        String key = keyC.getName();
        if (map.containsKey(key)) {
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
        protected final String scriptPath;

        public ToolAbstractExecScript(Map<String,?> props) {
            this.props = props;
            this.separator = getOptionalVal(props, PROP_SEPARATOR);
            this.out = getOptionalVal(props, PROP_OUT_STREAM);
            this.err = getOptionalVal(props, PROP_ERR_STREAM);
            
            this.scriptDir = getOptionalVal(props, PROP_SCRIPT_DIR);
            this.runAsRoot = getOptionalVal(props, PROP_RUN_AS_ROOT);
            this.noExtraOutput = getOptionalVal(props, PROP_NO_EXTRA_OUTPUT);
            this.noDeleteAfterExec = getOptionalVal(props, PROP_NO_DELETE_SCRIPT);
            
            String summary = getOptionalVal(props, PROP_SUMMARY);
            if (summary!=null) {
                summary = Strings.makeValidFilename(summary);
                if (summary.length()>30) 
                    summary = summary.substring(0,30);
            }
            this.scriptPath = scriptDir+"/brooklyn-"+
                Time.makeDateStampString()+"-"+Identifiers.makeRandomId(4)+
                (summary==null ? "" : "-"+summary) +
                ".sh";
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

        public abstract int run();
    }
    
}

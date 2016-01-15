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
package org.apache.brooklyn.util.core.internal.ssh.cli;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.internal.ssh.SshAbstractTool;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.apache.brooklyn.util.core.internal.ssh.cli.SshCliTool;
import org.apache.brooklyn.util.core.internal.ssh.process.ProcessTool;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.text.StringEscapes.BashStringEscapes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * For ssh and scp commands, delegating to system calls.
 */
public class SshCliTool extends SshAbstractTool implements SshTool {

    // TODO No retry support, with backoffLimitedRetryHandler
    
    private static final Logger LOG = LoggerFactory.getLogger(SshCliTool.class);

    public static final ConfigKey<String> PROP_SSH_EXECUTABLE = ConfigKeys.newStringConfigKey("sshExecutable", "command to execute for ssh (defaults to \"ssh\", but could be overridden to sshg3 for Tectia for example)", "ssh");
    public static final ConfigKey<String> PROP_SSH_FLAGS = ConfigKeys.newStringConfigKey("sshFlags", "flags to pass to ssh, as a space separated list", "");
    public static final ConfigKey<String> PROP_SCP_EXECUTABLE = ConfigKeys.newStringConfigKey("scpExecutable", "command to execute for scp (defaults to \"scp\", but could be overridden to scpg3 for Tectia for example)", "scp");

    public static Builder<SshCliTool,?> builder() {
        return new ConcreteBuilder();
    }
    
    private static class ConcreteBuilder extends Builder<SshCliTool, ConcreteBuilder> {
    }
    
    public static class Builder<T extends SshCliTool, B extends Builder<T,B>> extends AbstractSshToolBuilder<T,B> {
        private String sshExecutable;
        private String sshFlags;
        private String scpExecutable;

        @Override
        public B from(Map<String,?> props) {
            super.from(props);
            sshExecutable = getOptionalVal(props, PROP_SSH_EXECUTABLE);
            sshFlags = getOptionalVal(props, PROP_SSH_FLAGS);
            scpExecutable = getOptionalVal(props, PROP_SCP_EXECUTABLE);
            return self();
        }
        public B sshExecutable(String val) {
            this.sshExecutable = val; return self();
        }
        public B scpExecutable(String val) {
            this.scpExecutable = val; return self();
        }
        @SuppressWarnings("unchecked")
        public T build() {
            return (T) new SshCliTool(this);
        }
    }

    private final String sshExecutable;
    private final String sshFlags;
    private final String scpExecutable;

    public SshCliTool(Map<String,?> map) {
        this(builder().from(map));
    }
    
    private SshCliTool(Builder<?,?> builder) {
        super(builder);
        sshExecutable = checkNotNull(builder.sshExecutable);
        sshFlags = checkNotNull(builder.sshFlags);
        scpExecutable = checkNotNull(builder.scpExecutable);
        if (LOG.isTraceEnabled()) LOG.trace("Created SshCliTool {} ({})", this, System.identityHashCode(this));
    }
    
    @Override
    public void connect() {
        // no-op
    }

    @Override
    public void connect(int maxAttempts) {
        // no-op
    }

    @Override
    public void disconnect() {
        if (LOG.isTraceEnabled()) LOG.trace("Disconnecting SshCliTool {} ({}) - no-op", this, System.identityHashCode(this));
        // no-op
    }

    @Override
    public boolean isConnected() {
        // TODO Always pretends to be connected
        return true;
    }

    @Override
    public int copyToServer(java.util.Map<String,?> props, byte[] contents, String pathAndFileOnRemoteServer) {
        return copyTempFileToServer(props, writeTempFile(contents), pathAndFileOnRemoteServer);
    }
    
    @Override
    public int copyToServer(java.util.Map<String,?> props, InputStream contents, String pathAndFileOnRemoteServer) {
        return copyTempFileToServer(props, writeTempFile(contents), pathAndFileOnRemoteServer);
    }
    
    @Override
    public int copyToServer(Map<String,?> props, File f, String pathAndFileOnRemoteServer) {
        if (hasVal(props, PROP_LAST_MODIFICATION_DATE)) {
            LOG.warn("Unsupported ssh feature, setting lastModificationDate for {}:{}", this, pathAndFileOnRemoteServer);
        }
        if (hasVal(props, PROP_LAST_ACCESS_DATE)) {
            LOG.warn("Unsupported ssh feature, setting lastAccessDate for {}:{}", this, pathAndFileOnRemoteServer);
        }
        String permissions = getOptionalVal(props, PROP_PERMISSIONS);
        
        int uid = getOptionalVal(props, PROP_OWNER_UID);
        
        int result = scpToServer(props, f, pathAndFileOnRemoteServer);
        if (result == 0) {
            result = chmodOnServer(props, permissions, pathAndFileOnRemoteServer);
            if (result == 0) {
                if (uid != -1) {
                    result = chownOnServer(props, uid, pathAndFileOnRemoteServer);
                    if (result != 0) {
                        LOG.warn("Error setting file owner to {}, after copying file {} to {}:{}; exit code {}", new Object[] { uid, pathAndFileOnRemoteServer, this, f, result });
                    }
                }
            } else {
                LOG.warn("Error setting file permissions to {}, after copying file {} to {}:{}; exit code {}", new Object[] { permissions, pathAndFileOnRemoteServer, this, f, result });
            }
        } else {
            LOG.warn("Error copying file {} to {}:{}; exit code {}", new Object[] {pathAndFileOnRemoteServer, this, f, result});
        }
        return result;
    }

    private int chownOnServer(Map<String,?> props, int uid, String remote) {
        return sshExec(props, "chown "+uid+" "+remote);
    }
    
    private int copyTempFileToServer(Map<String,?> props, File f, String pathAndFileOnRemoteServer) {
        try {
            return copyToServer(props, f, pathAndFileOnRemoteServer);
        } finally {
            f.delete();
        }
    }

    @Override
    public int copyFromServer(Map<String,?> props, String pathAndFileOnRemoteServer, File localFile) {
        return scpFromServer(props, pathAndFileOnRemoteServer, localFile);
    }

    @Override
    public int execScript(final Map<String,?> props, final List<String> commands, final Map<String,?> env) {
        return new ToolAbstractExecScript(props) {
            public int run() {
                String scriptContents = toScript(props, commands, env);
                if (LOG.isTraceEnabled()) LOG.trace("Running shell command at {} as script: {}", host, scriptContents);
                copyTempFileToServer(ImmutableMap.of("permissions", "0700"), writeTempFile(scriptContents), scriptPath);

                String cmd = Strings.join(buildRunScriptCommand(), separator);
                return asInt(sshExec(props, cmd), -1);
            }
        }.run();
    }

    @Override
    public int execCommands(Map<String,?> props, List<String> commands, Map<String,?> env) {
        Map<String,Object> props2 = new MutableMap<String,Object>();
        if (props!=null) props2.putAll(props);
        props2.put(SshTool.PROP_NO_EXTRA_OUTPUT.getName(), true);
        return execScript(props2, commands, env);
    }
    
    private int scpToServer(Map<String,?> props, File local, String remote) {
        String to = (Strings.isEmpty(getUsername()) ? "" : getUsername()+"@")+getHostAddress()+":"+remote;
        return scpExec(props, local.getAbsolutePath(), to);
    }

    private int scpFromServer(Map<String,?> props, String remote, File local) {
        String from = (Strings.isEmpty(getUsername()) ? "" : getUsername()+"@")+getHostAddress()+":"+remote;
        return scpExec(props, from, local.getAbsolutePath());
    }
    
    private int chmodOnServer(Map<String,?> props, String permissions, String remote) {
        return sshExec(props, "chmod "+permissions+" "+remote);
    }

    private int scpExec(Map<String,?> props, String from, String to) {
        File tempFile = null;
        try {
            List<String> cmd = Lists.newArrayList();
            cmd.add(getOptionalVal(props, PROP_SCP_EXECUTABLE, scpExecutable));
            if (privateKeyFile != null) {
                cmd.add("-i");
                cmd.add(privateKeyFile.getAbsolutePath());
            } else if (privateKeyData != null) {
                tempFile = writeTempFile(privateKeyData);
                cmd.add("-i");
                cmd.add(tempFile.getAbsolutePath());
            }
            if (!strictHostKeyChecking) {
                cmd.add("-o");
                cmd.add("StrictHostKeyChecking=no");
            }
            if (port != 22) {
                cmd.add("-P");
                cmd.add(""+port);
            }
            cmd.add(from);
            cmd.add(to);
            
            if (LOG.isTraceEnabled()) LOG.trace("Executing with command: {}", cmd);
            int result = execProcess(props, cmd);
            
            if (LOG.isTraceEnabled()) LOG.trace("Executed command: {}; exit code {}", cmd, result);
            return result;

        } finally {
            if (tempFile != null) tempFile.delete();
        }
    }
    
    private int sshExec(Map<String,?> props, String command) {
        File tempKeyFile = null;
        try {
            List<String> cmd = Lists.newArrayList();
            cmd.add(getOptionalVal(props, PROP_SSH_EXECUTABLE, sshExecutable));
            String propsFlags = getOptionalVal(props, PROP_SSH_FLAGS, sshFlags);
            if (propsFlags!=null && propsFlags.trim().length()>0)
                cmd.addAll(Arrays.asList(propsFlags.trim().split(" ")));
            if (privateKeyFile != null) {
                cmd.add("-i");
                cmd.add(privateKeyFile.getAbsolutePath());
            } else if (privateKeyData != null) {
                tempKeyFile = writeTempFile(privateKeyData);
                cmd.add("-i");
                cmd.add(tempKeyFile.getAbsolutePath());
            }
            if (!strictHostKeyChecking) {
                cmd.add("-o");
                cmd.add("StrictHostKeyChecking=no");
            }
            if (port != 22) {
                cmd.add("-P");
                cmd.add(""+port);
            }
            if (allocatePTY) {
                // have to be careful with double -tt as it can leave a shell session active
                // when done from bash (ie  ssh -tt localhost < /tmp/myscript.sh);
                // hover that doesn't seem to be a problem the way we use it from brooklyn
                // (and note single -t doesn't work _programmatically_ since the input isn't a terminal)
                cmd.add("-tt");
            }
            cmd.add((Strings.isEmpty(getUsername()) ? "" : getUsername()+"@")+getHostAddress());
            
            cmd.add("bash -c "+BashStringEscapes.wrapBash(command));
            // previously we tried these approaches:
            //cmd.add("$(<"+tempCmdFile.getAbsolutePath()+")");
            // only pays attention to the first word; the "; echo Executing ..." get treated as arguments
            // to the script in the first word, when invoked from java (when invoked from prompt the behaviour is as desired)
            //cmd.add("\""+command+"\"");
            // only works if command is a single word
            //cmd.add(tempCmdFile.getAbsolutePath());
            // above of course only works if the metafile is copied across
            
            if (LOG.isTraceEnabled()) LOG.trace("Executing ssh with command: {} (with {})", command, cmd);
            int result = execProcess(props, cmd);
            
            if (LOG.isTraceEnabled()) LOG.trace("Executed command: {}; exit code {}", cmd, result);
            return result;
            
        } finally {
            if (tempKeyFile != null) tempKeyFile.delete();
        }
    }

    private int execProcess(Map<String,?> props, List<String> cmdWords) {
        OutputStream out = getOptionalVal(props, PROP_OUT_STREAM);
        OutputStream err = getOptionalVal(props, PROP_ERR_STREAM);
        return ProcessTool.execSingleProcess(cmdWords, null, (File)null, out, err, this);
    }
}

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
package brooklyn.util.internal.ssh.cli;

import static brooklyn.util.NetworkUtils.checkPortValid;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.internal.StreamGobbler;
import brooklyn.util.internal.ssh.BackoffLimitedRetryHandler;
import brooklyn.util.internal.ssh.SshException;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.StringEscapes.BashStringEscapes;
import brooklyn.util.text.Strings;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * For ssh and scp commands, delegating to system calls.
 */
public class SshCliTool implements SshTool {

    // TODO No retry support, with backoffLimitedRetryHandler
    
    private static final Logger LOG = LoggerFactory.getLogger(SshCliTool.class);

    private final String toString;

    private final String host;
    private final String user;
    private final String password;
    private final int port;
    private String privateKeyPassphrase;
    private String privateKeyData;
    private File privateKeyFile;
    private boolean strictHostKeyChecking;
    private boolean allocatePTY;
    private File localTempDir;

    public static Builder builder() {
        return new Builder();
    }
    
    private static void warnOnDeprecated(Map<String, ?> props, String deprecatedKey, String correctKey) {
        if (props.containsKey(deprecatedKey)) {
            if (correctKey != null && props.containsKey(correctKey)) {
                Object dv = props.get(deprecatedKey);
                Object cv = props.get(correctKey);
                if (!Objects.equal(cv, dv)) {
                    LOG.warn("SshjTool detected deprecated key '"+deprecatedKey+"' with different value ("+dv+") "+
                            "than new key '"+correctKey+"' ("+cv+"); ambiguous which will be used");
                } else {
                    // ignore, the deprecated key populated for legacy reasons
                }
            } else {
                Object dv = props.get(deprecatedKey);
                LOG.warn("SshjTool detected deprecated key '"+deprecatedKey+"' used, with value ("+dv+")");     
            }
        }
    }

    public static class Builder {
        private String host;
        private int port = 22;
        private String user = System.getProperty("user.name");
        private String password;
        private String privateKeyData;
        public String privateKeyPassphrase;
        private Set<String> privateKeyFiles = Sets.newLinkedHashSet();
        private boolean strictHostKeyChecking = false;
        private boolean allocatePTY = false;
        private int connectTimeout;
        private int sessionTimeout;
        private int sshTries = 4;  //allow 4 tries by default, much safer
        private int sshTriesTimeout = 2*60*1000;  //allow 2 minutesby default (so if too slow trying sshTries times, abort anyway)
        private long sshRetryDelay = 50L;
        private File localTempDir = new File(System.getProperty("java.io.tmpdir"), "tmpssh");

        @SuppressWarnings("unchecked")
        public Builder from(Map<String,?> props) {
            host = getMandatoryVal(props, "host", String.class);
            port = getOptionalVal(props, "port", Integer.class, port);
            user = getOptionalVal(props, "user", String.class, user);
            
            password = getOptionalVal(props, "password", String.class, password);
            
            warnOnDeprecated(props, "privateKey", "privateKeyData");
            privateKeyData = getOptionalVal(props, "privateKey", String.class, privateKeyData);
            privateKeyData = getOptionalVal(props, "privateKeyData", String.class, privateKeyData);
            privateKeyPassphrase = getOptionalVal(props, "privateKeyPassphrase", String.class, privateKeyPassphrase);
            
            // for backwards compatibility accept keyFiles and privateKey
            // but sshj accepts only a single privateKeyFile; leave blank to use defaults (i.e. ~/.ssh/id_rsa and id_dsa)
            warnOnDeprecated(props, "keyFiles", null);
            privateKeyFiles.addAll(getOptionalVal(props, "keyFiles", List.class, Collections.emptyList()));
            String privateKeyFile = getOptionalVal(props, "privateKeyFile", String.class, null);
            if (privateKeyFile != null) privateKeyFiles.add(privateKeyFile);
            
            strictHostKeyChecking = getOptionalVal(props, "strictHostKeyChecking", Boolean.class, strictHostKeyChecking);
            allocatePTY = getOptionalVal(props, "allocatePTY", Boolean.class, allocatePTY);
            connectTimeout = getOptionalVal(props, "connectTimeout", Integer.class, connectTimeout);
            sessionTimeout = getOptionalVal(props, "sessionTimeout", Integer.class, sessionTimeout);
            sshTries = getOptionalVal(props, "sshTries", Integer.class, sshTries);
            sshTriesTimeout = getOptionalVal(props, "sshTriesTimeout", Integer.class, sshTriesTimeout);
            sshRetryDelay = getOptionalVal(props, "sshRetryDelay", Long.class, sshRetryDelay);
            
            localTempDir = getOptionalVal(props, "localTempDir", File.class, localTempDir);
            
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
        public Builder privateKeyPassphrase(String val) {
            this.privateKeyPassphrase = val; return this;
        }
        /** @deprecated 1.4.0, use privateKeyData */
        public Builder privateKey(String val) {
            this.privateKeyData = val; return this;
        }
        public Builder privateKeyData(String val) {
            this.privateKeyData = val; return this;
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
        public Builder sshRetriesTimeout(int val) {
            this.sshTriesTimeout = val; return this;
        }
        public Builder sshRetryDelay(long val) {
            this.sshRetryDelay = val; return this;
        }
        public Builder localTempDir(File val) {
            this.localTempDir = val; return this;
        }
        public SshCliTool build() {
            return new SshCliTool(this);
        }
    }

    public SshCliTool(Map<String,?> map) {
        this(builder().from(map));
    }
    
    private SshCliTool(Builder builder) {
        host = checkNotNull(builder.host, "host");
        port = builder.port;
        user = builder.user;
        password = builder.password;
        strictHostKeyChecking = builder.strictHostKeyChecking;
        allocatePTY = builder.allocatePTY;
//        sshTries = builder.sshTries;
//        sshTriesTimeout = builder.sshTriesTimeout;
//        backoffLimitedRetryHandler = new BackoffLimitedRetryHandler(sshTries, builder.sshRetryDelay);
        privateKeyPassphrase = builder.privateKeyPassphrase;
        privateKeyData = builder.privateKeyData;
        localTempDir = builder.localTempDir;
        
        if (builder.privateKeyFiles.size() > 1) {
            throw new IllegalArgumentException("sshj supports only a single private key-file; " +
                    "for defaults of ~/.ssh/id_rsa and ~/.ssh/id_dsa leave blank");
        } else if (builder.privateKeyFiles.size() == 1) {
            String privateKeyFileStr = Iterables.get(builder.privateKeyFiles, 0);
            String amendedKeyFile = privateKeyFileStr.startsWith("~") ? (System.getProperty("user.home")+privateKeyFileStr.substring(1)) : privateKeyFileStr;
            privateKeyFile = new File(amendedKeyFile);
        } else {
            privateKeyFile = null;
        }
        
        checkArgument(host.length() > 0, "host value must not be an empty string");
        checkPortValid(port, "ssh port");

        toString = String.format("%s@%s:%d", user, host, port);
        
        if (LOG.isTraceEnabled()) LOG.trace("Created SshCliTool {} ({})", this, System.identityHashCode(this));
    }
    
    public String getHostAddress() {
        return this.host;
    }

    public String getUsername() {
        return this.user;
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

    private File writeTempFile(String contents) {
        return writeTempFile(contents.getBytes());
    }

    private File writeTempFile(byte[] contents) {
        return writeTempFile(new ByteArrayInputStream(contents));
    }

    @Override
    public int transferFileTo(Map<String,?> props, InputStream input, String pathAndFileOnRemoteServer) {
        return copyTempFileToServer(props, writeTempFile(input), pathAndFileOnRemoteServer);
    }
    
    @Override
    public int createFile(Map<String,?> props, String pathAndFileOnRemoteServer, InputStream input, long size) {
        return copyTempFileToServer(props, writeTempFile(input), pathAndFileOnRemoteServer);
    }

    @Override
    public int createFile(Map<String,?> props, String pathAndFileOnRemoteServer, String contents) {
        return copyTempFileToServer(props, writeTempFile(contents), pathAndFileOnRemoteServer);
    }

    /** Creates the given file with the given contents.
     *
     * Permissions specified using 'permissions:0755'.
     */
    @Override
    public int createFile(Map<String,?> props, String pathAndFileOnRemoteServer, byte[] contents) {
        return copyTempFileToServer(props, writeTempFile(contents), pathAndFileOnRemoteServer);
    }

    @Override
    public int copyToServer(Map<String,?> props, File f, String pathAndFileOnRemoteServer) {
        if (props.containsKey("lastModificationDate")) {
            LOG.warn("Unsupported ssh feature, setting lastModificationDate for {}:{}", this, pathAndFileOnRemoteServer);
        }
        if (props.containsKey("lastAccessDate")) {
            LOG.warn("Unsupported ssh feature, setting lastAccessDate for {}:{}", this, pathAndFileOnRemoteServer);
        }
        String permissions = getOptionalVal(props, "permissions", String.class, "0644");
        
        int result = scpToServer(props, f, pathAndFileOnRemoteServer);
        if (result == 0) {
            result = chmodOnServer(props, permissions, pathAndFileOnRemoteServer);
            if (result != 0) {
                LOG.warn("Error setting file permissions to {}, after copying file {} to {}:{}; exit code {}", new Object[] {permissions, pathAndFileOnRemoteServer, this, f, result});
            }
        } else {
            LOG.warn("Error copying file {} to {}:{}; exit code {}", new Object[] {pathAndFileOnRemoteServer, this, f, result});
        }
        return result;
    }

    private int copyTempFileToServer(Map<String,?> props, File f, String pathAndFileOnRemoteServer) {
        try {
            return copyToServer(props, f, pathAndFileOnRemoteServer);
        } finally {
            f.delete();
        }
    }

    @Override
    public int transferFileFrom(Map<String,?> props, String pathAndFileOnRemoteServer, String pathAndFileOnLocalServer) {
        return scpFromServer(props, pathAndFileOnRemoteServer, new File(pathAndFileOnLocalServer));
    }

    @Override
    public int execShell(Map<String,?> props, List<String> commands) {
        return execScript(props, commands, Collections.<String,Object>emptyMap());
    }
    
    @Override
    public int execShell(Map<String,?> props, List<String> commands, Map<String,?> env) {
        return execScript(props, commands, env);
    }

    @Override
    public int execScript(Map<String,?> props, List<String> commands) {
        return execScript(props, commands, Collections.<String,Object>emptyMap());
    }
    
    @Override
    public int execScript(Map<String,?> props, List<String> commands, Map<String,?> env) {
        String separator = getOptionalVal(props, "separator", String.class, ";");
        String scriptDir = getOptionalVal(props, "scriptDir", String.class, "/tmp");
        String scriptPath = scriptDir+"/brooklyn-"+System.currentTimeMillis()+"-"+Identifiers.makeRandomId(8)+".sh";
        String scriptContents = toScript(commands, env);
        
        if (LOG.isTraceEnabled()) LOG.trace("Running shell command at {} as script: {}", host, scriptContents);
        
        copyTempFileToServer(ImmutableMap.of("permissions", "0700"), writeTempFile(scriptContents), scriptPath);
        
        // use "-f" because some systems have "rm" aliased to "rm -i"; use "< /dev/null" to guarantee doesn't hang
        String cmd = 
                scriptPath+" < /dev/null"+separator+
                "RESULT=$?"+separator+
                "echo Executed "+scriptPath+", result $RESULT"+separator+ 
                "rm -f "+scriptPath+" < /dev/null"+separator+
                "exit $RESULT";
        
        Integer result = ssh(props, cmd);
        return result != null ? result : -1;
    }

    @Override
    public int execCommands(Map<String,?> props, List<String> commands) {
        return execCommands(props, commands, Collections.<String,Object>emptyMap());
    }

    @Override
    public int execCommands(Map<String,?> props, List<String> commands, Map<String,?> env) {
        return execScript(props, commands, env);
    }

    private String toScript(List<String> commands, Map<String,?> env) {
        List<String> allcmds = toCommandSequence(commands, env);
        
        StringBuilder result = new StringBuilder();
        // -e causes it to fail on any command in the script which has an error (non-zero return code)
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
            if (entry.getKey() == null || entry.getValue() == null) {
                LOG.warn("env key-values must not be null; ignoring: key="+entry.getKey()+"; value="+entry.getValue());
                continue;
            }
            String escapedVal = BashStringEscapes.escapeLiteralForDoubleQuotedBash(entry.getValue().toString());
            result.add("export "+entry.getKey()+"=\""+escapedVal+"\"");
        }
        
        for (CharSequence cmd : commands) { // objects in commands can be groovy GString so can't treat as String here
            result.add(cmd.toString());
        }

        return result;
    }

    private SshException propagate(Exception e, String message) throws SshException {
        throw new SshException("(" + toString() + ") " + message + ":" + e.getMessage(), e);
    }
    
    @Override
    public String toString() {
        return toString;
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
    
    private File writeTempFile(InputStream contents) {
        // TODO Use ConfigKeys.BROOKLYN_DATA_DIR, but how to get access to that here?
        File tempFile = ResourceUtils.writeToTempFile(contents, localTempDir, "sshcopy", "data");
        tempFile.setReadable(false, false);
        tempFile.setReadable(true, true);
        tempFile.setWritable(false);
        tempFile.setExecutable(false);
        return tempFile;
    }
    
    private int scpToServer(Map<String,?> props, File local, String remote) {
        File tempFile = null;
        try {
            List<String> cmd = Lists.newArrayList();
            cmd.add("scp");
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
            cmd.add(local.getAbsolutePath());
            cmd.add((Strings.isEmpty(getUsername()) ? "" : getUsername()+"@")+getHostAddress()+":"+remote);
            
            if (LOG.isTraceEnabled()) LOG.trace("Executing with command: {}", cmd);
            int result = execProcess(props, cmd);
            
            if (LOG.isTraceEnabled()) LOG.trace("Executed command: {}; exit code {}", cmd, result);
            return result;
            
        } finally {
            if (tempFile != null) tempFile.delete();
        }
    }

    private int scpFromServer(Map<String,?> props, String remote, File local) {
        File tempFile = null;
        try {
            List<String> cmd = Lists.newArrayList();
            cmd.add("scp");
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
            cmd.add((Strings.isEmpty(getUsername()) ? "" : getUsername()+"@")+getHostAddress()+":"+remote);
            cmd.add(local.getAbsolutePath());
            
            if (LOG.isTraceEnabled()) LOG.trace("Executing with command: {}", cmd);
            int result = execProcess(props, cmd);
            
            if (LOG.isTraceEnabled()) LOG.trace("Executed command: {}; exit code {}", cmd, result);
            return result;

        } finally {
            if (tempFile != null) tempFile.delete();
        }
    }
    
    private int chmodOnServer(Map<String,?> props, String permissions, String remote) {
        return ssh(props, "chmod "+permissions+" "+remote);
    }
    
    private int ssh(Map<String,?> props, String command) {
        File tempCmdFile = writeTempFile(command);
        File tempKeyFile = null;
        try {
            List<String> cmd = Lists.newArrayList();
            cmd.add("ssh");
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
            cmd.add((Strings.isEmpty(getUsername()) ? "" : getUsername()+"@")+getHostAddress());
            cmd.add("$(<"+tempCmdFile.getAbsolutePath()+")");
            //cmd.add("\""+command+"\"");
            
            if (LOG.isTraceEnabled()) LOG.trace("Executing ssh with command: {} (with {})", command, cmd);
            int result = execProcess(props, cmd);
            
            if (LOG.isTraceEnabled()) LOG.trace("Executed command: {}; exit code {}", cmd, result);
            return result;
            
        } finally {
            tempCmdFile.delete();
            if (tempKeyFile != null) tempKeyFile.delete();
        }
    }
    
    private int execProcess(Map<String,?> props, List<String> cmd) {
        OutputStream out = getOptionalVal(props, "out", OutputStream.class, null);
        OutputStream err = getOptionalVal(props, "err", OutputStream.class, null);
        StreamGobbler errgobbler = null;
        StreamGobbler outgobbler = null;
        
        ProcessBuilder pb = new ProcessBuilder(cmd);

        try {
            Process p = pb.start();
            
            if (true) {// FIXME
//            if (out != null) {
                InputStream outstream = p.getInputStream();
                outgobbler = new StreamGobbler(outstream, out, LOG).setLogPrefix("[stdout] ");// FIXME (Logger) null);
                outgobbler.start();
            }
            if (true) {// FIXME
//            if (err != null) {
                InputStream errstream = p.getErrorStream();
                errgobbler = new StreamGobbler(errstream, err, LOG).setLogPrefix("[stdout] ");// FIXME (Logger) null);
                errgobbler.start();
            }
            
            return p.waitFor();
            
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        } finally {
            closeWhispering(outgobbler, this);
            closeWhispering(errgobbler, this);
        }
    }
    
    /**
     * Similar to Guava's Closeables.closeQuitely, except logs exception at debug with context in message.
     */
    private void closeWhispering(Closeable closeable, Object context) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                if (LOG.isDebugEnabled()) {
                    String msg = String.format("<< exception during close, for %s -> %s (%s); continuing.", 
                            SshCliTool.this.toString(), context, closeable);
                    LOG.debug(msg, e);
                }
            }
        }
    }
}

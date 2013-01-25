package brooklyn.util.internal.ssh;

import static brooklyn.util.NetworkUtils.checkPortValid;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.jclouds.io.Payload;
import org.jclouds.io.payloads.ByteArrayPayload;
import org.jclouds.io.payloads.InputStreamPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.util.ResourceUtils;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.text.StringEscapes.BashStringEscapes;

import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.LimitInputStream;

public abstract class SshAbstractTool implements SshTool {

    private static final Logger LOG = LoggerFactory.getLogger(SshAbstractTool.class);
    
    protected final String toString;

    protected final String host;
    protected final String user;
    protected final String password;
    protected final int port;
    protected String privateKeyPassphrase;
    protected String privateKeyData;
    protected File privateKeyFile;
    protected boolean strictHostKeyChecking;
    protected boolean allocatePTY;
    protected File localTempDir;

    public static interface SshAction<T> {
        void clear() throws Exception;
        T create() throws Exception;
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

    public static abstract class AbstractToolBuilder<T extends SshTool> {
        protected String host;
        protected int port = 22;
        protected String user = System.getProperty("user.name");
        protected String password;
        protected String privateKeyData;
        protected String privateKeyPassphrase;
        protected Set<String> privateKeyFiles = Sets.newLinkedHashSet();
        protected boolean strictHostKeyChecking = false;
        protected boolean allocatePTY = false;
        protected File localTempDir = new File(System.getProperty("java.io.tmpdir"), "tmpssh");

        @SuppressWarnings("unchecked")
        public AbstractToolBuilder<T> from(Map<String,?> props) {
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
            
            localTempDir = getOptionalVal(props, "localTempDir", File.class, localTempDir);
            
            return this;
        }
        public AbstractToolBuilder<T> host(String val) {
            this.host = val; return this;
        }
        public AbstractToolBuilder<T> user(String val) {
            this.user = val; return this;
        }
        public AbstractToolBuilder<T> password(String val) {
            this.password = val; return this;
        }
        public AbstractToolBuilder<T> port(int val) {
            this.port = val; return this;
        }
        public AbstractToolBuilder<T> privateKeyPassphrase(String val) {
            this.privateKeyPassphrase = val; return this;
        }
        /** @deprecated 1.4.0, use privateKeyData */
        public AbstractToolBuilder<T> privateKey(String val) {
            this.privateKeyData = val; return this;
        }
        public AbstractToolBuilder<T> privateKeyData(String val) {
            this.privateKeyData = val; return this;
        }
        public AbstractToolBuilder<T> privateKeyFile(String val) {
            this.privateKeyFiles.add(val); return this;
        }
        public AbstractToolBuilder<T> localTempDir(File val) {
            this.localTempDir = val; return this;
        }
        public abstract T build();
    }

    protected SshAbstractTool(AbstractToolBuilder<?> builder) {
        // TODO Does this need to be ported from SshJschTool?
//        if (host && host==~ /[^@]+@[^@]+/) {
//            (user,host) = (host=~/([^@]+)@([^@]+)/)[0][1,2]
//        }

        host = checkNotNull(builder.host, "host");
        port = builder.port;
        user = builder.user;
        password = builder.password;
        strictHostKeyChecking = builder.strictHostKeyChecking;
        allocatePTY = builder.allocatePTY;
        privateKeyPassphrase = builder.privateKeyPassphrase;
        privateKeyData = builder.privateKeyData;
        
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

        localTempDir = builder.localTempDir;
        checkNotNull(localTempDir, "localTempDir");
        
        toString = String.format("%s@%s:%d", user, host, port);
    }

    
    
    protected Payload toPayload(InputStream input, long length) {
        InputStreamPayload payload = new InputStreamPayload(new LimitInputStream(input, length));
        payload.getContentMetadata().setContentLength(length);
        return payload;
    }
    
    protected Payload toPayload(InputStream input) {
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

    protected static <T> T getMandatoryVal(Map<String,?> map, ConfigKey<T> keyC) {
        String key = keyC.getName();
        checkArgument(map.containsKey(key), "must contain key '"+keyC+"'");
        return TypeCoercions.coerce(map.get(key), keyC.getType());
    }
    
    protected static <T> T getOptionalVal(Map<String,?> map, ConfigKey<T> keyC) {
        String key = keyC.getName();
        if (map.containsKey(key)) {
            return TypeCoercions.coerce(map.get(key), keyC.getType());
        } else {
            return keyC.getDefaultValue();
        }
    }

    /** returns the value of the key if specified, otherwise defaultValue */
    protected static <T> T getOptionalVal(Map<String,?> map, ConfigKey<T> keyC, T defaultValue) {
        String key = keyC.getName();
        if (map.containsKey(key)) {
            return TypeCoercions.coerce(map.get(key), keyC.getType());
        } else {
            return defaultValue;
        }
    }

    /** @deprecated since 0.5.0 use ConfigKey variant */
    @Deprecated
    protected static <T> T getMandatoryVal(Map<String,?> map, String key, Class<T> clazz) {
        checkArgument(map.containsKey(key), "must contain key '"+key+"'");
        return TypeCoercions.coerce(map.get(key), clazz);
    }
    
    /** @deprecated since 0.5.0 use ConfigKey variant */
    @Deprecated
    protected static <T> T getOptionalVal(Map<String,?> map, String key, Class<T> clazz, T defaultVal) {
        if (map.containsKey(key)) {
            return TypeCoercions.coerce(map.get(key), clazz);
        } else {
            return defaultVal;
        }
    }
    
    /**
     * Similar to Guava's Closeables.closeQuitely, except logs exception at debug with context in message.
     */
    protected void closeWhispering(Closeable closeable, Object context) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                if (LOG.isDebugEnabled()) {
                    String msg = String.format("<< exception during close, for %s -> %s (%s); continuing.", 
                            SshAbstractTool.this.toString(), context, closeable);
                    LOG.debug(msg, e);
                }
            }
        }
    }

    protected SshException propagate(Exception e, String message) throws SshException {
        throw new SshException("(" + toString() + ") " + message + ":" + e.getMessage(), e);
    }
    
    @Override
    public String toString() {
        return toString;
    }

    protected File writeTempFile(InputStream contents) {
        // TODO Use ConfigKeys.BROOKLYN_DATA_DIR, but how to get access to that here?
        File tempFile = ResourceUtils.writeToTempFile(contents, localTempDir, "sshcopy", "data");
        tempFile.setReadable(false, false);
        tempFile.setReadable(true, true);
        tempFile.setWritable(false);
        tempFile.setExecutable(false);
        return tempFile;
    }

    public String getHostAddress() {
        return this.host;
    }

    public String getUsername() {
        return this.user;
    }

    protected String toScript(Map<String,?> props, List<String> commands, Map<String,?> env) {
        List<String> allcmds = toCommandSequence(commands, env);
        
        StringBuilder result = new StringBuilder();
        // -e causes it to fail on any command in the script which has an error (non-zero return code)
        result.append(getOptionalVal(props, PROP_SCRIPT_HEADER)+"\n");
        
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

}

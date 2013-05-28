package brooklyn.util.internal.ssh;

import static brooklyn.util.NetworkUtils.checkPortValid;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.util.ResourceUtils;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.text.StringEscapes.BashStringEscapes;

import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

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

    public static abstract class AbstractToolBuilder<T extends SshTool, B extends AbstractToolBuilder<T,B>> {
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
        protected B self() {
           return (B) this;
        }

        @SuppressWarnings("unchecked")
        public B from(Map<String,?> props) {
            host = getMandatoryVal(props, PROP_HOST);
            port = getOptionalVal(props, PROP_PORT);
            user = getOptionalVal(props, PROP_USER);
            
            password = getOptionalVal(props, PROP_PASSWORD);
            
            warnOnDeprecated(props, "privateKey", "privateKeyData");
            privateKeyData = getOptionalVal(props, PROP_PRIVATE_KEY_DATA);
            privateKeyPassphrase = getOptionalVal(props, PROP_PRIVATE_KEY_PASSPHRASE);
            
            // for backwards compatibility accept keyFiles and privateKey
            // but sshj accepts only a single privateKeyFile; leave blank to use defaults (i.e. ~/.ssh/id_rsa and id_dsa)
            warnOnDeprecated(props, "keyFiles", null);
            String privateKeyFile = getOptionalVal(props, PROP_PRIVATE_KEY_FILE);
            if (privateKeyFile != null) privateKeyFiles.add(privateKeyFile);
            
            strictHostKeyChecking = getOptionalVal(props, PROP_STRICT_HOST_KEY_CHECKING);
            allocatePTY = getOptionalVal(props, PROP_ALLOCATE_PTY);
            
            localTempDir = getOptionalVal(props, PROP_LOCAL_TEMP_DIR);
            
            return self();
        }
        public B host(String val) {
            this.host = val; return self();
        }
        public B user(String val) {
            this.user = val; return self();
        }
        public B password(String val) {
            this.password = val; return self();
        }
        public B port(int val) {
            this.port = val; return self();
        }
        public B privateKeyPassphrase(String val) {
            this.privateKeyPassphrase = val; return self();
        }
        /** @deprecated 1.4.0, use privateKeyData */
        public B privateKey(String val) {
            this.privateKeyData = val; return self();
        }
        public B privateKeyData(String val) {
            this.privateKeyData = val; return self();
        }
        public B privateKeyFile(String val) {
            this.privateKeyFiles.add(val); return self();
        }
        public B localTempDir(File val) {
            this.localTempDir = val; return self();
        }
        public abstract T build();
    }

    protected SshAbstractTool(AbstractToolBuilder<?,?> builder) {
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

    @Override
    public String toString() {
        return toString;
    }

    protected static Boolean hasVal(Map<String,?> map, ConfigKey<?> keyC) {
        String key = keyC.getName();
        return map.containsKey(key);
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
    
    protected File writeTempFile(InputStream contents) {
        // TODO Use ConfigKeys.BROOKLYN_DATA_DIR, but how to get access to that here?
        File tempFile = ResourceUtils.writeToTempFile(contents, localTempDir, "sshcopy", "data");
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

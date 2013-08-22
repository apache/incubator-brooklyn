package brooklyn.util.internal.ssh;

import static brooklyn.util.net.Networking.checkPortValid;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public abstract class SshAbstractTool extends ShellAbstractTool implements SshTool {

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

    public static interface SshAction<T> {
        void clear() throws Exception;
        T create() throws Exception;
    }

    public static abstract class AbstractSshToolBuilder<T extends SshTool, B extends AbstractSshToolBuilder<T,B>> {
        protected String host;
        protected int port = 22;
        protected String user = System.getProperty("user.name");
        protected String password;
        protected String privateKeyData;
        protected String privateKeyPassphrase;
        protected Set<String> privateKeyFiles = Sets.newLinkedHashSet();
        protected boolean strictHostKeyChecking = false;
        protected boolean allocatePTY = false;
        protected File localTempDir = null;

        @SuppressWarnings("unchecked")
        protected B self() {
           return (B) this;
        }

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

    protected SshAbstractTool(AbstractSshToolBuilder<?,?> builder) {
        super(builder.localTempDir);
        
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
        
        toString = String.format("%s@%s:%d", user, host, port);
    }

    @Override
    public String toString() {
        return toString;
    }

    public String getHostAddress() {
        return this.host;
    }

    public String getUsername() {
        return this.user;
    }

    protected SshException propagate(Exception e, String message) throws SshException {
        throw new SshException("(" + toString() + ") " + message + ":" + e.getMessage(), e);
    }
    
}

package brooklyn.entity.java;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.util.Exceptions;
import brooklyn.util.MutableMap.Builder;
import brooklyn.util.ResourceUtils;
import brooklyn.util.crypto.FluentKeySigner;
import brooklyn.util.crypto.SecureKeys;
import brooklyn.util.jmx.jmxmp.JmxmpAgent;

public class JmxmpSslSupport {

    final static String BROOKLYN_VERSION = "0.5.0-SNAPSHOT";  // BROOKLYN_VERSION (updated by script)
    
    protected final JavaSoftwareProcessSshDriver driver;
    
    // for tests
    private KeyStore agentTrustStore;
    private KeyStore agentKeyStore;

    public JmxmpSslSupport(JavaSoftwareProcessSshDriver driver) {
        this.driver = driver;
    }
    
    public String getJmxSslKeyStoreFilePath() {
        return driver.getRunDir()+"/jmx-keystore";
    }
    
    public String getJmxSslTrustStoreFilePath() {
        return driver.getRunDir()+"/jmx-truststore";
    }
    
    public String getJmxmpAgentJarBasename() {
        return "brooklyn-jmxmp-agent-shaded-"+BROOKLYN_VERSION+".jar";
    }

    public String getJmxmpAgentJarUrl() {
        return "classpath://"+getJmxmpAgentJarBasename();
    }

    public String getJmxmpAgentJarDestinationFilePath() {
        return driver.getRunDir()+"/"+getJmxmpAgentJarBasename();
    }

    public void applyAgentJmxJavaSystemProperties(Builder<String, Object> result) {
        result.
            put(JmxmpAgent.JMXMP_PORT_PROPERTY, driver.getJmxPort()).
            put(JmxmpAgent.USE_SSL_PROPERTY, true).
            put(JmxmpAgent.AUTHENTICATE_CLIENTS_PROPERTY, true).
            // the option below wants a jmxremote.password file; we use certs (above) to authenticate
            put("com.sun.management.jmxremote.authenticate", false);

        // TODO reference the properties below
        result.
            put(JmxmpAgent.JMXMP_KEYSTORE_FILE_PROPERTY, getJmxSslKeyStoreFilePath()).
            put(JmxmpAgent.JMXMP_TRUSTSTORE_FILE_PROPERTY, getJmxSslTrustStoreFilePath());
    }

    public void applyAgentJmxJavaConfigOptions(List<String> result) {
        result.add("-javaagent:"+getJmxmpAgentJarDestinationFilePath());
    }
    
    public FluentKeySigner getBrooklynRootSigner() {
        // TODO use brooklyn root CA keys etc
        return new FluentKeySigner("brooklyn-root");
    }

    /** builds remote keystores, stores config keys/certs, and copies necessary files across */
    public void install() {
        try {
            // build truststore and keystore
            FluentKeySigner signer = getBrooklynRootSigner();
            KeyPair jmxAgentKey = SecureKeys.newKeyPair();
            X509Certificate jmxAgentCert = signer.newCertificateFor("jmxmp-agent", jmxAgentKey);

            agentKeyStore = SecureKeys.newKeyStore();
            agentKeyStore.setKeyEntry("jmxmp-agent", jmxAgentKey.getPrivate(), 
                    // TODO jmx.ssl.agent.keyPassword
                    "".toCharArray(),
                    new Certificate[] { jmxAgentCert });
            ByteArrayOutputStream agentKeyStoreBytes = new ByteArrayOutputStream();
            agentKeyStore.store(agentKeyStoreBytes, 
                    // TODO jmx.ssl.agent.keyStorePassword
                    "".toCharArray());
            
            agentTrustStore = SecureKeys.newKeyStore();
            agentTrustStore.setCertificateEntry("brooklyn", getJmxAccessCert());
            ByteArrayOutputStream agentTrustStoreBytes = new ByteArrayOutputStream();
            agentTrustStore.store(agentTrustStoreBytes, "".toCharArray());
            
            // install the truststore and keystore
            driver.getMachine().copyTo(new ByteArrayInputStream(agentKeyStoreBytes.toByteArray()), getJmxSslKeyStoreFilePath());
            driver.getMachine().copyTo(new ByteArrayInputStream(agentTrustStoreBytes.toByteArray()), getJmxSslTrustStoreFilePath());
            // and install the agent
            driver.getMachine().copyTo(new ResourceUtils(this).getResourceFromUrl(getJmxmpAgentJarUrl()), getJmxmpAgentJarDestinationFilePath());
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    protected <T> T getConfig(ConfigKey<T> key) {
        return driver.getEntity().getConfig(key);
    }
    
    public synchronized Certificate getJmxAccessCert() {
        Certificate cert = getConfig(UsesJmx.JMX_SSL_ACCESS_CERT);
        if (cert!=null) return cert;
        // TODO load from keyStoreUrl
        KeyPair jmxAccessKey = SecureKeys.newKeyPair();
        X509Certificate jmxAccessCert = getBrooklynRootSigner().newCertificateFor("brooklyn-jmx-access", jmxAccessKey);

        ((AbstractEntity)driver.getEntity()).setConfigEvenIfOwned(UsesJmx.JMX_SSL_ACCESS_CERT, jmxAccessCert);
        ((AbstractEntity)driver.getEntity()).setConfigEvenIfOwned(UsesJmx.JMX_SSL_ACCESS_KEY, jmxAccessKey.getPrivate());
        
        return jmxAccessCert;
    }
    
    public synchronized PrivateKey getJmxAccessKey() {
        PrivateKey key = getConfig(UsesJmx.JMX_SSL_ACCESS_KEY);
        if (key!=null) return key;
        getJmxAccessCert();
        return getConfig(UsesJmx.JMX_SSL_ACCESS_KEY);
    }

}

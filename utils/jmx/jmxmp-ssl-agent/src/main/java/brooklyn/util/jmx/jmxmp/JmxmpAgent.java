package brooklyn.util.jmx.jmxmp;

import java.io.FileInputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;


/**
 * This exposes JMX access over JMXMP, suitable for high-security environments,
 * with support for going through firewalls as well as encrypting and authenticating securely.
 * <p>
 * Listens on 11099 unless overridden by system property brooklyn.jmxmp.port.
 * <p>
 * Use the usual com.sun.management.jmxremote.ssl to enable both SSL _and_ authentication
 * (setting brooklyn.jmxmp.ssl.authenticate false if you need to disable authentication for some reason); 
 * unless you disable client-side server authentication you will need to supply brooklyn.jmxmp.ssl.keyStore, 
 * and similarly unless server-side client auth is off you'll need the corresponding trustStore 
 * (both pointing to files on the local file system).
 * <p>
 * Service comes up on:  service:jmx:jmxmp://${HOSTNAME}:${PORT}
 * <p>
 * NB: To use JConsole with this endpoing, you need the jmxremote_optional JAR, and the
 * following command (even more complicated if using SSL):
 * java -classpath $JAVA_HOME/lib/jconsole.jar:$HOME/.m2/repository/javax/management/jmxremote_optional/1.0.1_04/jmxremote_optional-1.0.1_04.jar sun.tools.jconsole.JConsole
 */
public class JmxmpAgent {

    /** port to listen on; default to {@link #JMXMP_DEFAULT_PORT} */
    public static final String JMXMP_PORT_PROPERTY = "brooklyn.jmxmp.port";
    /** hostname to advertise, and if {@value #JMX_SERVER_ADDRESS_WILDCARD_PROPERTY} is false also the hostname/interface to bind to */
    public static final String RMI_HOSTNAME_PROPERTY = "java.rmi.server.hostname";
    /** whether JMX should bind to all interfaces */
    public static final String JMX_SERVER_ADDRESS_WILDCARD_PROPERTY = "jmx.remote.server.address.wildcard";

    /** whether to use SSL (TLS) encryption; requires a keystore to be set */
    public static final String USE_SSL_PROPERTY = "com.sun.management.jmxremote.ssl";
    /** whether to use SSL (TLS) certificates to authenticate the client; 
     * requires a truststore to be set, and requires {@link #USE_SSL_PROPERTY} true 
     * (different to 'com.sun.management.jmxremote.authenticate' because something else
     * insists on intercepting that and uses it for passwords); 
     * defaults to true iff {@link #USE_SSL_PROPERTY} is set because 
     * who wouldn't want client authentication if you're encrypting the link */
    public static final String AUTHENTICATE_CLIENTS_PROPERTY = "brooklyn.jmxmp.ssl.authenticate";
    
    public static final String JMXMP_KEYSTORE_FILE_PROPERTY = "brooklyn.jmxmp.ssl.keyStore";
    public static final String JMXMP_KEYSTORE_PASSWORD_PROPERTY = "brooklyn.jmxmp.ssl.keyStorePassword";
    public static final String JMXMP_KEYSTORE_KEY_PASSWORD_PROPERTY = "brooklyn.jmxmp.ssl.keyStore.keyPassword";
    public static final String JMXMP_KEYSTORE_TYPE_PROPERTY = "brooklyn.jmxmp.ssl.keyStoreType";
    
    public static final String JMXMP_TRUSTSTORE_FILE_PROPERTY = "brooklyn.jmxmp.ssl.trustStore";
    public static final String JMXMP_TRUSTSTORE_PASSWORD_PROPERTY = "brooklyn.jmxmp.ssl.trustStorePassword";
    public static final String JMXMP_TRUSTSTORE_TYPE_PROPERTY = "brooklyn.jmxmp.ssl.trustStoreType";

    // properties above affect behaviour; those below are simply used in code
    
    
    public static final String TLS_NEED_AUTHENTICATE_CLIENTS_PROPERTY = "jmx.remote.tls.need.client.authentication";
    public static final String TLS_WANT_AUTHENTICATE_CLIENTS_PROPERTY = "jmx.remote.tls.want.client.authentication";
    public static final String TLS_SOCKET_FACTORY_PROPERTY = "jmx.remote.tls.socket.factory";
    
    public static final String TLS_JMX_REMOTE_PROFILES = "TLS";
    public static final int JMXMP_DEFAULT_PORT = 11099;
    
    public static void premain(String agentArgs) {
        new JmxmpAgent().startJmxmpConnector(System.getProperties());
    }
    
    public JMXConnectorServer startJmxmpConnector(Properties properties) {
        try {
            final int port = Integer.parseInt(properties.getProperty(JMXMP_PORT_PROPERTY, ""+JMXMP_DEFAULT_PORT));
            
            String hostname = getLocalhostHostname(properties);
            JMXServiceURL serviceUrl = new JMXServiceURL("service:jmx:jmxmp://"+hostname+":"+port);
            
            Map<String,Object> env = new LinkedHashMap<String, Object>();
            propagate(properties, env, JMX_SERVER_ADDRESS_WILDCARD_PROPERTY, null);

            if (asBoolean(properties, USE_SSL_PROPERTY, false, true)) {
                setSslEnvFromProperties(env, properties);
            } else {
                if (asBoolean(properties, AUTHENTICATE_CLIENTS_PROPERTY, false, true)) {
                    throw new IllegalStateException("Client authentication not supported when not using SSL");
                }
            }
            MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
            
            JMXConnectorServer connector = JMXConnectorServerFactory.newJMXConnectorServer(serviceUrl, env, platformMBeanServer);
            connector.start();

            System.out.println("JmxmpAgent active at: "+serviceUrl);
            
            return connector;
        } catch (RuntimeException e) {
            System.err.println("Unable to start JmxmpAgent: "+e);
            throw e;
        } catch (Exception e) {
            System.err.println("Unable to start JmxmpAgent: "+e);
            throw new RuntimeException(e);
        }
    }


    public static String getLocalhostHostname(Properties properties) throws UnknownHostException {
        String hostname = properties==null ? null : properties.getProperty(RMI_HOSTNAME_PROPERTY);
        if (hostname==null || hostname.isEmpty()) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                System.err.println("Misconfigured hostname when setting JmxmpAgent; reverting to 127.0.0.1: "+e);
                hostname = "127.0.0.1";
            }
        }
        return hostname;
    }

    /** copies the value of key from the source to the target, if set;
     * otherwise sets the defaultValueIfNotNull (final arg) if that is not null;
     * returns whether anything is set
     */
    private static boolean propagate(Properties source, Map<String, Object> target, String key, Object defaultValueIfNotNull) {
        Object v = source.getProperty(key);
        if (v==null) v = defaultValueIfNotNull;
        if (v==null) return false;
        target.put(key, v);
        return true;
    }
    
    /** returns boolean interpretation of a string, 
     * defaulting to valueIfUnknownText (last arg) if the value is unset or unrecognised, 
     * throwing exception if that is null and value is unset or unrecognised */
    private boolean asBoolean(Properties properties, String key, Boolean valueIfNull, Boolean valueIfUnknownText) {
        Object v = properties.get(key);
        if (v==null) {
            if (valueIfNull==null) throw new IllegalStateException("Property '"+key+"' is required.");
            return valueIfNull;
        }
        String vv = v.toString();
        if ("true".equalsIgnoreCase(vv)) return true;
        if ("false".equalsIgnoreCase(vv)) return false;
        if (valueIfUnknownText==null)
            throw new IllegalStateException("Property '"+key+"' has illegal value '"+vv+"'; should be true or false");
        return valueIfUnknownText;
    }
    
    public void setSslEnvFromProperties(Map<String, Object> env, Properties properties) throws Exception {
        env.put("jmx.remote.profiles", TLS_JMX_REMOTE_PROFILES); 
        
        boolean authenticating = asBoolean(properties, AUTHENTICATE_CLIENTS_PROPERTY, true, null); 
        if (authenticating) {
            env.put(AUTHENTICATE_CLIENTS_PROPERTY, "true");
            // NB: the above seem to be ignored (horrid API!); we need the ones below set
            propagate(properties, env, TLS_NEED_AUTHENTICATE_CLIENTS_PROPERTY, "true");
            // also note, the above seems to be overridden by below internally !
            // (setting WANT=false and NEED=true allows access if no trust managers are specified)
            propagate(properties, env, TLS_WANT_AUTHENTICATE_CLIENTS_PROPERTY, "true");
        }
        
        
        if (!propagate(properties, env, TLS_SOCKET_FACTORY_PROPERTY, null)) {
            String keyStoreFile = properties.getProperty(JMXMP_KEYSTORE_FILE_PROPERTY);
            String keyStorePass = properties.getProperty(JMXMP_KEYSTORE_PASSWORD_PROPERTY, "");
            String keyStoreType = properties.getProperty(JMXMP_KEYSTORE_TYPE_PROPERTY, KeyStore.getDefaultType());
            String keyStoreKeyPass = properties.getProperty(JMXMP_KEYSTORE_KEY_PASSWORD_PROPERTY, "");
            
            KeyStore ks = KeyStore.getInstance(keyStoreType);
            if (keyStoreFile!=null)
                ks.load(new FileInputStream(keyStoreFile), keyStorePass.toCharArray());
            else
                ks.load(null, null);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); 
            kmf.init(ks, keyStoreKeyPass.toCharArray());
            
            String trustStoreFile = properties.getProperty(JMXMP_TRUSTSTORE_FILE_PROPERTY);
            String trustStorePass = properties.getProperty(JMXMP_TRUSTSTORE_PASSWORD_PROPERTY, "");
            String trustStoreType = properties.getProperty(JMXMP_TRUSTSTORE_TYPE_PROPERTY, KeyStore.getDefaultType());
            
            TrustManager[] tms;
            if (trustStoreFile!=null) {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                KeyStore ts = KeyStore.getInstance(trustStoreType);
                ts.load(new FileInputStream(trustStoreFile), trustStorePass.toCharArray());
                tmf.init(ts);
//                tms = tmf.getTrustManagers();
                // line above causes tests to fail!  bug in JMXMP TLS impl?
                tms = new TrustManager[] { newInspectAllTrustManager((X509TrustManager) tmf.getTrustManagers()[0]) };
            } else {
                tms = null;
                if (authenticating) 
                    System.err.println("Authentication required but no truststore supplied to JmxmpAgent. Client connections will likely fail.");
            }
            
            SSLContext ctx = SSLContext.getInstance("TLSv1");
            ctx.init(kmf.getKeyManagers(), tms, null);
            SSLSocketFactory ssf = ctx.getSocketFactory(); 
            env.put(TLS_SOCKET_FACTORY_PROPERTY, ssf); 
        }
    }

    public static final TrustManager newInspectAllTrustManager(final X509TrustManager delegate) {
        return new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                // overriding this method fixes bug where non-accepted issuers have an "accept all" policy, in JMXMP/TLS
                return new X509Certificate[0];
            }
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws java.security.cert.CertificateException {
                delegate.checkClientTrusted(chain, authType);
            }
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws java.security.cert.CertificateException {
                delegate.checkServerTrusted(chain, authType);
            }
        };
    };
    
    public static void main(String[] args) {
        premain("");
    }
}

package brooklyn.entity.java;

import java.security.PrivateKey;
import java.security.cert.Certificate;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

public interface UsesJmx extends UsesJava {

    public static final int DEFAULT_JMX_PORT = 1099;   // RMI port?

    @SetFromFlag("useJmx")
    public static final ConfigKey<Boolean> USE_JMX = ConfigKeys.newConfigKey("jmx.enabled", "JMX enabled", Boolean.TRUE);

    @SetFromFlag("jmxPort")
    public static final PortAttributeSensorAndConfigKey JMX_PORT = Attributes.JMX_PORT;

    @SetFromFlag("rmiServerPort")
    public static final PortAttributeSensorAndConfigKey RMI_SERVER_PORT = Attributes.RMI_SERVER_PORT;

    @Deprecated // since 0.4 use RMI_REGISTRY_PORT
    public static final PortAttributeSensorAndConfigKey RMI_PORT = RMI_SERVER_PORT;
    public static final PortAttributeSensorAndConfigKey RMI_REGISTRY_PORT = RMI_SERVER_PORT;

    @SetFromFlag("jmxContext")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_CONTEXT = Attributes.JMX_CONTEXT;

    public static final AttributeSensor<String> JMX_URL = Sensors.newStringSensor("jmx.url", "JMX URL");

    /** forces JMX to be secured, using JMXMP so it gets through firewalls _and_ SSL/TLS
     * (NB: there is not currently any corresponding JMXMP without SSL/TLS) */
    @SetFromFlag("jmxSecure")
    public static final ConfigKey<Boolean> JMX_SSL_ENABLED = ConfigKeys.newConfigKey("jmx.ssl.enabled", "JMX over JMXMP enabled with SSL/TLS", Boolean.FALSE);

    /*
     * Synopsis of how the keys work for JMX_SSL:
     * 
     * BROOKLYN
     *  * brooklyn ROOT key + cert -> 
     *      used to identify things brooklyn has signed, ie to confirm their identity
     *      signs all certs created by brooklyn
     *      (created per entity if not specified as input)
     *  * brooklyn JMX ACCESS key + cert ->
     *      used to authenticate brooklyn to remote JMX agent
     *      typically, but not necessarily, signed by ROOT cert
     *      (typically created per entity, unless specified;
     *      global would probably be fine but more work;
     *      however it is important that this _not_ sign agents keys,
     *      to prevent agents from accessing other agents)
     * 
     * AGENT (e.g. JMX server in each managed java process)
     *  * gets AGENT key + cert ->
     *      signed by brooklyn ROOT, used to authenticate itself to brooklyn
     *      (brooklyn trusts this; does not need to remember this)
     *  * trusts only the relevant brooklyn JMX ACCESS key (its truststore contains that cert)
     */

    /* TODO brooklyn ROOT key
     *  
    public static final ConfigKey<String> BROOKLYN_SSL_ROOT_KEYSTORE_URL = new BasicConfigKey<String>(
            String.class, "brooklyn.ssl.root.keyStoreUrl", "URL to keystore Brooklyn should use as root private key and certificate-signing authority", null);
    
    public static final ConfigKey<String> BROOKLYN_SSL_ROOT_KEY_DATA = new BasicConfigKey<String>(
            String.class, "brooklyn.ssl.root.key", "root private key (RSA string format), used to sign managed servers", null);
    public static final ConfigKey<String> BROOKLYN_SSL_ROOT_CERT_DATA = new BasicConfigKey<String>(
            String.class, "brooklyn.ssl.root.cert", "certificate for root private key (RSA string format)", null);
    
     * brooklyn.ssl.root.keyStorePassword
     * brooklyn.ssl.root.keyAlias (if null, looks for one called 'brooklyn', otherwise takes the first key)
     * brooklyn.ssl.root.keyPassword
     */ 
    
    public static final ConfigKey<PrivateKey> JMX_SSL_ACCESS_KEY = new BasicConfigKey<PrivateKey>(
            PrivateKey.class, "jmx.ssl.access.key", "key used to access a JMX agent (typically per entity, embedded in the managed JVM)", null);
    public static final ConfigKey<Certificate> JMX_SSL_ACCESS_CERT = new BasicConfigKey<Certificate>(
            Certificate.class, "jmx.ssl.access.cert", "certificate of key used to access a JMX agent", null);
    
    /* TODO specify a keystore from which to get the access key
     * (above keys are set programmatically, typically _not_ by the user ... keystore would be the way to do that)
     * 
     * jmx.ssl.access.keyStoreUrl (optional)
     * jmx.ssl.access.keyStorePassword (optional)
     * jmx.ssl.access.keyAlias (optional)
     */
    
    /* could allow user to specify additional certs for JMX agents which should be trusted
     *    
     * jmx.ssl.access.trustStoreUrl
     */
    
    /* optionally: could allow JMX agent to trust additional accessers, 
     * and/or use known keys in the case that other accessers might want to authenticate the JMX server
     *   
     * NB currently agent keys are not stored in brooklyn... no reason to as 
     * (a) currently we trust jmx agents; and (b) for agent-auth we should simply sign keys;
     * either way, seems fine for brooklyn to throw them away once they are installed on the remote machine)
     * 
     * jmx.ssl.agent.keyStoreUrl
     * jmx.ssl.agent.keyStorePassword
     * jmx.ssl.agent.keyAlias
     * jmx.ssl.agent.keyPassword
     * 
     * jmx.ssl.agent.trustStoreUrl
     */

    /* optionally: this could be set to disallow attaching to JMX through the attach mechanism
     * (but this option is generally not considered needed, as JVM attachment is
     * already restricted to localhost and to the the user running the process)
     * 
     * -XX:+DisableAttachMechanism
     */
}
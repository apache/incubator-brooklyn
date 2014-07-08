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
package brooklyn.entity.java;

import java.security.PrivateKey;
import java.security.cert.Certificate;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.PortRange;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

public interface UsesJmx extends UsesJava {

    public static final int DEFAULT_JMX_PORT = 1099;   // RMI port?

    @SetFromFlag("useJmx")
    public static final ConfigKey<Boolean> USE_JMX = ConfigKeys.newConfigKey("jmx.enabled", "JMX enabled", Boolean.TRUE);

    /** chosen by java itself by default; setting this will only have any effect if using an agent */ 
    @SetFromFlag("jmxPort")
    public static final PortAttributeSensorAndConfigKey JMX_PORT = new PortAttributeSensorAndConfigKey(
            "jmx.direct.port", "JMX direct/private port (e.g. JMX RMI server port, or JMXMP port, but not RMI registry port)", PortRanges.fromString("31001+")) {
        @Override protected Integer convertConfigToSensor(PortRange value, Entity entity) {
            // TODO when using JmxAgentModes.NONE we should *not* convert, but leave it null
            // (e.g. to prevent a warning in e.g. ActiveMQIntegrationTest)
            // [there was - previously - a note about needing to move these keys to UsesJmx,
            // that has been done, so not sure if there is anything more needed or if we can just
            // check here entity.getConfig(JMX_AGENT_MODE) ... needs testing of course]
            return super.convertConfigToSensor(value, entity);
        }
    };
    
    /** well-known port used by Java itself to start the RMI registry where JMX private port can be discovered;
     * ignored if using JMXMP agent
     */ 
    @SetFromFlag("rmiRegistryPort")
    public static final PortAttributeSensorAndConfigKey RMI_REGISTRY_PORT = new PortAttributeSensorAndConfigKey(
            "rmi.registry.port", "RMI registry port, used for discovering JMX (private) port", PortRanges.fromString("1099, 19099+"));

    @SetFromFlag("jmxContext")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_CONTEXT = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "jmx.context", "JMX context path", "jmxrmi");

    public static final AttributeSensor<String> JMX_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "jmx.service.url", "The URL for connecting to the MBean Server");

    /** forces JMX to be secured, using JMXMP so it gets through firewalls _and_ SSL/TLS
     * (NB: there is not currently any corresponding JMXMP without SSL/TLS) */
    @SetFromFlag("jmxSecure")
    public static final ConfigKey<Boolean> JMX_SSL_ENABLED = ConfigKeys.newBooleanConfigKey("jmx.ssl.enabled", "JMX over JMXMP enabled with SSL/TLS", Boolean.FALSE);

    public enum JmxAgentModes {
        /** auto-detect the agent to use based on location, preferring JMXMP except at localhost where JMX_RMI_CUSTOM_AGENT is preferred */
        AUTODETECT,
        /** JMXMP which permits firewall access through a single port {@link UsesJmx#JMX_PORT} */
        JMXMP,
        /** Start {@link #JMXMP} along with an RMI Registry on {@link UsesJmx#RMI_REGISTRY_PORT}
         * (redirecting to an anonymous high-numbered port as the RMI server) */
        JMXMP_AND_RMI,
        /** JMX over RMI custom agent which permits access through a known RMI registry port, redirected to a known JMX-RMI port;
         * two ports must be opened on the firewall, and the same hostname resolvable on the target machine and by the client */
        JMX_RMI_CUSTOM_AGENT,
        /** do not install a JMX agent; use the default RMI which opens a registry at a known port, redirected to an _unknown_ port for jmx 
         * (experimental) */
        NONE
    }
    
    @SetFromFlag("jmxAgentMode")
    public static final ConfigKey<JmxAgentModes> JMX_AGENT_MODE = ConfigKeys.newConfigKey(JmxAgentModes.class,
            "jmx.agent.mode", "What type of JMX agent to use; defaults to null (autodetect) which means " +
    		"JMXMP_AND_RMI allowing firewall access through a single port as well as local access supporting jconsole " +
    		"(unless JMX_SSL_ENABLED is set, in which case it is JMXMP only)", 
    		JmxAgentModes.AUTODETECT);

    /** Currently only used to connect; not used to set up JMX (so only applies where systems set this up themselves)
     */
    public static final BasicAttributeSensorAndConfigKey<String> JMX_USER = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "jmx.user", "JMX username");
    
    /** Currently only used to connect; not used to set up JMX (so only applies where systems set this up themselves)
     */
    public static final BasicAttributeSensorAndConfigKey<String> JMX_PASSWORD = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "jmx.password", "JMX password");
    
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
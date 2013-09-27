package brooklyn.entity.java;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.effector.EffectorTasks;
import brooklyn.event.feed.jmx.JmxHelper;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation.LocalhostMachine;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.BrooklynMavenArtifacts;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.jmx.jmxmp.JmxmpAgent;
import brooklyn.util.jmx.jmxrmi.JmxRmiAgent;
import brooklyn.util.maven.MavenArtifact;
import brooklyn.util.maven.MavenRetriever;
import brooklyn.util.net.Urls;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

public class JmxSupport implements UsesJmx {

    private static final Logger log = LoggerFactory.getLogger(JmxSupport.class);
    
    private final Entity entity;
    private final String runDir;
    
    private Boolean isJmx;
    private Boolean isSecure;
    private JmxAgentModes jmxAgentMode;

    private static boolean warnedAboutNotOnClasspath = false;
    
    /** run dir may be null if it is not accessed */
    public JmxSupport(Entity entity, @Nullable String runDir) {
        this.entity = Preconditions.checkNotNull(entity, "entity must be supplied");
        this.runDir = runDir;
    }
    
    @Nonnull
    public String getRunDir() {
        return Preconditions.checkNotNull(runDir, "runDir must have been supplied to perform this operation");
    }
    
    public Entity getEntity() {
        return entity;
    }
    
    <T> T getConfig(ConfigKey<T> key) {
        return getEntity().getConfig(key);
    }
    
    <T> T getConfig(HasConfigKey<T> key) {
        return getEntity().getConfig(key);
    }
    
    <T> void setConfig(ConfigKey<T> key, T value) {
        ((EntityLocal)getEntity()).setConfig(key, value);
    }
    
    public Optional<SshMachineLocation> getMachine() {
        Collection<Location> l = entity.getLocations();
        if (l.size()==1) {
            Location ll = l.iterator().next();
            if (ll instanceof SshMachineLocation) return Optional.of((SshMachineLocation) ll);
        }
        return Optional.absent();
    }

    public boolean isJmx() {
        init();
        return isJmx;
    }

    public JmxAgentModes getJmxAgentMode() {
        init();
        if (jmxAgentMode==null) return JmxAgentModes.NONE;
        return jmxAgentMode;
    }
    
    public boolean isSecure() {
        init();
        if (isSecure==null) return false;
        return isSecure;
    }
    
    protected synchronized void init() {
        if (isJmx!=null)
            return;
        
        if (entity.getConfig(USE_JMX)==Boolean.FALSE) {
            isJmx = false;
            return;
        }
        isJmx = true;
        jmxAgentMode = entity.getConfig(JMX_AGENT_MODE);
        if (jmxAgentMode==null) jmxAgentMode = JmxAgentModes.AUTODETECT;
        
        isSecure = entity.getConfig(JMX_SSL_ENABLED);
        if (isSecure==null) isSecure = false;
        
        if (jmxAgentMode==JmxAgentModes.AUTODETECT) {
            if (isSecure()) {
                jmxAgentMode = JmxAgentModes.JMXMP;
            } else {
                // attempt to autodetect
                Optional<SshMachineLocation> m = getMachine();
                if (m.isPresent()) {
                    SshMachineLocation ll = m.get();
                    if (ll instanceof LocalhostMachine) {
                        if (log.isTraceEnabled())
                            log.trace("Auto-detecting JMX configuration for "+entity+", set as NOT jmxmp because it is localhost");
                        jmxAgentMode = JmxAgentModes.JMX_RMI_CUSTOM_AGENT;
                    } else {
                        if (log.isTraceEnabled())
                            log.trace("Auto-detecting JMX configuration for "+entity+", set as JMXMP because it is "+ll);
                        jmxAgentMode = JmxAgentModes.JMXMP;
                    }
                } else {
                    log.warn("Auto-detecting JMX configuration for "+entity+": cannot identify location so setting JMXMP");
                    jmxAgentMode = JmxAgentModes.JMXMP;                    
                }
                if (!new ResourceUtils(this).doesUrlExist(getJmxAgentJarUrl())) {
                    // can happen e.g. if eclipse build
                    log.warn("JMX agent JAR not found ("+getJmxAgentJarUrl()+") when auto-detecting JMX settings for "+entity+"; " +
                            "likely cause is an incomplete build (e.g. from Eclipse; run a maven build then retry in the IDE); "+
                    		"reverting to NONE (use built-in Java JMX support, which will not go through firewalls)");
                    jmxAgentMode = JmxAgentModes.NONE;
                }
            }
            
            ((EntityLocal)entity).setConfig(JMX_AGENT_MODE, jmxAgentMode);
        }
        
        if (isSecure && jmxAgentMode!=JmxAgentModes.JMXMP) {
            String msg = "JMX SSL is specified, but it requires JMXMP which is disabled, when configuring "+entity;
            log.warn(msg);
            throw new IllegalStateException(msg);
        }
    }

    public void setJmxUrl() {
        ((EntityInternal)entity).setAttribute(JMX_URL, getJmxUrl());
    }

    public String getJmxUrl() {
        init();
        
        String host = entity.getAttribute(Attributes.HOSTNAME);
        if (host==null) {
            SshMachineLocation machine = EffectorTasks.getSshMachine(entity);
            host = machine.getAddress().getHostName();
        }
        
        if (getJmxAgentMode()==JmxAgentModes.JMXMP) {
            return JmxHelper.toJmxmpUrl(host, entity.getAttribute(JMX_PORT));
        } else {
            if (getJmxAgentMode()==JmxAgentModes.NONE) {
                fixPortsForModeNone();
            }
            // this will work for agent or agentless
            return JmxHelper.toRmiJmxUrl(host, 
                    entity.getAttribute(JMX_PORT),
                    entity.getAttribute(RMI_REGISTRY_PORT),
                    entity.getAttribute(JMX_CONTEXT));
        }
    }

    /** mode NONE cannot set a JMX (RMI server) port; it needs an RMI registry port,
     * then gets redirected to an anonymous RMI server port;
     * both the hostname and the anonymous port must be accessible to use this mode
     * (hence the use of the other agents in most cases) */
    protected int fixPortsForModeNone() {
        assert getJmxAgentMode()==JmxAgentModes.NONE;
        Integer jmxRemotePort = getEntity().getAttribute(JMX_PORT);
        Integer rmiRegistryPort = getEntity().getAttribute(RMI_REGISTRY_PORT);
        if (rmiRegistryPort!=null && rmiRegistryPort>0) {
            if (jmxRemotePort==null || jmxRemotePort!=rmiRegistryPort) {
                if (jmxRemotePort!=null && jmxRemotePort>0) {
                    // ignore RMI registry port when mode 'none' is set -- set same as JMX port here
                    // (bit irritating, but JMX_PORT will be ignored in this mode)
                    log.warn("Ignoring JMX_PORT "+jmxRemotePort+" when configuring agentless JMX on "+getEntity()+"; will use RMI_REGISTRY_PORT "+rmiRegistryPort);
                }
                jmxRemotePort = rmiRegistryPort;
                ((EntityLocal)getEntity()).setAttribute(JMX_PORT, jmxRemotePort);
            }
        } else {
            if (jmxRemotePort==null || jmxRemotePort<=0) {
                throw new IllegalStateException("Invalid JMX_PORT "+jmxRemotePort+" and RMI_REGISTRY_PORT "+rmiRegistryPort+" when configuring JMX "+getJmxAgentMode()+" on "+getEntity());
            }
            ((EntityLocal)getEntity()).setAttribute(RMI_REGISTRY_PORT, jmxRemotePort);
        }
        return jmxRemotePort;
    }

    public List<String> getJmxJavaConfigOptions() {
        if (getJmxAgentMode()==JmxAgentModes.NONE)
            return MutableList.of();
        return MutableList.of(String.format("-javaagent:%s", getJmxAgentJarDestinationFilePath()));
    }

    public String getJmxAgentJarDestinationFilePath() {
        return Urls.mergePaths(getRunDir(), getJmxAgentJarBasename());
    }

    @Nullable public MavenArtifact getJmxAgentJarMavenArtifact() {
        switch (getJmxAgentMode()) {
        case JMXMP: 
            MavenArtifact result = BrooklynMavenArtifacts.artifact(null, "brooklyn-jmxmp-agent", "jar", "with-dependencies");
            // the "with-dependencies" variant is needed; however the filename then has the classifier segment _replaced_ by "shaded" when this filename is created
            result.setCustomFileNameAfterArtifactMarker("shaded");
            result.setClassifierFileNameMarker("");
            return result;
        case JMX_RMI_CUSTOM_AGENT: 
            return BrooklynMavenArtifacts.jar("brooklyn-jmxrmi-agent");
        default:
            return null;
        }        
    }

    /** @deprecated since 0.6.0; use {@link #getJmxAgentJarMavenArtifact()} */
    @Deprecated
    public String getJmxAgentJarBasename() {
        MavenArtifact artifact = getJmxAgentJarMavenArtifact();
        if (artifact==null)
            throw new IllegalStateException("Either JMX is not enabled or there is an error in the configuration (JMX mode "+getJmxAgentMode()+" does not support agent JAR)");
        return artifact.getFilename();
    }

    /** returns URL for accessing the java agent, throwing if not applicable;
     * prefers on classpath where it should be, but will fall back to taking from maven hosted 
     * (known problem in Eclipse where JARs are not always copied) 
     */
    public String getJmxAgentJarUrl() {
        MavenArtifact artifact = getJmxAgentJarMavenArtifact();
        if (artifact==null)
            throw new IllegalStateException("Either JMX is not enabled or there is an error in the configuration (JMX mode "+getJmxAgentMode()+" does not support agent JAR)");
        String jar = "classpath://" + artifact.getFilename();
        if (new ResourceUtils(this).doesUrlExist(jar))
            return jar;
        
        String result = MavenRetriever.localUrl(artifact);
        if (warnedAboutNotOnClasspath) {
            log.debug("JMX JAR for "+artifact+" is not on the classpath; taking from "+result);
        } else {
            log.warn("JMX JAR for "+artifact+" is not on the classpath; taking from "+result+" (subsequent similar messages will be logged at debug)");
            warnedAboutNotOnClasspath = true;
        }
        return result;
    }
    
    /** applies _some_ of the common settings needed to connect via JMX */
    public void applyJmxJavaSystemProperties(MutableMap.Builder<String,Object> result) {
        if (!isJmx()) return ;
        
        Integer jmxRemotePort;
        String hostName = getEntity().getAttribute(Attributes.HOSTNAME);
        if (hostName==null) hostName = checkNotNull(getMachine().get().getAddress().getHostName(), "hostname for entity " + entity);
        
        result.put("com.sun.management.jmxremote", null);

        switch (getJmxAgentMode()) {
        case JMXMP:
            jmxRemotePort = getEntity().getAttribute(JMX_PORT);
            if (jmxRemotePort==null || jmxRemotePort<=0)
                throw new IllegalStateException("Unsupported JMX port "+jmxRemotePort+" - when applying system properties ("+getJmxAgentMode()+" / "+getEntity()+")");
            result.put(JmxmpAgent.JMXMP_PORT_PROPERTY, jmxRemotePort);
            // with JMXMP don't try to tell it the hostname -- it isn't needed for JMXMP, and if specified 
            // it will break if the hostname we see is not known at the server, e.g. a forwarding public IP
            // (should not be present, but remove just to be sure)
            result.remove("java.rmi.server.hostname");
            break;
        case JMX_RMI_CUSTOM_AGENT:    
            jmxRemotePort = getEntity().getAttribute(JMX_PORT);
            if (jmxRemotePort==null || jmxRemotePort<=0)
                throw new IllegalStateException("Unsupported JMX port "+jmxRemotePort+" - when applying system properties ("+getJmxAgentMode()+" / "+getEntity()+")");
            result.put(JmxRmiAgent.RMI_REGISTRY_PORT_PROPERTY, 
                    Preconditions.checkNotNull(entity.getAttribute(UsesJmx.RMI_REGISTRY_PORT), "registry port"));
            result.put(JmxRmiAgent.JMX_SERVER_PORT_PROPERTY, jmxRemotePort);
            result.put("java.rmi.server.hostname", hostName);
            break;
        case NONE:
            // only for mode 'NONE' - other modes use different fields
            jmxRemotePort = fixPortsForModeNone();
            result.put("com.sun.management.jmxremote.port", jmxRemotePort);
            result.put("java.rmi.server.hostname", hostName);
            break;
        default:    
            throw new IllegalStateException("Unsupported JMX mode - when applying system properties ("+getJmxAgentMode()+" / "+getEntity()+")");
        }
        
        if (isSecure()) {
            // set values true, and apply keys pointing to keystore / truststore
            getJmxSslSupport().applyAgentJmxJavaSystemProperties(result);
        } else {
            result.
                put("com.sun.management.jmxremote.ssl", false).
                put("com.sun.management.jmxremote.authenticate", false);
        }
    }

    /** installs files needed for JMX, to the runDir given in constructor, assuming the runDir has been created */ 
    public void install() {
        if (getJmxAgentMode()!=JmxAgentModes.NONE) {
            getMachine().get().copyTo(new ResourceUtils(this).getResourceFromUrl(
                getJmxAgentJarUrl()), getJmxAgentJarDestinationFilePath());
        }
        if (isSecure()) {
            getJmxSslSupport().install();
        }
    }

    protected JmxmpSslSupport getJmxSslSupport() {
        return new JmxmpSslSupport(this);
    }

    /** sets JMR_RMI_CUSTOM_AGENT as the connection mode for the indicated apps.
     * <p>
     * TODO callers of this method have RMI dependencies in the actual app;
     * we should look at removing them, so that those pieces of software can run behind 
     * forwarding public IP's and over SSL (both reasons JMXMP is preferred by us!)
     */
    public void recommendJmxRmiCustomAgent() {
        // set JMX_RMI because the registry is needed (i think)
        Object jmx = ((EntityInternal)entity).getConfigMap().getRawConfig(UsesJmx.JMX_AGENT_MODE);
        if (jmx==null) {
            setConfig(UsesJmx.JMX_AGENT_MODE, JmxAgentModes.JMX_RMI_CUSTOM_AGENT);
        } else if (jmx!=JmxAgentModes.JMX_RMI_CUSTOM_AGENT) {
            log.warn("Entity "+entity+" may not function unless running JMX_RMI_CUSTOM_AGENT mode (asked to use "+jmx+")");
        }
    }
    
}

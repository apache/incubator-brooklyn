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
package org.apache.brooklyn.entity.java;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.core.location.access.BrooklynAccessUtils;
import org.apache.brooklyn.feed.jmx.JmxHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.BrooklynMavenArtifacts;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.jmx.jmxmp.JmxmpAgent;
import org.apache.brooklyn.util.jmx.jmxrmi.JmxRmiAgent;
import org.apache.brooklyn.util.maven.MavenArtifact;
import org.apache.brooklyn.util.maven.MavenRetriever;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Preconditions;
import com.google.common.net.HostAndPort;

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
        ((EntityLocal)getEntity()).config().set(key, value);
    }

    public Maybe<SshMachineLocation> getMachine() {
        return Locations.findUniqueSshMachineLocation(entity.getLocations());
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

        if (Boolean.FALSE.equals(entity.getConfig(USE_JMX))) {
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
                jmxAgentMode = JmxAgentModes.JMXMP_AND_RMI;
                if (!ResourceUtils.create(this).doesUrlExist(getJmxAgentJarUrl())) {
                    // can happen e.g. if eclipse build
                    log.warn("JMX agent JAR not found ("+getJmxAgentJarUrl()+") when auto-detecting JMX settings for "+entity+"; " +
                            "likely cause is an incomplete build (e.g. from Eclipse; run a maven build then retry in the IDE); "+
                            "reverting to NONE (use built-in Java JMX support, which will not go through firewalls)");
                    jmxAgentMode = JmxAgentModes.NONE;
                }
            }

            ((EntityLocal)entity).config().set(JMX_AGENT_MODE, jmxAgentMode);
        }

        if (isSecure && jmxAgentMode!=JmxAgentModes.JMXMP) {
            String msg = "JMX SSL is specified, but it requires JMXMP which is disabled, when configuring "+entity;
            log.warn(msg);
            throw new IllegalStateException(msg);
        }
    }

    public void setJmxUrl() {
        ((EntityInternal)entity).sensors().set(JMX_URL, getJmxUrl());
    }

    public String getJmxUrl() {
        init();

        HostAndPort jmx = BrooklynAccessUtils.getBrooklynAccessibleAddress(entity, entity.getAttribute(JMX_PORT));

        if (EnumSet.of(JmxAgentModes.JMXMP, JmxAgentModes.JMXMP_AND_RMI).contains(getJmxAgentMode())) {
            return JmxHelper.toJmxmpUrl(jmx.getHostText(), jmx.getPort());
        } else {
            if (getJmxAgentMode() == JmxAgentModes.NONE) {
                fixPortsForModeNone();
            }
            // this will work for agent or agentless
            HostAndPort rmi = BrooklynAccessUtils.getBrooklynAccessibleAddress(entity, entity.getAttribute(RMI_REGISTRY_PORT));
            return JmxHelper.toRmiJmxUrl(jmx.getHostText(), jmx.getPort(), rmi.getPort(),
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
                ((EntityLocal)getEntity()).sensors().set(JMX_PORT, jmxRemotePort);
            }
        } else {
            if (jmxRemotePort==null || jmxRemotePort<=0) {
                throw new IllegalStateException("Invalid JMX_PORT "+jmxRemotePort+" and RMI_REGISTRY_PORT "+rmiRegistryPort+" when configuring JMX "+getJmxAgentMode()+" on "+getEntity());
            }
            ((EntityLocal)getEntity()).sensors().set(RMI_REGISTRY_PORT, jmxRemotePort);
        }
        return jmxRemotePort;
    }

    public List<String> getJmxJavaConfigOptions() {
        if (EnumSet.<JmxAgentModes>of(JmxAgentModes.NONE, JmxAgentModes.JMX_RMI).contains(getJmxAgentMode())) {
            return MutableList.of();
        } else {
            return MutableList.of(String.format("-javaagent:%s", getJmxAgentJarDestinationFilePath()));
        }
    }

    public String getJmxAgentJarDestinationFilePath() {
        // cache the local path so we continue to work post-rebind to a different version
        String result = getEntity().getAttribute(JMX_AGENT_LOCAL_PATH);
        if (Strings.isNonBlank(result)) return result;
        result = getJmxAgentJarDestinationFilePathDefault();
        ((EntityInternal)getEntity()).sensors().set(JMX_AGENT_LOCAL_PATH, result);
        return result;
    }
    
    public String getJmxAgentJarDestinationFilePathDefault() {
        return Urls.mergePaths(getRunDir(), getJmxAgentJarBasename());
    }

    @Nullable public MavenArtifact getJmxAgentJarMavenArtifact() {
        switch (getJmxAgentMode()) {
        case JMXMP:
        case JMXMP_AND_RMI:
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
        if (ResourceUtils.create(this).doesUrlExist(jar))
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

        Integer jmxPort = Preconditions.checkNotNull(entity.getAttribute(JMX_PORT), "jmx port must not be null for %s", entity);
        HostAndPort jmx = BrooklynAccessUtils.getBrooklynAccessibleAddress(entity, jmxPort);
        Integer jmxRemotePort = getEntity().getAttribute(JMX_PORT);
        String hostName = jmx.getHostText();

        result.put("com.sun.management.jmxremote", null);
        result.put("java.rmi.server.hostname", hostName);

        switch (getJmxAgentMode()) {
        case JMXMP_AND_RMI:
            Integer rmiRegistryPort = Preconditions.checkNotNull(entity.getAttribute(UsesJmx.RMI_REGISTRY_PORT), "registry port (config val %s)", entity.getConfig(UsesJmx.RMI_REGISTRY_PORT));
            result.put(JmxmpAgent.RMI_REGISTRY_PORT_PROPERTY, rmiRegistryPort);
        case JMXMP:
            if (jmxRemotePort==null || jmxRemotePort<=0)
                throw new IllegalStateException("Unsupported JMX port "+jmxRemotePort+" - when applying system properties ("+getJmxAgentMode()+" / "+getEntity()+")");
            result.put(JmxmpAgent.JMXMP_PORT_PROPERTY, jmxRemotePort);
            // with JMXMP don't try to tell it the hostname -- it isn't needed for JMXMP, and if specified
            // it will break if the hostname we see is not known at the server, e.g. a forwarding public IP
            result.remove("java.rmi.server.hostname");
            break;
        case JMX_RMI_CUSTOM_AGENT:
            if (jmxRemotePort==null || jmxRemotePort<=0)
                throw new IllegalStateException("Unsupported JMX port "+jmxRemotePort+" - when applying system properties ("+getJmxAgentMode()+" / "+getEntity()+")");
            result.put(JmxRmiAgent.RMI_REGISTRY_PORT_PROPERTY, Preconditions.checkNotNull(entity.getAttribute(UsesJmx.RMI_REGISTRY_PORT), "registry port"));
            result.put(JmxRmiAgent.JMX_SERVER_PORT_PROPERTY, jmxRemotePort);
            break;
        case NONE:
            jmxRemotePort = fixPortsForModeNone();
        case JMX_RMI:
            result.put("com.sun.management.jmxremote.port", jmxRemotePort);
            result.put("java.rmi.server.useLocalHostname", "true");
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
        if (EnumSet.of(JmxAgentModes.JMXMP_AND_RMI, JmxAgentModes.JMXMP, JmxAgentModes.JMX_RMI_CUSTOM_AGENT).contains(getJmxAgentMode())) {
            Tasks.setBlockingDetails("Copying JMX agent jar to server.");
            try {
                getMachine().get().copyTo(ResourceUtils.create(this).getResourceFromUrl(
                        getJmxAgentJarUrl()), getJmxAgentJarDestinationFilePath());
            } finally {
                Tasks.resetBlockingDetails();
            }
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
        Maybe<Object> jmx = entity.getConfigRaw(UsesJmx.JMX_AGENT_MODE, true);
        if (!jmx.isPresentAndNonNull()) {
            setConfig(UsesJmx.JMX_AGENT_MODE, JmxAgentModes.JMX_RMI_CUSTOM_AGENT);
        } else if (jmx.get()!=JmxAgentModes.JMX_RMI_CUSTOM_AGENT) {
            log.warn("Entity "+entity+" may not function unless running JMX_RMI_CUSTOM_AGENT mode (asked to use "+jmx.get()+")");
        }
    }

}

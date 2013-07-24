/*
 * Copyright 2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.messaging.kafka;

import static java.lang.String.format;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.BrooklynVersion;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.util.ssh.CommonCommands;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.entity.java.UsesJmx;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.NetworkUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableMap.Builder;
import brooklyn.util.jmx.jmxrmi.JmxRmiAgent;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public abstract class AbstractfKafkaSshDriver extends JavaSoftwareProcessSshDriver {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(KafkaZookeeperSshDriver.class);

    public AbstractfKafkaSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    protected abstract Map<String, Integer> getPortMap();

    protected abstract ConfigKey<String> getConfigTemplateKey();

    protected abstract String getConfigFileName();

    protected abstract String getLaunchScriptName();

    protected abstract String getProcessIdentifier();

    private String expandedInstallDir;

    @Override
    protected String getLogFileLocation() { return getRunDir()+"/console.out"; }

    private String getExpandedInstallDir() {
        if (expandedInstallDir == null) throw new IllegalStateException("expandedInstallDir is null; most likely install was not called");
        return expandedInstallDir;
    }

    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        expandedInstallDir = getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("kafka-%s-src", getVersion()));

        List<String> commands = new LinkedList<String>();
        commands.addAll(CommonCommands.downloadUrlAs(urls, saveAs));
        commands.add(CommonCommands.INSTALL_TAR);
        commands.add("tar xzfv "+saveAs);
        commands.add("cd "+expandedInstallDir);
        commands.add("./sbt update");
        commands.add("./sbt package");
        if (isV08()) {
            // target not known in v0.7.x but required in v0.8.0-beta1
            commands.add("./sbt assembly-package-dependency");
        }

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();
    }

    protected boolean isV08() {
        String v = getEntity().getConfig(Kafka.SUGGESTED_VERSION);
        if (v.startsWith("0.7.")) return false;
        return true;
    }
    
    @Override
    public void customize() {
        NetworkUtils.checkPortsValid(getPortMap());
        newScript(CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(format("cp -R %s/* %s", getExpandedInstallDir(), getRunDir()))
                .execute();

        String config = entity.getConfig(getConfigTemplateKey());
        copyTemplate(config, getConfigFileName());

        if (isJmxEnabled()) {
            // Copy JMX agent Jar to server
            getMachine().copyTo(new ResourceUtils(this).getResourceFromUrl(getJmxRmiAgentJarUrl()), getJmxRmiAgentJarDestinationFilePath());
        }
    }

    public String getJmxRmiAgentJarBasename() {
        return "brooklyn-jmxrmi-agent-" + BrooklynVersion.get() + ".jar";
    }

    // KAFKA requires a JMX port specified even if it is disabled in brooklyn (we just don't consume it)
    @Override
    public Integer getJmxPort() {
        return entity.getAttribute(UsesJmx.JMX_PORT);
    }
    public Integer getRmiServerPort() {
        return entity.getAttribute(UsesJmx.RMI_SERVER_PORT);
    }

    public String getJmxRmiAgentJarUrl() {
        return "classpath://" + getJmxRmiAgentJarBasename();
    }

    public String getJmxRmiAgentJarDestinationFilePath() {
        return getRunDir() + "/" + getJmxRmiAgentJarBasename();
    }

    @Override
    public void launch() {
        newScript(ImmutableMap.of("usePidFile", getPidFile()), LAUNCHING)
                .failOnNonZeroResultCode()
                .body.append(String.format("nohup ./bin/%s ./%s > console.out 2>&1 &", getLaunchScriptName(), getConfigFileName()))
                .execute();
    }

    public String getPidFile() { return getRunDir() + "/kafka.pid"; }

    @Override
    public boolean isRunning() {
        return newScript(ImmutableMap.of("usePidFile", getPidFile()), CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(ImmutableMap.of("usePidFile", false), STOPPING)
                .body.append(String.format("ps ax | grep %s | awk '{print $1}' | xargs kill", getProcessIdentifier()))
                .body.append(String.format("ps ax | grep %s | awk '{print $1}' | xargs kill -9", getProcessIdentifier()))
                .execute();
    }

    @Override
    protected Map<String, ?> getJmxJavaSystemProperties() {
        Builder<String, Object> result = MutableMap.<String, Object> builder();
        if (isJmxEnabled()) {
            // TODO JMX SSL ENABLED -- see superclass behaviour
            result.put(JmxRmiAgent.JMX_SERVER_PORT_PROPERTY, getJmxPort())
                .put(JmxRmiAgent.RMI_REGISTRY_PORT_PROPERTY, getRmiServerPort())
                .put("com.sun.management.jmxremote.ssl", false)
                .put("com.sun.management.jmxremote.authenticate", false)
                .put("java.rmi.server.hostname", getHostname());
        }
        return result.build();
    }

    @Override
    protected List<String> getJmxJavaConfigOptions() {
        if (isJmxEnabled())
            return ImmutableList.of("-javaagent:" + getJmxRmiAgentJarDestinationFilePath());
        else
            return ImmutableList.of();
    }

    /**
     * Use RMI agent to provide JMX.
     */
    @Override
    public Map<String, String> getShellEnvironment() {
        Map<String, String> orig = super.getShellEnvironment();
        String kafkaJmxOpts = orig.remove("JAVA_OPTS");
        return MutableMap.<String, String>builder()
                .putAll(orig)
                .putIfNotNull("KAFKA_JMX_OPTS", kafkaJmxOpts)
                .build();
    }

}

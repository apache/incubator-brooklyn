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
package org.apache.brooklyn.entity.webapp.jboss;

import static java.lang.String.format;
import static org.apache.brooklyn.util.text.Strings.isEmpty;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.entity.java.UsesJmx;
import org.apache.brooklyn.entity.java.UsesJmx.JmxAgentModes;
import org.apache.brooklyn.entity.webapp.JavaWebAppSshDriver;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;

public class JBoss6SshDriver extends JavaWebAppSshDriver implements JBoss6Driver {

    public static final String SERVER_TYPE = "standard";
    public static final int DEFAULT_HTTP_PORT = 8080;
    private static final String PORT_GROUP_NAME = "ports-brooklyn";

    public JBoss6SshDriver(JBoss6ServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public JBoss6ServerImpl getEntity() {
        return (JBoss6ServerImpl) super.getEntity();
    }
    
    protected String getLogFileLocation() {
        return Os.mergePathsUnix(getRunDir(), "server", SERVER_TYPE, "log/server.log");
    }

    protected String getDeploySubdir() {
        return Os.mergePathsUnix("server", SERVER_TYPE, "deploy");
    } // FIXME what is this in as6?

    protected String getBindAddress() {
        return entity.getAttribute(JBoss6Server.BIND_ADDRESS);
    }

    protected Integer getPortIncrement() {
        return entity.getAttribute(JBoss6Server.PORT_INCREMENT);
    }

    protected String getClusterName() {
        return entity.getAttribute(JBoss6Server.CLUSTER_NAME);
    }

    // FIXME Should this pattern be used elsewhere? How?
    @Override
    public String getExpandedInstallDir() {
        // Ensure never returns null, so if stop called even if install/start was not then don't throw exception.
        String result = super.getExpandedInstallDir();
        return (result != null) ? result : getInstallDir()+"/" + "jboss-"+getVersion();
    }

    @Override
    public void postLaunch() {
        entity.sensors().set(JBoss6Server.HTTP_PORT, DEFAULT_HTTP_PORT + getPortIncrement());
        super.postLaunch();
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(format("jboss-%s", getVersion()))));
    }

    @Override
    public void install() {
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        // Note the -o option to unzip, to overwrite existing files without warning.
        // The JBoss zip file contains lgpl.txt (at least) twice and the prompt to
        // overwrite interrupts the installer.

        List<String> commands = new LinkedList<String>();
        commands.addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs));
        commands.add(BashCommands.INSTALL_UNZIP);
        commands.add(format("unzip -o %s",saveAs));

        newScript(INSTALLING)
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING)
                .body.append(
                        format("mkdir -p %s/server", getRunDir()),
                        format("cd %s/server", getRunDir()),
                        format("cp -r %s/server/%s %s", getExpandedInstallDir(), SERVER_TYPE, SERVER_TYPE),
                        format("cd %s/conf/bindingservice.beans/META-INF/",SERVER_TYPE),
                        "BJB=\"bindings-jboss-beans.xml\"",
                        format("sed -i.bk 's/ports-03/%s/' $BJB",PORT_GROUP_NAME),
                        format("sed -i.bk 's/<parameter>300<\\/parameter>/<parameter>%s<\\/parameter>/' $BJB",getPortIncrement())
                )
                .execute();

        getEntity().deployInitialWars();
    }

    @Override
    public void launch() {
        Map<String,Integer> ports = new HashMap<String, Integer>();
        ports.put("httpPort",getHttpPort());
        if (isJmxEnabled()) ports.put("jmxPort",getJmxPort());

        Networking.checkPortsValid(ports);

        String clusterArg = isEmpty(getClusterName()) ? "":"-g "+getClusterName();
        // run.sh must be backgrounded otherwise the script will never return.

        // Don't automatically create pid; instead set JBOSS_PIDFILE to create the pid file we need
        // We wait for evidence of tomcat running because, using SshCliTool,
        // we saw the ssh session return before the tomcat process was fully running 
        // so the process failed to start.
        newScript(MutableMap.of(USE_PID_FILE, false), LAUNCHING)
                .body.append(
                        format("export JBOSS_CLASSPATH=%s/lib/jboss-logmanager.jar",getExpandedInstallDir()),
                        format("export JBOSS_PIDFILE=%s/%s", getRunDir(), PID_FILENAME),
                        format("%s/bin/run.sh -Djboss.service.binding.set=%s -Djboss.server.base.dir=$RUN_DIR/server ",getExpandedInstallDir(),PORT_GROUP_NAME) +
                                format("-Djboss.server.base.url=file://$RUN_DIR/server -Djboss.messaging.ServerPeerID=%s ",entity.getId())+
                                format("-Djboss.boot.server.log.dir=%s/server/%s/log ",getRunDir(),SERVER_TYPE) +
                                format("-b %s %s -c %s ", getBindAddress(), clusterArg,SERVER_TYPE) +
                                ">>$RUN_DIR/console 2>&1 </dev/null &",
                        "for i in {1..10}\n" +
                                "do\n" +
                                "    grep -i 'starting' "+getRunDir()+"/console && exit\n" +
                                "    sleep 1\n" +
                                "done\n" +
                                "echo \"Couldn't determine if process is running (console output does not contain 'starting'); continuing but may subsequently fail\""
                    )
                .execute();
    }

    @Override
    public boolean isRunning() {
        JmxAgentModes jmxMode = entity.getConfig(UsesJmx.JMX_AGENT_MODE);
        if (jmxMode == JmxAgentModes.JMX_RMI_CUSTOM_AGENT) {
            String host = entity.getAttribute(Attributes.HOSTNAME);
            Integer port = entity.getAttribute(UsesJmx.RMI_REGISTRY_PORT);

            List<String> checkRunningScript = new LinkedList<String>();
            checkRunningScript.add(
                    format("%s/bin/twiddle.sh --host %s --port %s get \"jboss.system:type=Server\" Started | grep true || exit 1",
                            getExpandedInstallDir(), host, port));

            //have to override the CLI/JMX options

            Map<String, Object> flags = new LinkedHashMap<String, Object>();
            flags.put("env", new LinkedHashMap<String, String>());

            int result = execute(flags, checkRunningScript, "checkRunning " + entity + " on " + getMachine());
            if (result == 0) return true;
            if (result == 1) return false;
            throw new IllegalStateException(format("%s running check gave result code %s",getEntity(),result));
        } else {
            return newScript(MutableMap.of(USE_PID_FILE, true), CHECK_RUNNING).execute() == 0;
        }
    }

    @Override
    public void stop() {
        JmxAgentModes jmxMode = entity.getConfig(UsesJmx.JMX_AGENT_MODE);
        if (jmxMode == JmxAgentModes.JMX_RMI_CUSTOM_AGENT) {
            String host = entity.getAttribute(Attributes.HOSTNAME);
            Integer port = entity.getAttribute(UsesJmx.RMI_REGISTRY_PORT);
            List<String> shutdownScript = new LinkedList<String>();
            shutdownScript.add(format("%s/bin/shutdown.sh --host %s --port %s -S", getExpandedInstallDir(), host, port));

            //again, messy copy of parent; but new driver scheme could allow script-helper to customise parameters
            log.debug("invoking shutdown script for {}: {}", entity, shutdownScript);
            Map<String, Object> flags = new LinkedHashMap<String, Object>();
            flags.put("env", new LinkedHashMap<String, String>());
            int result = execute(flags, shutdownScript, "shutdown " + entity + " on " + getMachine());
            if (result != 0) log.warn("non-zero result code terminating {}: {}", entity, result);
            log.debug("done invoking shutdown script for {}", entity);
        } else {
            newScript(MutableMap.of(USE_PID_FILE, true), STOPPING).execute();
        }
    }

    @Override
    protected List<String> getCustomJavaConfigOptions() {
        return MutableList.<String>builder()
                .addAll(super.getCustomJavaConfigOptions())
                .add("-Xms200m")
                .add("-Xmx800m")
                .add("-XX:MaxPermSize=400m")
                .build();
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        return MutableMap.<String, String>builder()
                .putAll(super.getShellEnvironment())
                .put("LAUNCH_JBOSS_IN_BACKGROUND", "1")
                .put("RUN", getRunDir())
                .build();
    }

    @Override
    protected Map getCustomJavaSystemProperties() {
        return MutableMap.<String, String>builder()
                .put("jboss.platform.mbeanserver", null)
                .put("javax.management.builder.initial", "org.jboss.system.server.jmx.MBeanServerBuilderImpl")
                .put("java.util.logging.manager", "org.jboss.logmanager.LogManager")
                .put("org.jboss.logging.Logger.pluginClass", "org.jboss.logging.logmanager.LoggerPluginImpl")
                .build();
    }
}

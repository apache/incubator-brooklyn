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
package brooklyn.entity.webapp.tomcat;

import static java.lang.String.format;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.webapp.JavaWebAppSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.StringEscapes.BashStringEscapes;

public class Tomcat7SshDriver extends JavaWebAppSshDriver implements Tomcat7Driver {

    public Tomcat7SshDriver(TomcatServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    protected String getLogFileLocation() {
        return Os.mergePathsUnix(getRunDir(), "logs/catalina.out");
    }

    protected String getDeploySubdir() {
       return "webapps";
    }

    protected Integer getShutdownPort() {
        return entity.getAttribute(TomcatServerImpl.SHUTDOWN_PORT);
    }
    
    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName("apache-tomcat-"+getVersion())));
    }

    @Override
    public void install() {
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        List<String> commands = new LinkedList<String>();
        commands.addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs));
        commands.add(BashCommands.INSTALL_TAR);
        commands.add(format("tar xvzf %s",saveAs));

        newScript(INSTALLING)
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING)
                .body.append(
                        "mkdir conf logs webapps temp",
                        format("cp %s/conf/{server,web}.xml conf/", getExpandedInstallDir()),
                        format("sed -i.bk s/8080/%s/g conf/server.xml", getHttpPort()),
                        format("sed -i.bk s/8005/%s/g conf/server.xml", getShutdownPort()),
                        "sed -i.bk /8009/D conf/server.xml"
                    )
                .execute();

        getEntity().deployInitialWars();
    }

    @Override
    public void launch() {
        Map ports = MutableMap.of("httpPort", getHttpPort(), "shutdownPort", getShutdownPort());
        Networking.checkPortsValid(ports);

        // We wait for evidence of tomcat running because, using 
        // brooklyn.ssh.config.tool.class=brooklyn.util.internal.ssh.cli.SshCliTool,
        // we saw the ssh session return before the tomcat process was fully running 
        // so the process failed to start.
        newScript(MutableMap.of(USE_PID_FILE, false), LAUNCHING)
                .body.append(
                        format("%s/bin/startup.sh >>$RUN/console 2>&1 </dev/null",getExpandedInstallDir()),
                        "for i in {1..10}\n" +
                        "do\n" +
                        "    if [ -s "+getLogFileLocation()+" ]; then exit; fi\n" +
                        "    sleep 1\n" +
                        "done\n" +
                        "echo \"Couldn't determine if tomcat-server is running (logs/catalina.out is still empty); continuing but may subsequently fail\""
                    )
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of(USE_PID_FILE, "pid.txt"), CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, "pid.txt"), STOPPING).execute();
    }

    @Override
    public void kill() {
        newScript(MutableMap.of(USE_PID_FILE, "pid.txt"), KILLING).execute();
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
        Map<String, String> shellEnv =  MutableMap.<String, String>builder()
                .putAll(super.getShellEnvironment())
                .remove("JAVA_OPTS")
                .put("CATALINA_PID", "pid.txt")
                .put("CATALINA_BASE", getRunDir())
                .put("RUN", getRunDir())
                .build();

        // Double quoting of individual JAVA_OPTS entries required due to eval in catalina.sh
        List<String> javaOpts = getJavaOpts();
        String sJavaOpts = BashStringEscapes.doubleQuoteLiteralsForBash(javaOpts.toArray(new String[0]));
        shellEnv.put("CATALINA_OPTS", sJavaOpts);

        return shellEnv;
    }
}

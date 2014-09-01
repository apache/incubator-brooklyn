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
package brooklyn.entity.monitoring.monit;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MonitSshDriver extends AbstractSoftwareProcessSshDriver implements MonitDriver {
    
    private String expandedInstallDir;
    private String remoteControlFilePath;
    
    public MonitSshDriver(MonitNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(format("monit-%s", getVersion()))));
    }

    @Override
    public void install() {
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        List<String> commands = ImmutableList.<String>builder()
            .add(BashCommands.INSTALL_TAR)
            .add(BashCommands.INSTALL_CURL)
            .add(BashCommands.commandToDownloadUrlsAs(urls, saveAs))
            .add(format("tar xfvz %s", saveAs))
            .build();
        
        newScript(INSTALLING)
            .failOnNonZeroResultCode()
            .body
            .append(commands)
            .execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING)
            .body.append("echo copying control file")
            .execute();  //create the directory
        String controlFileUrl = getEntity().getConfig(MonitNode.CONTROL_FILE_URL);
        remoteControlFilePath = getRunDir() + "/monit.monitrc";
        copyTemplate(controlFileUrl, remoteControlFilePath, getEntity().getConfig(MonitNode.CONTROL_FILE_SUBSTITUTIONS));
        // Monit demands the control file has permissions <= 0700
        newScript(CUSTOMIZING)
            .body.append("chmod 600 " + remoteControlFilePath)
            .execute();
    }

    @Override
    public void launch() {
        // NOTE: executing monit in daemon mode will spawn a separate process for the monit daemon so the value of $! cannot be used
        // instead we use the -p argument
        String command = format("touch %s && nohup %s/bin/monit -c %s -p %s > out.log 2> err.log < /dev/null &", getMonitPidFile(),
            expandedInstallDir, remoteControlFilePath, getMonitPidFile());
        newScript(MutableMap.of("usePidFile", false), LAUNCHING)
            .updateTaskAndFailOnNonZeroResultCode()
            .body.append(command)
            .execute();
    }
    
    @Override
    public boolean isRunning() {
        Map flags = ImmutableMap.of("usePidFile", getMonitPidFile());
        return newScript(flags, CHECK_RUNNING).execute() == 0;
    }
    
    @Override
    public void stop() {
        new ScriptHelper(this, "Send SIGTERM to Monit process")
            .body.append("kill -s SIGTERM `cat " + getMonitPidFile() + "`")
            .execute();        
    }
    
    protected String getMonitPidFile() {
        // Monit seems to dislike starting with a relative path to a pid file.
        return getRunDir() + "/" + AbstractSoftwareProcessSshDriver.PID_FILENAME;
    }
    
    public String getMonitLogFile() {
        return getRunDir() + "/monit.log";
    }

    public String getOsTag() {
        OsDetails os = getLocation().getOsDetails();
        if (os == null) {
            // Default to 64 bit linux
            return "linux-x64";
        } else if (os.isMac()) {
            return "macosx-universal";
        } else {
            String arch = os.is64bit() ? "x64" : "x86";
            return "linux-" + arch;
        }
    }
    
    @Override
    public String getStatusCmd() {
        return format("%s/bin/monit -c %s status", expandedInstallDir, remoteControlFilePath);
    }
    
    @Override
    public String getExpandedInstallDir() {
        return expandedInstallDir;
    }
}

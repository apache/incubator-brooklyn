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
package org.apache.brooklyn.entity.database.crate;

import static java.lang.String.format;

import java.util.List;

import org.apache.brooklyn.api.internal.EntityLocal;
import org.apache.brooklyn.entity.core.Entities;
import org.apache.brooklyn.entity.java.JavaSoftwareProcessSshDriver;

import com.google.common.collect.ImmutableList;

import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;

public class CrateNodeSshDriver extends JavaSoftwareProcessSshDriver {

    public CrateNodeSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(),
                resolver.getUnpackedDirectoryName(format("crate-%s", getVersion()))));
    }

    @Override
    public void install() {
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        List<String> commands = ImmutableList.<String>builder()
                .addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs))
                .add("tar xvfz "+saveAs)
                .build();

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands).execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING)
                .body.append("mkdir -p " + getDataLocation())
                .execute();
        copyTemplate(entity.getConfig(CrateNode.SERVER_CONFIG_URL), getConfigFileLocation());
    }

    @Override
    public void launch() {
        StringBuilder command = new StringBuilder(getExpandedInstallDir())
                .append("/bin/crate ")
                .append(" -d")
                .append(" -p ").append(getPidFileLocation())
                .append(" -Des.config=").append(getConfigFileLocation());
        newScript(LAUNCHING)
                .failOnNonZeroResultCode()
                .body.append(command).execute();

    }

    @Override
    public boolean isRunning() {
        return newScript (MutableMap.of("usePidFile", getPidFileLocation()), CHECK_RUNNING)
                .execute() == 0;
    }

    @Override
    public void stop() {
        // See https://crate.io/docs/stable/cli.html#signal-handling.
        newScript(STOPPING)
                .body.append("kill -USR2 `cat " + getPidFileLocation() + "`")
                .execute();
    }

    protected String getConfigFileLocation() {
        return Urls.mergePaths(getRunDir(), "config.yaml");
    }

    @Override
    public String getLogFileLocation() {
        return Urls.mergePaths(getRunDir(), "crate.log");
    }

    protected String getPidFileLocation () {
        return Urls.mergePaths(getRunDir(), "pid.txt");
    }

    // public for use in template too.
    public String getDataLocation() {
        return Urls.mergePaths(getRunDir(), "data");
    }

}

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
package brooklyn.entity.database.rubyrep;

import static java.lang.String.format;

import java.io.Reader;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.stream.Streams;

import com.google.common.collect.ImmutableList;

public class RubyRepSshDriver extends AbstractSoftwareProcessSshDriver implements RubyRepDriver {

    public static final Logger log = LoggerFactory.getLogger(RubyRepSshDriver.class);

    public RubyRepSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);

        entity.setAttribute(Attributes.LOG_FILE_LOCATION, getLogFileLocation());
    }

    protected String getLogFileLocation() {
        return getRunDir() + "/log/rubyrep.log";
    }

    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        List<String> commands = ImmutableList.<String>builder()
                .addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs))
                .add(BashCommands.INSTALL_UNZIP)
                .add("unzip " + saveAs)
                .build();

        newScript(INSTALLING)
                .body.append(commands)
                .failOnNonZeroResultCode()
                .execute();

        setExpandedInstallDir(getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("rubyrep-%s", getVersion())));
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING)
                .body.append(format("cp -R %s %s", getExpandedInstallDir(), getRunDir()))
                .failOnNonZeroResultCode()
                .execute();
        try {
            customizeConfiguration();
        } catch (Exception e) {
            log.error("Failed to configure rubyrep, replication is unlikely to succeed", e);
        }
    }

    protected void customizeConfiguration() throws ExecutionException, InterruptedException, URISyntaxException {
        log.info("Copying creation script " + getEntity().toString());

        // TODO check these semantics are what we really want?
        String configScriptUrl = entity.getConfig(RubyRepNode.CONFIGURATION_SCRIPT_URL);
        Reader configContents;
        if (configScriptUrl != null) {
            // If set accept as-is
            configContents = Streams.reader(resource.getResourceFromUrl(configScriptUrl));
        } else {
            String configScriptContents = processTemplate(entity.getConfig(RubyRepNode.TEMPLATE_CONFIGURATION_URL));
            configContents = Streams.newReaderWithContents(configScriptContents);
        }

        getMachine().copyTo(configContents, getRunDir() + "/rubyrep.conf");
    }

    @Override
    public void launch() {
        newScript(MutableMap.of("usePidFile", true), LAUNCHING)
                .body.append(format("nohup rubyrep-%s/jruby/bin/jruby rubyrep-%s/bin/rubyrep replicate -c rubyrep.conf > ./console 2>&1 &", getVersion(), getVersion()))
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", true), CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of("usePidFile", true), STOPPING).execute();
    }
}
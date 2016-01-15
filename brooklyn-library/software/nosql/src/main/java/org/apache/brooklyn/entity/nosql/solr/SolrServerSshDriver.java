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
package org.apache.brooklyn.entity.nosql.solr;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.java.UsesJmx;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.file.ArchiveUtils;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.stream.Streams;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

/**
 * Start a {@link SolrServer} in a {@link Location} accessible over ssh.
 */
public class SolrServerSshDriver extends JavaSoftwareProcessSshDriver implements SolrServerDriver {

    private static final Logger log = LoggerFactory.getLogger(SolrServerSshDriver.class);

    public SolrServerSshDriver(SolrServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public Integer getSolrPort() { return entity.getAttribute(SolrServer.SOLR_PORT); }

    @Override
    public String getSolrConfigTemplateUrl() { return entity.getConfig(SolrServer.SOLR_CONFIG_TEMPLATE_URL); }

    public String getMirrorUrl() { return entity.getConfig(SolrServer.MIRROR_URL); }

    public String getPidFile() { return Os.mergePaths(getRunDir(), "solr.pid"); }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(format("solr-%s", getVersion()))));
    }

    @Override
    public void install() {
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        List<String> commands = ImmutableList.<String>builder()
                .addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs))
                .add(BashCommands.INSTALL_TAR)
                .add("tar xzfv " + saveAs)
                .build();

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();
    }

    public Set<Integer> getPortsUsed() {
        Set<Integer> result = Sets.newLinkedHashSet(super.getPortsUsed());
        result.addAll(getPortMap().values());
        return result;
    }

    private Map<String, Integer> getPortMap() {
        return ImmutableMap.<String, Integer>builder()
                .put("solrPort", getSolrPort())
                .put("jmxPort", entity.getAttribute(UsesJmx.JMX_PORT))
                .put("rmiPort", entity.getAttribute(UsesJmx.RMI_REGISTRY_PORT))
                .build();
    }

    @Override
    public void customize() {
        log.debug("Customizing {}", entity);
        Networking.checkPortsValid(getPortMap());

        ImmutableList.Builder<String> commands = new ImmutableList.Builder<String>()
                .add("mkdir contrib")
                .add("mkdir solr")
                .add(String.format("cp -R %s/example/{etc,contexts,lib,logs,resources,webapps} .", getExpandedInstallDir()))
                .add(String.format("cp %s/example/start.jar .", getExpandedInstallDir()))
                .add(String.format("cp %s/dist/*.jar lib/", getExpandedInstallDir()))
                .add(String.format("cp %s/contrib/*/lib/*.jar contrib/", getExpandedInstallDir()));

        newScript(CUSTOMIZING)
                .body.append(commands.build())
                .execute();

        // Copy the solr.xml configuration file across
        String configFileContents = processTemplate(getSolrConfigTemplateUrl());
        String destinationConfigFile = String.format("%s/solr/solr.xml", getRunDir());
        getMachine().copyTo(Streams.newInputStreamWithContents(configFileContents), destinationConfigFile);

        // Copy the core definitions across
        Map<String, String> coreConfig = entity.getConfig(SolrServer.SOLR_CORE_CONFIG);
        for (String core : coreConfig.keySet()) {
            String url = coreConfig.get(core);
            String solr = Urls.mergePaths(getRunDir(), "solr");
            ArchiveUtils.deploy(url, getMachine(), solr);
        }
    }

    @Override
    public void launch() {
        newScript(MutableMap.of(USE_PID_FILE, getPidFile()), LAUNCHING)
                .body.append("nohup java $JAVA_OPTS -jar start.jar > ./logs/console.log 2>&1 &")
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of(USE_PID_FILE, getPidFile()), CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, getPidFile()), STOPPING).execute();
    }

    @Override
    protected String getLogFileLocation() {
        return Urls.mergePaths(getRunDir(), "solr", "logs", "solr.log");
    }
}

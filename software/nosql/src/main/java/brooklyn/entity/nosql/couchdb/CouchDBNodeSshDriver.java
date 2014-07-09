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
package brooklyn.entity.nosql.couchdb;

import static brooklyn.util.ssh.BashCommands.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Attributes;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

/**
 * Start a {@link CouchDBNode} in a {@link Location} accessible over ssh.
 */
public class CouchDBNodeSshDriver extends AbstractSoftwareProcessSshDriver implements CouchDBNodeDriver {

    private static final Logger log = LoggerFactory.getLogger(CouchDBNodeSshDriver.class);

    public CouchDBNodeSshDriver(CouchDBNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);

        entity.setAttribute(Attributes.LOG_FILE_LOCATION, getLogFileLocation());
    }

    public String getLogFileLocation() { return Os.mergePathsUnix(getRunDir(), "couchdb.log"); }

    @Override
    public Integer getHttpPort() { return entity.getAttribute(CouchDBNode.HTTP_PORT); }

    @Override
    public Integer getHttpsPort() { return entity.getAttribute(CouchDBNode.HTTPS_PORT); }

    @Override
    public String getClusterName() { return entity.getAttribute(CouchDBNode.CLUSTER_NAME); }

    @Override
    public String getCouchDBConfigTemplateUrl() { return entity.getAttribute(CouchDBNode.COUCHDB_CONFIG_TEMPLATE_URL); }

    @Override
    public String getCouchDBUriTemplateUrl() { return entity.getAttribute(CouchDBNode.COUCHDB_URI_TEMPLATE_URL); }

    @Override
    public String getCouchDBConfigFileName() { return entity.getAttribute(CouchDBNode.COUCHDB_CONFIG_FILE_NAME); }

    public String getErlangVersion() { return entity.getConfig(CouchDBNode.ERLANG_VERSION); }

    @Override
    public void install() {
        log.info("Installing {}", entity);
        List<String> commands = ImmutableList.<String>builder()
                .add(ifExecutableElse0("zypper", chainGroup( // SLES 11 not supported, would require building from source
                        ok(sudo("zypper --non-interactive addrepo http://download.opensuse.org/repositories/devel:/languages:/erlang/openSUSE_11.4 erlang_suse_11")),
                        ok(sudo("zypper --non-interactive addrepo http://download.opensuse.org/repositories/devel:/languages:/erlang/openSUSE_12.3 erlang_suse_12")),
                        ok(sudo("zypper --non-interactive addrepo http://download.opensuse.org/repositories/devel:/languages:/erlang/openSUSE_13.1 erlang_suse_13")),
                        ok(sudo("zypper --non-interactive addrepo http://download.opensuse.org/repositories/server:/database/openSUSE_11.4 db_suse_11")),
                        ok(sudo("zypper --non-interactive addrepo http://download.opensuse.org/repositories/server:/database/openSUSE_12.3 db_suse_12")),
                        ok(sudo("zypper --non-interactive addrepo http://download.opensuse.org/repositories/server:/database/openSUSE_13.1 db_suse_13")))))
                .add(installPackage( // NOTE only 'port' states the version of Erlang used, maybe remove this constraint?
                        ImmutableMap.of(
                                "apt", "erlang-nox erlang-dev",
                                "port", "erlang@"+getErlangVersion()+"+ssl"),
                        "erlang"))
                .add(installPackage("couchdb"))
                .add(ifExecutableElse0("service", sudo("service couchdb stop")))
                .build();

        newScript(INSTALLING)
                .body.append(commands)
                .execute();
    }

    @Override
    public Set<Integer> getPortsUsed() {
        Set<Integer> result = Sets.newLinkedHashSet(super.getPortsUsed());
        result.addAll(getPortMap().values());
        return result;
    }

    private Map<String, Integer> getPortMap() {
        return ImmutableMap.<String, Integer>builder()
                .put("httpPort", getHttpPort())
                .build();
    }

    @Override
    public void customize() {
        log.info("Customizing {} (Cluster {})", entity, getClusterName());
        Networking.checkPortsValid(getPortMap());

        newScript(CUSTOMIZING).execute();

        // Copy the configuration files across
        String destinationConfigFile = Os.mergePathsUnix(getRunDir(), getCouchDBConfigFileName());
        copyTemplate(getCouchDBConfigTemplateUrl(), destinationConfigFile);
        String destinationUriFile = Os.mergePathsUnix(getRunDir(), "couch.uri");
        copyTemplate(getCouchDBUriTemplateUrl(), destinationUriFile);
    }

    @Override
    public void launch() {
        log.info("Launching  {}", entity);
        newScript(MutableMap.of(USE_PID_FILE, false), LAUNCHING)
                .body.append(sudo(String.format("nohup couchdb -p %s -a %s -o couchdb-console.log -e couchdb-error.log -b &", getPidFile(), Os.mergePathsUnix(getRunDir(), getCouchDBConfigFileName()))))
                .execute();
    }

    public String getPidFile() { return Os.mergePathsUnix(getRunDir(), "couchdb.pid"); }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of(USE_PID_FILE, false), CHECK_RUNNING)
                .body.append(sudo(String.format("couchdb -p %s -s", getPidFile())))
                .execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, false), STOPPING)
                .body.append(sudo(String.format("couchdb -p %s -k", getPidFile())))
                .failOnNonZeroResultCode()
                .execute();
    }
}

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
package brooklyn.entity.nosql.couchbase;

import static brooklyn.util.ssh.BashCommands.INSTALL_CURL;
import static brooklyn.util.ssh.BashCommands.alternatives;
import static brooklyn.util.ssh.BashCommands.chainGroup;
import static brooklyn.util.ssh.BashCommands.sudo;
import static java.lang.String.format;

import java.util.List;

import javax.annotation.Nullable;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class CouchbaseSyncGatewaySshDriver extends AbstractSoftwareProcessSshDriver implements CouchbaseSyncGatewayDriver {
    public CouchbaseSyncGatewaySshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void stop() {

    }

    @Override
    public void install() {
        //reference http://docs.couchbase.com/sync-gateway/#getting-started-with-sync-gateway
        DownloadResolver resolver = Entities.newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();

        log.info("Installing couchbase-sync-gateway version: {}", getVersion());
        if (osDetails.isLinux()) {
            List<String> commands = installLinux(urls, saveAs);
            newScript(INSTALLING)
                    .body.append(commands).execute();
        }
    }

    @Override
    public void customize() {

    }

    @Override
    public void launch() {
        Entity cbNode = entity.getConfig(CouchbaseSyncGateway.COUCHBASE_SERVER);
        Entities.waitForServiceUp(cbNode, Duration.ONE_HOUR);
        DependentConfiguration.waitInTaskForAttributeReady(cbNode, CouchbaseCluster.IS_CLUSTER_INITIALIZED, Predicates.equalTo(true));
        // Even once the bucket has published its API URL, it can still take a couple of seconds for it to become available
        Time.sleep(10 * 1000);
        if (cbNode instanceof CouchbaseCluster) {
            Optional<Entity> cbClusterNode = Iterables.tryFind(cbNode.getAttribute(CouchbaseCluster.GROUP_MEMBERS), new Predicate<Entity>() {

                @Override
                public boolean apply(@Nullable Entity entity) {
                    if (entity instanceof CouchbaseNode && Boolean.TRUE.equals(entity.getAttribute(CouchbaseNode.IS_IN_CLUSTER))) {
                        return true;
                    }
                    return false;
                }
            });
            if (cbClusterNode.isPresent()) {
                cbNode = cbClusterNode.get();
            } else {
                throw new IllegalArgumentException(format("The cluster %s does not contain any suitable Couchbase nodes to connect to..", cbNode.getId()));
            }

        }
        String hostname = cbNode.getAttribute(CouchbaseNode.HOSTNAME);
        String webPort = cbNode.getAttribute(CouchbaseNode.COUCHBASE_WEB_ADMIN_PORT).toString();


        String username = cbNode.getConfig(CouchbaseNode.COUCHBASE_ADMIN_USERNAME);
        String password = cbNode.getConfig(CouchbaseNode.COUCHBASE_ADMIN_PASSWORD);

        String bucketName = entity.getConfig(CouchbaseSyncGateway.COUCHBASE_SERVER_BUCKET);
        String pool = entity.getConfig(CouchbaseSyncGateway.COUCHBASE_SERVER_POOL);
        String pretty = entity.getConfig(CouchbaseSyncGateway.PRETTY) ? "-pretty" : "";
        String verbose = entity.getConfig(CouchbaseSyncGateway.VERBOSE) ? "-verbose" : "";

        String adminRestApiPort = entity.getConfig(CouchbaseSyncGateway.ADMIN_REST_API_PORT).iterator().next().toString();
        String syncRestApiPort = entity.getConfig(CouchbaseSyncGateway.SYNC_REST_API_PORT).iterator().next().toString();

        String serverWebAdminUrl = format("http://%s:%s@%s:%s", username, password, hostname, webPort);
        String options = format("-url %s -bucket %s -adminInterface 0.0.0.0:%s -interface 0.0.0.0:%s -pool %s %s %s",
                serverWebAdminUrl, bucketName, adminRestApiPort, syncRestApiPort, pool, pretty, verbose);

        newScript(ImmutableMap.of("usePidFile", true), LAUNCHING)
                .body.append(format("/opt/couchbase-sync-gateway/bin/sync_gateway %s ", options) + "> out.log 2> err.log < /dev/null &")
                .failOnNonZeroResultCode()
                .execute();
    }
    
    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", true), CHECK_RUNNING).execute() == 0;
    }
    
    @Override
    public void kill() {
        newScript(MutableMap.of("usePidFile", true), KILLING).execute();
    }

    private List<String> installLinux(List<String> urls, String saveAs) {

        String apt = chainGroup(
                "which apt-get",
                sudo("apt-get update"),
                sudo(format("dpkg -i %s", saveAs)));

        String yum = chainGroup(
                "which yum",
                sudo(format("rpm --install %s", saveAs)));

        return ImmutableList.<String>builder()
                .add(INSTALL_CURL)
                .addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs))
                .add(alternatives(apt, yum))
                .build();
    }

    @Override
    public String getOsTag() {
        OsDetails os = getLocation().getOsDetails();
        if (os == null) {
            // Default to generic linux
            return "x86_64.rpm";
        } else {
            //FIXME should be a better way to check for OS name and version
            String osName = os.getName().toLowerCase();
            String fileExtension = osName.contains("deb") || osName.contains("ubuntu") ? ".deb" : ".rpm";
            String arch = os.is64bit() ? "x86_64" : "x86";
            return arch + fileExtension;
        }
    }

}
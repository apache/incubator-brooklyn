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
package org.apache.brooklyn.entity.nosql.couchbase;

import static brooklyn.util.ssh.BashCommands.*;
import static java.lang.String.format;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.brooklyn.management.Task;
import org.apache.http.auth.UsernamePasswordCredentials;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.drivers.downloads.BasicDownloadRequirement;
import brooklyn.entity.drivers.downloads.DownloadProducerFromUrlAttribute;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.location.OsDetails;
import brooklyn.location.access.BrooklynAccessUtils;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.TaskTags;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.NaturalOrderComparator;
import brooklyn.util.text.StringEscapes.BashStringEscapes;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;

public class CouchbaseNodeSshDriver extends AbstractSoftwareProcessSshDriver implements CouchbaseNodeDriver {

    public CouchbaseNodeSshDriver(final CouchbaseNodeImpl entity, final SshMachineLocation machine) {
        super(entity, machine);
    }

    public static String couchbaseCli(String cmd) {
        return "/opt/couchbase/bin/couchbase-cli " + cmd + " ";
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(getInstallDir());
    }

    @Override
    public void install() {
        //for reference https://github.com/urbandecoder/couchbase/blob/master/recipes/server.rb
        //installation instructions (http://docs.couchbase.com/couchbase-manual-2.5/cb-install/#preparing-to-install)

        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        OsDetails osDetails = getMachine().getMachineDetails().getOsDetails();

        if (osDetails.isLinux()) {
            List<String> commands = installLinux(urls, saveAs);
            //FIXME installation return error but the server is up and running.
            newScript(INSTALLING)
                    .body.append(commands).execute();
        } else {
            Tasks.markInessential();
            throw new IllegalStateException("Unsupported OS for installing Couchbase. Will continue but may fail later.");
        }
    }

    private List<String> installLinux(List<String> urls, String saveAs) {

        log.info("Installing " + getEntity() + " using couchbase-server-{} {}", getCommunityOrEnterprise(), getVersion());

        String apt = chainGroup(
                installPackage(MutableMap.of("apt", "python-httplib2 libssl0.9.8"), null),
                sudo(format("dpkg -i %s", saveAs)));

        String yum = chainGroup(
                "which yum",
                // The following prevents failure on RHEL AWS nodes:
                // https://forums.aws.amazon.com/thread.jspa?threadID=100509
                ok(sudo("sed -i.bk s/^enabled=1$/enabled=0/ /etc/yum/pluginconf.d/subscription-manager.conf")),
                ok(sudo("yum check-update")),
                sudo("yum install -y pkgconfig"),
                // RHEL requires openssl version 098
                sudo("[ -f /etc/redhat-release ] && (grep -i \"red hat\" /etc/redhat-release && sudo yum install -y openssl098e) || :"),
                sudo(format("rpm --install %s", saveAs)));

        String link = new DownloadProducerFromUrlAttribute().apply(new BasicDownloadRequirement(this)).getPrimaryLocations().iterator().next();
        return ImmutableList.<String>builder()
                .add(INSTALL_CURL)
                .addAll(Arrays.asList(INSTALL_CURL,
                        BashCommands.require(BashCommands.alternatives(BashCommands.simpleDownloadUrlAs(urls, saveAs),
                                        // Referer link is required for 3.0.0; note mis-spelling is correct, as per http://en.wikipedia.org/wiki/HTTP_referer
                                        "curl -f -L -k " + BashStringEscapes.wrapBash(link)
                                                + " -H 'Referer: http://www.couchbase.com/downloads'"
                                                + " -o " + saveAs),
                                "Could not retrieve " + saveAs + " (from " + urls.size() + " sites)", 9)))
                .add(alternatives(apt, yum))
                .build();
    }

    @Override
    public void customize() {
        //TODO: add linux tweaks for couchbase
        //http://blog.couchbase.com/often-overlooked-linux-os-tweaks
        //http://blog.couchbase.com/kirk

        //turn off swappiness
        //vm.swappiness=0
        //sudo echo 0 > /proc/sys/vm/swappiness

        //os page cache = 20%

        //disable THP
        //sudo echo never > /sys/kernel/mm/transparent_hugepage/enabled
        //sudo echo never > /sys/kernel/mm/transparent_hugepage/defrag

        //turn off transparent huge pages
        //limit page cache disty bytes
        //control the rate page cache is flused ... vm.dirty_*
    }

    @Override
    public void launch() {
        String clusterPrefix = "--cluster-" + (isPreV3() ? "init-" : "");
        // in v30, the cluster arguments were changed, and it became mandatory to supply a url + password (if there is none, these are ignored)
        newScript(LAUNCHING)
                .body.append(
                sudo("/etc/init.d/couchbase-server start"),
                "for i in {0..120}\n" +
                        "do\n" +
                        "    if [ $i -eq 120 ]; then echo REST API unavailable after 120 seconds, failing; exit 1; fi;\n" +
                        "    curl -s " + String.format("http://localhost:%s", getWebPort()) + " > /dev/null && echo REST API available after $i seconds && break\n" +
                        "    sleep 1\n" +
                        "done\n" +
                        couchbaseCli("cluster-init") +
                        (isPreV3() ? getCouchbaseHostnameAndPort() : getCouchbaseHostnameAndCredentials()) +
                        " " + clusterPrefix + "username=" + getUsername() +
                        " " + clusterPrefix + "password=" + getPassword() +
                        " " + clusterPrefix + "port=" + getWebPort() +
                        " " + clusterPrefix + "ramsize=" + getClusterInitRamSize())
                .execute();
    }

    @Override
    public boolean isRunning() {
        //TODO add a better way to check if couchbase server is running
        return (newScript(CHECK_RUNNING)
                .body.append(format("curl -u %s:%s http://localhost:%s/pools/nodes", getUsername(), getPassword(), getWebPort()))
                .execute() == 0);
    }

    @Override
    public void stop() {
        newScript(STOPPING)
                .body.append(sudo("/etc/init.d/couchbase-server stop"))
                .execute();
    }

    @Override
    public String getVersion() {
        return entity.getConfig(CouchbaseNode.SUGGESTED_VERSION);
    }

    @Override
    public String getOsTag() {
        return newDownloadLinkSegmentComputer().getOsTag();
    }

    protected DownloadLinkSegmentComputer newDownloadLinkSegmentComputer() {
        return new DownloadLinkSegmentComputer(getLocation().getOsDetails(), !isPreV3(), Strings.toString(getEntity()));
    }

    public static class DownloadLinkSegmentComputer {
        // links are:
        // http://packages.couchbase.com/releases/2.2.0/couchbase-server-community_2.2.0_x86_64.rpm
        // http://packages.couchbase.com/releases/2.2.0/couchbase-server-community_2.2.0_x86_64.deb
        // ^^^ preV3 is _ everywhere
        // http://packages.couchbase.com/releases/3.0.0/couchbase-server-community_3.0.0-ubuntu12.04_amd64.deb
        // ^^^ most V3 is _${version}-
        // http://packages.couchbase.com/releases/3.0.0/couchbase-server-community-3.0.0-centos6.x86_64.rpm
        // ^^^ but RHEL is -${version}-

        @Nullable
        private final OsDetails os;
        @Nonnull
        private final boolean isV3OrLater;
        @Nonnull
        private final String context;
        @Nonnull
        private final String osName;
        @Nonnull
        private final boolean isRpm;
        @Nonnull
        private final boolean is64bit;

        public DownloadLinkSegmentComputer(@Nullable OsDetails os, boolean isV3OrLater, @Nonnull String context) {
            this.os = os;
            this.isV3OrLater = isV3OrLater;
            this.context = context;
            if (os == null) {
                // guess centos as RPM is sensible default
                log.warn("No details known for OS of " + context + "; assuming 64-bit RPM distribution of Couchbase");
                osName = "centos";
                isRpm = true;
                is64bit = true;
                return;
            }
            osName = os.getName().toLowerCase();
            isRpm = !(osName.contains("deb") || osName.contains("ubuntu"));
            is64bit = os.is64bit();
        }

        /**
         * separator after the version number used to be _ but is - in 3.0 and later
         */
        public String getPreVersionSeparator() {
            if (!isV3OrLater) return "_";
            if (isRpm) return "-";
            return "_";
        }

        public String getOsTag() {
            // couchbase only provide certain versions; if on other platforms let's suck-it-and-see
            String family;
            if (osName.contains("debian")) family = "debian7_";
            else if (osName.contains("ubuntu")) family = "ubuntu12.04_";
            else if (osName.contains("centos") || osName.contains("rhel") || (osName.contains("red") && osName.contains("hat")))
                family = "centos6.";
            else {
                log.warn("Unrecognised OS " + os + " of " + context + "; assuming RPM distribution of Couchbase");
                family = "centos6.";
            }

            if (!is64bit && !isV3OrLater) {
                // NB: 32-bit binaries aren't (yet?) available for v30
                log.warn("32-bit binaries for Couchbase might not be available, when deploying " + context);
            }
            String arch = !is64bit ? "x86" : !isRpm && isV3OrLater ? "amd64" : "x86_64";
            String fileExtension = isRpm ? ".rpm" : ".deb";

            if (isV3OrLater)
                return family + arch + fileExtension;
            else
                return arch + fileExtension;
        }

        public String getOsTagWithPrefix() {
            return (!isV3OrLater ? "_" : "-") + getOsTag();
        }
    }

    @Override
    public String getDownloadLinkOsTagWithPrefix() {
        return newDownloadLinkSegmentComputer().getOsTagWithPrefix();
    }

    @Override
    public String getDownloadLinkPreVersionSeparator() {
        return newDownloadLinkSegmentComputer().getPreVersionSeparator();
    }

    private boolean isPreV3() {
        return NaturalOrderComparator.INSTANCE.compare(getEntity().getConfig(CouchbaseNode.SUGGESTED_VERSION), "3.0") < 0;
    }

    @Override
    public String getCommunityOrEnterprise() {
        Boolean isEnterprise = getEntity().getConfig(CouchbaseNode.USE_ENTERPRISE);
        return isEnterprise ? "enterprise" : "community";
    }

    private String getUsername() {
        return entity.getConfig(CouchbaseNode.COUCHBASE_ADMIN_USERNAME);
    }

    private String getPassword() {
        return entity.getConfig(CouchbaseNode.COUCHBASE_ADMIN_PASSWORD);
    }

    private String getWebPort() {
        return "" + entity.getAttribute(CouchbaseNode.COUCHBASE_WEB_ADMIN_PORT);
    }

    private String getCouchbaseHostnameAndCredentials() {
        return format("-c %s:%s -u %s -p %s", getSubnetHostname(), getWebPort(), getUsername(), getPassword());
    }

    private String getCouchbaseHostnameAndPort() {
        return format("-c %s:%s", getSubnetHostname(), getWebPort());
    }

    private String getClusterInitRamSize() {
        return entity.getConfig(CouchbaseNode.COUCHBASE_CLUSTER_INIT_RAM_SIZE).toString();
    }

    @Override
    public void rebalance() {
        entity.setAttribute(CouchbaseNode.REBALANCE_STATUS, "explicitly started");
        newScript("rebalance")
                .body.append(
                couchbaseCli("rebalance") + getCouchbaseHostnameAndCredentials())
                .failOnNonZeroResultCode()
                .execute();

        // wait until the re-balance is started
        // (if it's quick, this might miss it, but it will only block for 30s if so)
        Repeater.create()
                .backoff(Repeater.DEFAULT_REAL_QUICK_PERIOD, 2, Duration.millis(500))
                .limitTimeTo(Duration.THIRTY_SECONDS)
                .until(new Callable<Boolean>() {
                           @Override
                           public Boolean call() throws Exception {
                               for (HostAndPort nodeHostAndPort : getNodesHostAndPort()) {
                                   if (isNodeRebalancing(nodeHostAndPort.toString())) {
                                       return true;
                                   }
                               }
                               return false;
                           }
                       }
                ).run();

        entity.setAttribute(CouchbaseNode.REBALANCE_STATUS, "waiting for completion");
        // Wait until the Couchbase node finishes the re-balancing
        Task<Boolean> reBalance = TaskBuilder.<Boolean>builder()
                .name("Waiting until node is rebalancing")
                .body(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return Repeater.create()
                                .backoff(Duration.ONE_SECOND, 1.2, Duration.TEN_SECONDS)
                                .limitTimeTo(Duration.FIVE_MINUTES)
                                .until(new Callable<Boolean>() {
                                    @Override
                                    public Boolean call() throws Exception {
                                        for (HostAndPort nodeHostAndPort : getNodesHostAndPort()) {
                                            if (isNodeRebalancing(nodeHostAndPort.toString())) {
                                                return false;
                                            }
                                        }
                                        return true;
                                    }
                                })
                                .run();
                        }
                })
                .build();
        Boolean completed = DynamicTasks.queueIfPossible(reBalance)
                .orSubmitAndBlock()
                .andWaitForSuccess();
        if (completed) {
            entity.setAttribute(CouchbaseNode.REBALANCE_STATUS, "completed");
            ServiceStateLogic.ServiceNotUpLogic.clearNotUpIndicator(getEntity(), "rebalancing");
            log.info("Rebalanced cluster via primary node {}", getEntity());
        } else {
            entity.setAttribute(CouchbaseNode.REBALANCE_STATUS, "timed out");
            ServiceStateLogic.ServiceNotUpLogic.updateNotUpIndicator(getEntity(), "rebalancing", "rebalance did not complete within time limit");
            log.warn("Timeout rebalancing cluster via primary node {}", getEntity());
        }
    }

    private Iterable<HostAndPort> getNodesHostAndPort() {
        Group group = Iterables.getFirst(getEntity().getGroups(), null);
        if (group == null) return Lists.newArrayList();
        return Iterables.transform(group.getAttribute(CouchbaseCluster.COUCHBASE_CLUSTER_UP_NODES),
                new Function<Entity, HostAndPort>() {
                    @Override
                    public HostAndPort apply(Entity input) {
                        return BrooklynAccessUtils.getBrooklynAccessibleAddress(input, input.getAttribute(CouchbaseNode.COUCHBASE_WEB_ADMIN_PORT));
                    }
                });
    }

    private boolean isNodeRebalancing(String nodeHostAndPort) {
        HttpToolResponse response = getApiResponse("http://" + nodeHostAndPort + "/pools/default/rebalanceProgress");
        if (response.getResponseCode() != 200) {
            throw new IllegalStateException("failed retrieving rebalance status: " + response);
        }
        return !"none".equals(HttpValueFunctions.jsonContents("status", String.class).apply(response));
    }

    private HttpToolResponse getApiResponse(String uri) {
        return HttpTool.httpGet(HttpTool.httpClientBuilder()
                        // the uri is required by the HttpClientBuilder in order to set the AuthScope of the credentials
                        .uri(uri)
                        .credentials(new UsernamePasswordCredentials(getUsername(), getPassword()))
                        .build(),
                URI.create(uri),
                ImmutableMap.<String, String>of());
    }

    @Override
    public void serverAdd(String serverToAdd, String username, String password) {
        newScript("serverAdd").body.append(couchbaseCli("server-add")
                + getCouchbaseHostnameAndCredentials() +
                " --server-add=" + BashStringEscapes.wrapBash(serverToAdd) +
                " --server-add-username=" + BashStringEscapes.wrapBash(username) +
                " --server-add-password=" + BashStringEscapes.wrapBash(password))
                .failOnNonZeroResultCode()
                .execute();
    }

    @Override
    public void serverAddAndRebalance(String serverToAdd, String username, String password) {
        newScript("serverAddAndRebalance").body.append(couchbaseCli("rebalance")
                + getCouchbaseHostnameAndCredentials() +
                " --server-add=" + BashStringEscapes.wrapBash(serverToAdd) +
                " --server-add-username=" + BashStringEscapes.wrapBash(username) +
                " --server-add-password=" + BashStringEscapes.wrapBash(password))
                .failOnNonZeroResultCode()
                .execute();
        entity.setAttribute(CouchbaseNode.REBALANCE_STATUS, "triggered as part of server-add");
    }

    @Override
    public void bucketCreate(String bucketName, String bucketType, Integer bucketPort, Integer bucketRamSize, Integer bucketReplica) {
        log.info("Adding bucket: {} to cluster {} primary node: {}", new Object[]{bucketName, CouchbaseClusterImpl.getClusterOrNode(getEntity()), getEntity()});

        newScript("bucketCreate").body.append(couchbaseCli("bucket-create")
                + getCouchbaseHostnameAndCredentials() +
                " --bucket=" + BashStringEscapes.wrapBash(bucketName) +
                " --bucket-type=" + BashStringEscapes.wrapBash(bucketType) +
                " --bucket-port=" + bucketPort +
                " --bucket-ramsize=" + bucketRamSize +
                " --bucket-replica=" + bucketReplica)
                .failOnNonZeroResultCode()
                .execute();
    }

    @Override
    public void addReplicationRule(Entity toCluster, String fromBucket, String toBucket) {
        DynamicTasks.queue(DependentConfiguration.attributeWhenReady(toCluster, Attributes.SERVICE_UP)).getUnchecked();

        String destName = CouchbaseClusterImpl.getClusterName(toCluster);

        log.info("Setting up XDCR for " + fromBucket + " from " + CouchbaseClusterImpl.getClusterName(getEntity()) + " (via " + getEntity() + ") "
                + "to " + destName + " (" + toCluster + ")");

        Entity destPrimaryNode = toCluster.getAttribute(CouchbaseCluster.COUCHBASE_PRIMARY_NODE);
        String destHostname = destPrimaryNode.getAttribute(Attributes.HOSTNAME);
        String destUsername = toCluster.getConfig(CouchbaseNode.COUCHBASE_ADMIN_USERNAME);
        String destPassword = toCluster.getConfig(CouchbaseNode.COUCHBASE_ADMIN_PASSWORD);

        // on the REST API there is mention of a 'type' 'continuous' but i don't see other refs to this

        // PROTOCOL   Select REST protocol or memcached for replication. xmem indicates memcached while capi indicates REST protocol.
        // looks like xmem is the default; leave off for now
//        String replMode = "xmem";

        DynamicTasks.queue(TaskTags.markInessential(SshEffectorTasks.ssh(
                couchbaseCli("xdcr-setup") +
                        getCouchbaseHostnameAndCredentials() +
                        " --create" +
                        " --xdcr-cluster-name=" + BashStringEscapes.wrapBash(destName) +
                        " --xdcr-hostname=" + BashStringEscapes.wrapBash(destHostname) +
                        " --xdcr-username=" + BashStringEscapes.wrapBash(destUsername) +
                        " --xdcr-password=" + BashStringEscapes.wrapBash(destPassword)
        ).summary("create xdcr destination " + destName).newTask()));

        // would be nice to auto-create bucket, but we'll need to know the parameters; the port in particular is tedious
//        ((CouchbaseNode)destPrimaryNode).bucketCreate(toBucket, "couchbase", null, 0, 0);

        DynamicTasks.queue(SshEffectorTasks.ssh(
                couchbaseCli("xdcr-replicate") +
                        getCouchbaseHostnameAndCredentials() +
                        " --create" +
                        " --xdcr-cluster-name=" + BashStringEscapes.wrapBash(destName) +
                        " --xdcr-from-bucket=" + BashStringEscapes.wrapBash(fromBucket) +
                        " --xdcr-to-bucket=" + BashStringEscapes.wrapBash(toBucket)
//            + " --xdcr-replication-mode="+replMode
        ).summary("configure replication for " + fromBucket + " to " + destName + ":" + toBucket).newTask());
    }
}

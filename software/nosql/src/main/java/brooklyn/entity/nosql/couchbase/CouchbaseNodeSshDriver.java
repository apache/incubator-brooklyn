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
import static brooklyn.util.ssh.BashCommands.ok;
import static brooklyn.util.ssh.BashCommands.sudo;
import static java.lang.String.format;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.drivers.downloads.BasicDownloadRequirement;
import brooklyn.entity.drivers.downloads.DownloadProducerFromUrlAttribute;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.http.JsonFunctions;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.guava.Functionals;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.TaskTags;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.NaturalOrderComparator;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

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

        log.info("Installing "+getEntity()+" using couchbase-server-{} {}", getCommunityOrEnterprise(), getVersion());

        String apt = chainGroup(
                "export DEBIAN_FRONTEND=noninteractive",
                "which apt-get",
                sudo("apt-get update"),
                // The following line is required to run on Docker container
                sudo("apt-get install -y python-httplib2"),
                sudo("apt-get install -y libssl0.9.8"),
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
                        "curl -f -L -k "+link
                            + " -H 'Referer: http://www.couchbase.com/downloads'"
                            + " -o "+saveAs),
                        "Could not retrieve "+saveAs+" (from "+urls.size()+" sites)", 9)))
                .add(alternatives(apt, yum))
                .build();
    }

    @Override
    public void customize() {
        //TODO: add linux tweaks for couchbase
        //http://blog.couchbase.com/often-overlooked-linux-os-tweaks

        //turn off swappiness
        //sudo echo 0 > /proc/sys/vm/swappiness

        //disable THP
        //sudo echo never > /sys/kernel/mm/transparent_hugepage/enabled
        //sudo echo never > /sys/kernel/mm/transparent_hugepage/defrag
    }

    @Override
    public void launch() {
        String clusterPrefix = "--cluster-"+(isPreV300() ? "init-" : "");
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
                        (isPreV300() ? getCouchbaseHostnameAndPort() : getCouchbaseHostnameAndCredentials()) +
                        " "+clusterPrefix+"username=" + getUsername() +
                        " "+clusterPrefix+"password=" + getPassword() +
                        " "+clusterPrefix+"port=" + getWebPort() +
                        " "+clusterPrefix+"ramsize=" + getClusterInitRamSize())
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
        return getOsTag(getLocation().getOsDetails(), !isPreV300());
    }
    
    public static String getOsTag(OsDetails os, boolean isV30OrLater) {
        if (os == null) {
            // Default to 64-bit centos linux
            if (isV30OrLater)
                return "centos6.x86_64.rpm";
            else
                return "x86_64.rpm";
            
        } else {
            // are there should ways to check for OS name and version
            String osName = os.getName().toLowerCase();
            boolean isDebbish = osName.contains("deb") || osName.contains("ubuntu");
            
            // couchbase only provide certain versions; if on other platforms let's suck-it-and-see
            String family;
            if (osName.contains("debian")) family = "debian7_";
            else if (osName.contains("ubuntu")) family = "ubuntu12.04_";
            else family = "centos6.";

            // 32-bit binaries aren't (yet?) available
            String arch = !os.is64bit() ? "x86" : isDebbish && isV30OrLater ? "amd64" : "x86_64";
            String fileExtension = isDebbish ? ".deb" : ".rpm";
            
            if (isV30OrLater)
                return family + arch + fileExtension;
            else
                return arch + fileExtension;
        }
    }
    /** separator after the version number used to be _ but is - in 3.0 and later */
    public String getOsTagWithPrefix() {
        return (isPreV300() ? "_" : "-") + getOsTag(); 
    }

    private boolean isPreV300() {
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
        return ""+entity.getAttribute(CouchbaseNode.COUCHBASE_WEB_ADMIN_PORT);
    }

    private String getCouchbaseHostnameAndCredentials() {
        return format("-c localhost:%s -u %s -p %s", getWebPort(), getUsername(), getPassword());
    }

    private String getCouchbaseHostnameAndPort() {
        return format("-c localhost:%s", getWebPort());
    }

    private String getClusterInitRamSize() {
        return entity.getConfig(CouchbaseNode.COUCHBASE_CLUSTER_INIT_RAM_SIZE).toString();
    }
        
    @Override
    public void rebalance() {
        entity.setAttribute(CouchbaseNode.REBALANCE_STATUS, "Rebalance Started");
        newScript("rebalance")
                .body.append(
                couchbaseCli("rebalance") +
                        getCouchbaseHostnameAndCredentials())
                .failOnNonZeroResultCode()
                .execute();
        
        // wait until the re-balance is started
        // (if it's quick, this might miss it, but it will only block for 30s if so)
        Repeater.create()
            .backoff(Duration.millis(10), 2, Duration.millis(500))
            .limitTimeTo(Duration.THIRTY_SECONDS)
            .until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    for (String nodeHostAndPort : CouchbaseNodeSshDriver.this.getNodesHostAndPort()) {
                        if (isNodeRebalancing(nodeHostAndPort)) {
                            return true;
                        }
                    }
                    return false;
                }
            })
            .run();
        
        // then wait until the re-balance is complete
        Repeater.create()
            .every(Duration.FIVE_SECONDS)
            .limitTimeTo(Duration.FIVE_MINUTES)
            .until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    for (String nodeHostAndPort : getNodesHostAndPort()) {
                        if (isNodeRebalancing(nodeHostAndPort)) {
                            return false;
                        }
                    }
                    return true;
                }
            })
            .run();
        log.info("Rebalanced cluster via primary node {}", getEntity());
    }

    private Iterable<String> getNodesHostAndPort() throws URISyntaxException {
        Function<JsonElement, Iterable<String>> getNodesAsList = new Function<JsonElement, Iterable<String>>() {
            @Override public Iterable<String> apply(JsonElement input) {
                if (input == null) {
                    return Collections.emptyList();
                }
                Collection<String> names = Lists.newArrayList();
                JsonArray nodes = input.getAsJsonArray();
                for (JsonElement element : nodes) {
                    // NOTE: the 'hostname' element also includes the port
                    names.add(element.getAsJsonObject().get("hostname").toString().replace("\"", ""));
                }
                return names;
            }
        };
        HttpToolResponse nodesResponse = getAPIResponse(String.format("http://%s:%s/pools/nodes", getHostname(), getWebPort()));
        return Functionals.chain(
            HttpValueFunctions.jsonContents(),
            JsonFunctions.walkN("nodes"),
            getNodesAsList
        ).apply(nodesResponse);
    }
    
    private boolean isNodeRebalancing(String nodeHostAndPort) throws URISyntaxException {
        HttpToolResponse response = getAPIResponse("http://" + nodeHostAndPort + "/pools/nodes/rebalanceProgress");
        if (response.getResponseCode() != 200) {
            throw new IllegalStateException("failed to rebalance cluster: " + response);
        }
        return !"none".equals(HttpValueFunctions.jsonContents("status", String.class).apply(response));
    }
    
    private HttpToolResponse getAPIResponse(String uri) throws URISyntaxException {
        URI apiUri = new URI(uri);
        Credentials credentials = new UsernamePasswordCredentials(getUsername(), getPassword());
        return HttpTool.httpGet(HttpTool.httpClientBuilder()
                // the uri is required by the HttpClientBuilder in order to set the AuthScope of the credentials
                .uri(apiUri)
                .credentials(credentials)
                .build(), 
            apiUri, 
            ImmutableMap.<String, String>of());
    }
    
    @Override
    public void serverAdd(String serverToAdd, String username, String password) {
        newScript("serverAdd").body.append(couchbaseCli("server-add")
                + getCouchbaseHostnameAndCredentials() +
                " --server-add=" + serverToAdd +
                " --server-add-username=" + username +
                " --server-add-password=" + password)
                .failOnNonZeroResultCode()
                .execute();
    }

    @Override
    public void serverAddAndRebalance(String serverToAdd, String username, String password) {
        newScript("serverAddAndRebalance").body.append(couchbaseCli("rebalance")
                + getCouchbaseHostnameAndCredentials() +
                " --server-add=" + serverToAdd +
                " --server-add-username=" + username +
                " --server-add-password=" + password)
                .failOnNonZeroResultCode()
                .execute();
        entity.setAttribute(CouchbaseNode.REBALANCE_STATUS, "Rebalance Started");
    }

    @Override
    public void bucketCreate(String bucketName, String bucketType, Integer bucketPort, Integer bucketRamSize, Integer bucketReplica) {
        log.info("Adding bucket: {} to cluster {} primary node: {}", new Object[] { bucketName, CouchbaseClusterImpl.getCluster(getEntity()), getEntity() });
        
        newScript("bucketCreate").body.append(couchbaseCli("bucket-create")
            + getCouchbaseHostnameAndCredentials() +
            " --bucket=" + bucketName +
            " --bucket-type=" + bucketType +
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
        
        log.info("Setting up XDCR for "+fromBucket+" from "+CouchbaseClusterImpl.getClusterName(getEntity())+" (via "+getEntity()+") "
            + "to "+destName+" ("+toCluster+")");
        
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
            " --xdcr-cluster-name="+destName +
            " --xdcr-hostname="+destHostname +
            " --xdcr-username="+destUsername +
            " --xdcr-password="+destPassword
            ).summary("create xdcr destination "+destName).newTask()));

        // would be nice to auto-create bucket, but we'll need to know the parameters; the port in particular is tedious
//        ((CouchbaseNode)destPrimaryNode).bucketCreate(toBucket, "couchbase", null, 0, 0);
        
        DynamicTasks.queue(SshEffectorTasks.ssh(
            couchbaseCli("xdcr-replicate") +
            getCouchbaseHostnameAndCredentials() +
            " --create" +
            " --xdcr-cluster-name="+destName +
            " --xdcr-from-bucket="+fromBucket +
            " --xdcr-to-bucket="+toBucket
//            + " --xdcr-replication-mode="+replMode
            ).summary("configure replication for "+fromBucket+" to "+destName+":"+toBucket).newTask());
    }
}

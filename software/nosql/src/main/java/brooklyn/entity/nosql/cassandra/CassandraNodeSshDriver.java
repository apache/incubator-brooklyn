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
package brooklyn.entity.nosql.cassandra;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.database.DatastoreMixins;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.Location;
import brooklyn.location.access.BrooklynAccessUtils;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.TaskWrapper;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;
import brooklyn.util.text.TemplateProcessor;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Start a {@link CassandraNode} in a {@link Location} accessible over ssh.
 */
public class CassandraNodeSshDriver extends JavaSoftwareProcessSshDriver implements CassandraNodeDriver {

    private static final Logger log = LoggerFactory.getLogger(CassandraNodeSshDriver.class);

    protected Maybe<String> resolvedAddressCache = Maybe.absent();

    public CassandraNodeSshDriver(CassandraNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected String getLogFileLocation() { return Os.mergePathsUnix(getRunDir(),"cassandra.log"); }

    @Override
    public Integer getGossipPort() { return entity.getAttribute(CassandraNode.GOSSIP_PORT); }

    @Override
    public Integer getSslGossipPort() { return entity.getAttribute(CassandraNode.SSL_GOSSIP_PORT); }

    @Override
    public Integer getThriftPort() { return entity.getAttribute(CassandraNode.THRIFT_PORT); }

    @Override
    public Integer getNativeTransportPort() { return entity.getAttribute(CassandraNode.NATIVE_TRANSPORT_PORT); }

    @Override
    public String getClusterName() { return entity.getAttribute(CassandraNode.CLUSTER_NAME); }

    @Override
    public String getCassandraConfigTemplateUrl() {
        String templatedUrl = entity.getConfig(CassandraNode.CASSANDRA_CONFIG_TEMPLATE_URL);
        return TemplateProcessor.processTemplateContents(templatedUrl, this, ImmutableMap.<String, Object>of());
    }

    @Override
    public String getCassandraConfigFileName() { return entity.getConfig(CassandraNode.CASSANDRA_CONFIG_FILE_NAME); }

    public String getEndpointSnitchName() { return entity.getConfig(CassandraNode.ENDPOINT_SNITCH_NAME); }

    public String getCassandraRackdcConfigTemplateUrl() { return entity.getConfig(CassandraNode.CASSANDRA_RACKDC_CONFIG_TEMPLATE_URL); }

    public String getCassandraRackdcConfigFileName() { return entity.getConfig(CassandraNode.CASSANDRA_RACKDC_CONFIG_FILE_NAME); }

    public String getMirrorUrl() { return entity.getConfig(CassandraNode.MIRROR_URL); }
    
    protected String getDefaultUnpackedDirectoryName() {
        return "apache-cassandra-"+getVersion();
    }
    
    @Override
    public boolean installJava() {
        String version = getVersion();
        if (version.startsWith("2.")) {
            return checkForAndInstallJava7or8();
        } else {
            return super.installJava();
        }
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(getDefaultUnpackedDirectoryName())));
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
                .body.append(commands)
                .execute();
    }

    @Override
    public Set<Integer> getPortsUsed() {
        return ImmutableSet.<Integer>builder()
                .addAll(super.getPortsUsed())
                .addAll(getPortMap().values())
                .build();
    }

    protected Map<String, Integer> getPortMap() {
        return ImmutableMap.<String, Integer>builder()
                .put("jmxPort", entity.getAttribute(UsesJmx.JMX_PORT))
                .put("rmiPort", entity.getAttribute(UsesJmx.RMI_REGISTRY_PORT))
                .put("gossipPort", getGossipPort())
                .put("sslGossipPort", getSslGossipPort())
                .put("thriftPort", getThriftPort())
                .build();
    }

    @Override
    public void customize() {
        log.debug("Customizing {} (Cluster {})", entity, getClusterName());
        Networking.checkPortsValid(getPortMap());

        customizeInitialSeeds();

        String logFileEscaped = getLogFileLocation().replace("/", "\\/"); // escape slashes

        ImmutableList.Builder<String> commands = new ImmutableList.Builder<String>()
                .add(String.format("cp -R %s/{bin,conf,lib,interface,pylib,tools} .", getExpandedInstallDir()))
                .add("mkdir -p data")
                .add("mkdir -p brooklyn_commands")
                .add(String.format("sed -i.bk 's/log4j.appender.R.File=.*/log4j.appender.R.File=%s/g' %s/conf/log4j-server.properties", logFileEscaped, getRunDir()))
                .add(String.format("sed -i.bk '/JMX_PORT/d' %s/conf/cassandra-env.sh", getRunDir()))
                // Script sets 180k on Linux which gives Java error:  The stack size specified is too small, Specify at least 228k 
                .add(String.format("sed -i.bk 's/-Xss180k/-Xss280k/g' %s/conf/cassandra-env.sh", getRunDir())); 

        newScript(CUSTOMIZING)
                .body.append(commands.build())
                .failOnNonZeroResultCode()
                .execute();

        // Copy the cassandra.yaml configuration file across
        String destinationConfigFile = Os.mergePathsUnix(getRunDir(), "conf", getCassandraConfigFileName());
        copyTemplate(getCassandraConfigTemplateUrl(), destinationConfigFile);

        // Copy the cassandra-rackdc.properties configuration file across
        String rackdcDestinationFile = Os.mergePathsUnix(getRunDir(), "conf", getCassandraRackdcConfigFileName());
        copyTemplate(getCassandraRackdcConfigTemplateUrl(), rackdcDestinationFile);

        customizeCopySnitch();
    }

    protected void customizeCopySnitch() {
        // Copy the custom snitch jar file across
        String customSnitchJarUrl = entity.getConfig(CassandraNode.CUSTOM_SNITCH_JAR_URL);
        if (Strings.isNonBlank(customSnitchJarUrl)) {
            int lastSlashIndex = customSnitchJarUrl.lastIndexOf("/");
            String customSnitchJarName = (lastSlashIndex > 0) ? customSnitchJarUrl.substring(lastSlashIndex+1) : "customBrooklynSnitch.jar";
            String jarDestinationFile = Os.mergePathsUnix(getRunDir(), "lib", customSnitchJarName);
            InputStream customSnitchJarStream = checkNotNull(resource.getResourceFromUrl(customSnitchJarUrl), "%s could not be loaded", customSnitchJarUrl);
            try {
                getMachine().copyTo(customSnitchJarStream, jarDestinationFile);
            } finally {
                Streams.closeQuietly(customSnitchJarStream);
            }
        }
    }

    protected void customizeInitialSeeds() {
        if (entity.getConfig(CassandraNode.INITIAL_SEEDS)==null) {
            if (isClustered()) {
                entity.setConfig(CassandraNode.INITIAL_SEEDS, 
                    DependentConfiguration.attributeWhenReady(entity.getParent(), CassandraDatacenter.CURRENT_SEEDS));
            } else {
                entity.setConfig(CassandraNode.INITIAL_SEEDS, MutableSet.<Entity>of(entity));
            }
        }
    }

    @Override
    public boolean isClustered() {
        return entity.getParent() instanceof CassandraDatacenter;
    }

    @Override
    public void launch() {
        String subnetHostname = Machines.findSubnetOrPublicHostname(entity).get();
        Set<Entity> seeds = getEntity().getConfig(CassandraNode.INITIAL_SEEDS);
        List<Entity> ancestors = getCassandraAncestors();
        log.info("Launching " + entity + ": " +
                "cluster "+getClusterName()+", " +
                "hostname (public) " + getEntity().getAttribute(Attributes.HOSTNAME) + ", " +
                "hostname (subnet) " + subnetHostname + ", " +
                "seeds "+((CassandraNode)entity).getSeeds()+" (from "+seeds+")");

        boolean isFirst = seeds.iterator().next().equals(entity);
        if (isClustered() && !isFirst && CassandraDatacenter.WAIT_FOR_FIRST) {
            // wait for the first node
            long firstStartTime = Entities.submit(entity, DependentConfiguration.attributeWhenReady(
                ancestors.get(ancestors.size()-1), CassandraDatacenter.FIRST_NODE_STARTED_TIME_UTC)).getUnchecked();
            // optionally force a delay before starting subsequent nodes; see comment at CassandraCluster.DELAY_AFTER_FIRST
            Duration toWait = Duration.millis(firstStartTime + CassandraDatacenter.DELAY_AFTER_FIRST.toMilliseconds() -  System.currentTimeMillis());
            if (toWait.toMilliseconds()>0) {
                log.info("Launching " + entity + ": delaying launch of non-first node by "+toWait+" to prevent schema disagreements");
                Tasks.setBlockingDetails("Pausing to ensure first node has time to start");
                Time.sleep(toWait);
                Tasks.resetBlockingDetails();
            }
        }

        List<Entity> queuedStart = null;
        if (CassandraDatacenter.DELAY_BETWEEN_STARTS!=null && !ancestors.isEmpty()) {
            Entity root = ancestors.get(ancestors.size()-1);
            // TODO currently use the class as a semaphore; messy, and obviously will not federate;
            // should develop a brooklyn framework semaphore (similar to that done on SshMachineLocation)
            // and use it - note however the synch block is very very short so relatively safe at least
            synchronized (CassandraNode.class) {
                queuedStart = root.getAttribute(CassandraDatacenter.QUEUED_START_NODES);
                if (queuedStart==null) {
                    queuedStart = new ArrayList<Entity>();
                    ((EntityLocal)root).setAttribute(CassandraDatacenter.QUEUED_START_NODES, queuedStart);
                }
                queuedStart.add(getEntity());
                ((EntityLocal)root).setAttribute(CassandraDatacenter.QUEUED_START_NODES, queuedStart);
            }
            do {
                // get it again in case it is backed by something external
                queuedStart = root.getAttribute(CassandraDatacenter.QUEUED_START_NODES);
                if (queuedStart.get(0).equals(getEntity())) break;
                synchronized (queuedStart) {
                    try {
                        queuedStart.wait(1000);
                    } catch (InterruptedException e) {
                        Exceptions.propagate(e);
                    }
                }
            } while (true);
            
            // TODO should look at last start time... but instead we always wait
            CassandraDatacenter.DELAY_BETWEEN_STARTS.countdownTimer().waitForExpiryUnchecked();
        }

        try {
            newScript(MutableMap.of(USE_PID_FILE, getPidFile()), LAUNCHING)
                    .body.append(
                            // log the date to attempt to debug occasional http://wiki.apache.org/cassandra/FAQ#schema_disagreement
                            // (can be caused by machines out of synch time-wise; but in our case it seems to be caused by other things!)
                            "echo date on cassandra server `hostname` when launching is `date`",
                            launchEssentialCommand())
                    .execute();
            if (!isClustered()) {
                InputStream creationScript = DatastoreMixins.getDatabaseCreationScript(entity);
                if (creationScript!=null) { 
                    Tasks.setBlockingDetails("Pausing to ensure Cassandra (singleton) has started before running creation script");
                    Time.sleep(Duration.seconds(20));
                    Tasks.resetBlockingDetails();
                    executeScriptAsync(Streams.readFullyString(creationScript));
                }
            }
            if (isClustered() && isFirst) {
                for (Entity ancestor: getCassandraAncestors()) {
                    ((EntityLocal)ancestor).setAttribute(CassandraDatacenter.FIRST_NODE_STARTED_TIME_UTC, System.currentTimeMillis());
                }
            }
        } finally {
            if (queuedStart!=null) {
                Entity head = queuedStart.remove(0);
                checkArgument(head.equals(getEntity()), "first queued node was "+head+" but we are "+getEntity());
                synchronized (queuedStart) {
                    queuedStart.notifyAll();
                }
            }
        }
    }

    /** returns cassandra-related ancestors (datacenter, fabric), with datacenter first and fabric last */
    protected List<Entity> getCassandraAncestors() {
        List<Entity> result = new ArrayList<Entity>();
        Entity ancestor = getEntity().getParent();
        while (ancestor!=null) {
            if (ancestor instanceof CassandraDatacenter || ancestor instanceof CassandraFabric)
                result.add(ancestor);
            ancestor = ancestor.getParent();
        }
        return result;
    }
    
    protected String launchEssentialCommand() {
        return String.format("nohup ./bin/cassandra -p %s > ./cassandra-console.log 2>&1 &", getPidFile());
    }

    public String getPidFile() { return Os.mergePathsUnix(getRunDir(), "cassandra.pid"); }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of(USE_PID_FILE, getPidFile()), CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, getPidFile()), STOPPING).execute();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Map<String,String> getCustomJavaSystemProperties() {
        return MutableMap.<String, String>builder()
                .putAll(super.getCustomJavaSystemProperties())
                .put("cassandra.config", getCassandraConfigFileName())
                .build();
    }
    
    @Override
    public Map<String, String> getShellEnvironment() {
        return MutableMap.<String, String>builder()
                .putAll(super.getShellEnvironment())
                .put("CASSANDRA_HOME", getRunDir())
                .put("CASSANDRA_CONF", Os.mergePathsUnix(getRunDir(), "conf"))
                .renameKey("JAVA_OPTS", "JVM_OPTS")
                .build();
    }

    @Override
    public ProcessTaskWrapper<Integer> executeScriptAsync(String commands) {
        String fileToRun = Os.mergePathsUnix("brooklyn_commands", "cassandra-commands-"+Identifiers.makeRandomId(8));
        TaskWrapper<Void> task = SshEffectorTasks.put(Os.mergePathsUnix(getRunDir(), fileToRun))
                .machine(getMachine())
                .contents(commands)
                .summary("copying cassandra script to execute "+fileToRun)
                .newTask();
        DynamicTasks.queueIfPossible(task).orSubmitAndBlock(getEntity()).andWaitForSuccess();
        return executeScriptFromInstalledFileAsync(fileToRun);
    }

    public ProcessTaskWrapper<Integer> executeScriptFromInstalledFileAsync(String fileToRun) {
        ProcessTaskWrapper<Integer> task = SshEffectorTasks.ssh(
                        "cd "+getRunDir(),
                        scriptInvocationCommand(getThriftPort(), fileToRun))
                .machine(getMachine())
                .summary("executing cassandra script "+fileToRun)
                .newTask();
        DynamicTasks.queueIfPossible(task).orSubmitAndBlock(getEntity());
        return task;
    }

    protected String scriptInvocationCommand(Integer optionalThriftPort, String fileToRun) {
        return "bin/cassandra-cli " +
                (optionalThriftPort != null ? "--port " + optionalThriftPort : "") +
                " --file "+fileToRun;
    }

    @Override
    public String getResolvedAddress(String hostname) {
        return resolvedAddressCache.or(BrooklynAccessUtils.resolvedAddressSupplier(getEntity(), getMachine(), hostname));
    }
    
}

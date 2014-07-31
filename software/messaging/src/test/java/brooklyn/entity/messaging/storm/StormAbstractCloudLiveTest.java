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
package brooklyn.entity.messaging.storm;

import static brooklyn.entity.messaging.storm.Storm.NIMBUS_HOSTNAME;
import static brooklyn.entity.messaging.storm.Storm.ZOOKEEPER_ENSEMBLE;
import static brooklyn.entity.messaging.storm.Storm.Role.NIMBUS;
import static brooklyn.entity.messaging.storm.Storm.Role.SUPERVISOR;
import static brooklyn.entity.messaging.storm.Storm.Role.UI;
import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;

import java.io.File;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import backtype.storm.Config;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.AlreadyAliveException;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.generated.StormTopology;
import backtype.storm.testing.TestWordSpout;
import backtype.storm.topology.TopologyBuilder;
import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.messaging.storm.topologies.ExclamationBolt;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.zookeeper.ZooKeeperEnsemble;
import brooklyn.location.Location;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.file.ArchiveBuilder;
import brooklyn.util.os.Os;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

public abstract class StormAbstractCloudLiveTest extends BrooklynAppLiveTestSupport {

    protected static final Logger log = LoggerFactory
            .getLogger(StormAbstractCloudLiveTest.class);
    private Location location;
    private ZooKeeperEnsemble zooKeeperEnsemble;
    private Storm nimbus;
    private Storm supervisor;
    private Storm ui;

    @BeforeClass(alwaysRun = true)
    public void beforeClass() throws Exception {
        mgmt = new LocalManagementContext();
        location = mgmt.getLocationRegistry()
                .resolve(getLocation(), getFlags());
        super.setUp();
    }

    @AfterClass(alwaysRun = true)
    public void afterClass() throws Exception {
        // Entities.destroyAll(mgmt);
    }

    public abstract String getLocation();

    public Map<String, ?> getFlags() {
        return MutableMap.of();
    }

    @Test(groups = {"Live","WIP"})  // needs repair to avoid hard dependency on Andrea's environment
    public void deployStorm() throws Exception {
        try {
            zooKeeperEnsemble = app.createAndManageChild(EntitySpec.create(
                    ZooKeeperEnsemble.class).configure(
                    ZooKeeperEnsemble.INITIAL_SIZE, 3));
            nimbus = app.createAndManageChild(EntitySpec
                    .create(Storm.class)
                    .configure(Storm.ROLE, NIMBUS)
                    .configure(NIMBUS_HOSTNAME, "localhost")
                    .configure(ZOOKEEPER_ENSEMBLE, zooKeeperEnsemble)
                    );
            supervisor = app.createAndManageChild(EntitySpec
                    .create(Storm.class)
                    .configure(Storm.ROLE, SUPERVISOR)
                    .configure(ZOOKEEPER_ENSEMBLE, zooKeeperEnsemble)
                    .configure(NIMBUS_HOSTNAME,
                            attributeWhenReady(nimbus, Attributes.HOSTNAME)));
            ui = app.createAndManageChild(EntitySpec
                    .create(Storm.class)
                    .configure(Storm.ROLE, UI)
                    .configure(ZOOKEEPER_ENSEMBLE, zooKeeperEnsemble)
                    .configure(NIMBUS_HOSTNAME,
                            attributeWhenReady(nimbus, Attributes.HOSTNAME)));
            log.info("Started Storm deployment on '" + getLocation() + "'");
            app.start(ImmutableList.of(location));
            Entities.dumpInfo(app);
            EntityTestUtils.assertAttributeEqualsEventually(app, Startable.SERVICE_UP, true);
            EntityTestUtils.assertAttributeEqualsEventually(zooKeeperEnsemble, Startable.SERVICE_UP, true);
            EntityTestUtils.assertAttributeEqualsEventually(nimbus, Startable.SERVICE_UP, true);
            EntityTestUtils.assertAttributeEqualsEventually(supervisor, Startable.SERVICE_UP, true);
            EntityTestUtils.assertAttributeEqualsEventually(ui, Startable.SERVICE_UP, true);
            
            StormTopology stormTopology = createTopology();
            submitTopology(stormTopology, "myExclamation", 3, true, 60000);
        } catch (Exception e) {
            log.error("Failed to deploy Storm", e);
            Assert.fail();
            throw e;
        }
    }

    private StormTopology createTopology()
            throws AlreadyAliveException, InvalidTopologyException {
        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout("word", new TestWordSpout(), 10);
        builder.setBolt("exclaim1", new ExclamationBolt(), 3).shuffleGrouping("word");
        builder.setBolt("exclaim2", new ExclamationBolt(), 2).shuffleGrouping("exclaim1");
        
        return builder.createTopology();
    }

    public boolean submitTopology(StormTopology stormTopology, String topologyName, int numOfWorkers, boolean debug, long timeoutMs) {
        if (log.isDebugEnabled()) log.debug("Connecting to NimbusClient: {}", nimbus.getConfig(Storm.NIMBUS_HOSTNAME));
        Config conf = new Config();
        conf.setDebug(debug);
        conf.setNumWorkers(numOfWorkers);

        // TODO - confirm this creats the JAR correctly
        String jar = createJar(
            new File(Os.mergePaths(ResourceUtils.create(this).getClassLoaderDir(), "brooklyn/entity/messaging/storm/topologies")),
            "brooklyn/entity/messaging/storm/");
        System.setProperty("storm.jar", jar);
        long startMs = System.currentTimeMillis();
        long endMs = (timeoutMs == -1) ? Long.MAX_VALUE : (startMs + timeoutMs);
        long currentTime = startMs;
        Throwable lastError = null;
        int attempt = 0;
        while (currentTime <= endMs) {
            currentTime = System.currentTimeMillis();
            if (attempt != 0) Time.sleep(Duration.ONE_SECOND);
            if (log.isTraceEnabled()) log.trace("trying connection to {} at time {}", nimbus.getConfig(Storm.NIMBUS_HOSTNAME), currentTime);

            try {
                StormSubmitter.submitTopology(topologyName, conf, stormTopology);
                return true;
            } catch (Exception e) {
                if (shouldRetryOn(e)) {
                    if (log.isDebugEnabled()) log.debug("Attempt {} failed connecting to {} ({})", new Object[] {attempt + 1, nimbus.getConfig(Storm.NIMBUS_HOSTNAME), e.getMessage()});
                    lastError = e;
                } else {
                    throw Throwables.propagate(e);
                }
            }
            attempt++;
        }
        log.warn("unable to connect to Nimbus client: ", lastError);
        Assert.fail();
        return false;
    }
    
    private boolean shouldRetryOn(Exception e) {
        if (e.getMessage().equals("org.apache.thrift7.transport.TTransportException: java.net.ConnectException: Connection refused"))  return true;
        return false;
    }
    
    private String createJar(File dir, String parentDirInJar) {
        if (dir.isDirectory()) {
            File jarFile = ArchiveBuilder.jar().addAt(dir, parentDirInJar).create(Os.newTempDir(getClass())+"/topologies.jar");
            return jarFile.getAbsolutePath();
        } else {
            return dir.getAbsolutePath(); // An existing Jar archive?
        }
    }

}

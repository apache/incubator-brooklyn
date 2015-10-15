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
package org.apache.brooklyn.entity.database.mysql;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;

import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.effector.EffectorTasks;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.entity.database.mysql.MySqlCluster.MySqlMaster;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.ssh.SshTasks;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.testng.annotations.Test;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class MySqlClusterIntegrationTest extends BrooklynAppLiveTestSupport {

    private static final String TEST_LOCATION = "localhost";

    @Test(groups="Integration")
    public void testAllNodesInit() throws Exception {
        try {
            MySqlClusterTestHelper.test(app, getLocation());
        } finally {
            cleanData();
        }
    }

    @Test(groups = {"Integration"})
    public void testMasterInit() throws Exception {
        try {
            MySqlClusterTestHelper.testMasterInit(app, getLocation());
        } finally {
            cleanData();
        }
    }

    @Test(groups="Integration")
    public void testDumpReplication() throws Exception {
        try {
            Location loc = getLocation();
            EntitySpec<MySqlCluster> clusterSpec = EntitySpec.create(MySqlCluster.class)
                    .configure(MySqlMaster.MASTER_CREATION_SCRIPT_CONTENTS, MySqlClusterTestHelper.CREATION_SCRIPT)
                    .configure(MySqlNode.MYSQL_SERVER_CONF, MutableMap.<String, Object>of("skip-name-resolve",""));
            MySqlCluster cluster = MySqlClusterTestHelper.initCluster(app, loc, clusterSpec);
            MySqlNode master = (MySqlNode) cluster.getAttribute(MySqlCluster.FIRST);
            purgeLogs(cluster, master);

            // test dump replication from master
            MySqlNode slave = (MySqlNode) Iterables.getOnlyElement(cluster.invoke(MySqlCluster.RESIZE_BY_DELTA, ImmutableMap.of("delta", 1)).getUnchecked());
            assertEquals(cluster.getAttribute(MySqlCluster.REPLICATION_LAST_SLAVE_SNAPSHOT).getEntityId(), master.getId());
            MySqlClusterTestHelper.assertReplication(master, slave);

            // test dump replication from slave, missing dump on node
            deleteSnapshot(cluster);
            cluster.config().set(MySqlCluster.REPLICATION_PREFERRED_SOURCE, slave.getId());
            MySqlNode secondSlave = (MySqlNode) Iterables.getOnlyElement(cluster.invoke(MySqlCluster.RESIZE_BY_DELTA, ImmutableMap.of("delta", 1)).getUnchecked());
            assertEquals(cluster.getAttribute(MySqlCluster.REPLICATION_LAST_SLAVE_SNAPSHOT).getEntityId(), slave.getId());
            MySqlClusterTestHelper.assertReplication(master, secondSlave);

            // test dump replication from slave, missing snapshot entity
            Entities.destroy(slave);
            cluster.config().set(MySqlCluster.REPLICATION_PREFERRED_SOURCE, secondSlave.getId());
            MySqlNode thirdSlave = (MySqlNode) Iterables.getOnlyElement(cluster.invoke(MySqlCluster.RESIZE_BY_DELTA, ImmutableMap.of("delta", 1)).getUnchecked());
            assertEquals(cluster.getAttribute(MySqlCluster.REPLICATION_LAST_SLAVE_SNAPSHOT).getEntityId(), secondSlave.getId());
            MySqlClusterTestHelper.assertReplication(master, thirdSlave);
        } finally {
            cleanData();
        }
    }

    private void deleteSnapshot(MySqlCluster cluster) {
        ReplicationSnapshot replicationSnapshot = cluster.getAttribute(MySqlCluster.REPLICATION_LAST_SLAVE_SNAPSHOT);
        Entity snapshotEntity = mgmt.getEntityManager().getEntity(replicationSnapshot.getEntityId());
        SshMachineLocation machine = EffectorTasks.getSshMachine(snapshotEntity);
        Entities.submit(snapshotEntity, SshEffectorTasks.ssh(
                "cd $RUN_DIR",
                "rm " + replicationSnapshot.getSnapshotPath())
            .summary("clear snapshot")
            .machine(machine)
            .environmentVariable("RUN_DIR", snapshotEntity.getAttribute(MySqlNode.RUN_DIR))
            .requiringExitCodeZero())
        .asTask()
        .getUnchecked();
    }

    private void purgeLogs(MySqlCluster cluster, MySqlNode master) {
        String preFlushBinaryLogFile = getBinaryLogFile(master);
        ReplicationSnapshot replicationSnapshot = master.getParent().getAttribute(MySqlCluster.REPLICATION_LAST_SLAVE_SNAPSHOT);
        assertEquals(preFlushBinaryLogFile, replicationSnapshot.getBinLogName());
        MySqlClusterTestHelper.execSql(master, "FLUSH LOGS");
        String postFlushBinaryLogFile = getBinaryLogFile(master);
        waitSlavesCatchUp(cluster, postFlushBinaryLogFile);
        assertNotEquals(postFlushBinaryLogFile, preFlushBinaryLogFile);
        MySqlClusterTestHelper.execSql(master, "PURGE BINARY LOGS TO '" + postFlushBinaryLogFile + "';");
        assertFalse(fileExists(master, preFlushBinaryLogFile));
    }

    private void waitSlavesCatchUp(final MySqlCluster cluster, final String binLog) {
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                MySqlNode master = (MySqlNode) cluster.getAttribute(MySqlCluster.FIRST);
                for (Entity node : cluster.getMembers()) {
                    if (node == master) continue;
                    String status = MySqlClusterTestHelper.execSql((MySqlNode) node, "SHOW SLAVE STATUS \\G");
                    Map<String, String> map = MySqlRowParser.parseSingle(status);
                    assertEquals(map.get("Relay_Master_Log_File"), binLog);
                }
            }
        });
    }
    private String getBinaryLogFile(MySqlNode master) {
        String status = MySqlClusterTestHelper.execSql(master, "SHOW MASTER STATUS \\G");
        Map<String, String> map = MySqlRowParser.parseSingle(status);
        return map.get("File");
    }
    private boolean fileExists(MySqlNode node, String binLogName) {
        String dataDir = Strings.nullToEmpty(node.getConfig(MySqlNode.DATA_DIR));
        String path = Os.mergePathsUnix(dataDir, binLogName);
        String cmd = BashCommands.chain(
                "cd $RUN_DIR",
                BashCommands.requireTest(String.format("-f \"%s\"", path), "File " + path + " doesn't exist."));
        String summary = "Check if file " + path + " exists";
        SshMachineLocation machine = EffectorTasks.getSshMachine(node);
        return Entities.submit(node, SshTasks.newSshExecTaskFactory(machine, cmd)
                .allowingNonZeroExitCode()
                .environmentVariable("RUN_DIR", node.getAttribute(SoftwareProcess.RUN_DIR))
                .summary(summary)
                .allowingNonZeroExitCode()).asTask().getUnchecked() == 0;
    }
    private void cleanData() {
        if (app.getChildren().isEmpty()) return;
        for (Entity member : Iterables.getOnlyElement(app.getChildren()).getChildren()) {
            String runDir = member.getAttribute(MySqlNode.RUN_DIR);
            if (runDir != null) {
                Os.deleteRecursively(runDir);
            }
        }
    }

    private Location getLocation() {
        return mgmt.getLocationRegistry().resolve(TEST_LOCATION);
    }
}

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

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.effector.EffectorTasks;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.entity.database.mysql.MySqlNode.ExportDumpEffector;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.TaskTags;
import org.apache.brooklyn.util.core.task.ssh.SshTasks;
import org.apache.brooklyn.util.core.task.system.ProcessTaskFactory;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.StringEscapes.BashStringEscapes;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.concurrent.ConcurrentUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class InitSlaveTaskBody implements Runnable {
    private static final String SNAPSHOT_DUMP_OPTIONS = "--skip-lock-tables --single-transaction --flush-logs --hex-blob";

    private static final Logger log = LoggerFactory.getLogger(InitSlaveTaskBody.class);

    private final MySqlCluster cluster;
    private final MySqlNode slave;
    private Semaphore lock;

    public InitSlaveTaskBody(MySqlCluster cluster, MySqlNode slave, Semaphore lock) {
        this.cluster = cluster;
        this.slave = slave;
        this.lock = lock;
    }

    @Override
    public void run() {
        // Replication init state consists of:
        //   * initial dump (optional)
        //   * location of initial dump (could be on any of the members, optional)
        //   * bin log file name
        //   * bin log position
        // 1. Check replication state:
        //   * Does the dump exist (and the machine where it is located)
        //   * Does the bin log exist on the master
        // 2. If the replication state is not valid create a new one
        //   * Select a slave to dump, master if no slaves
        //   * If it's a slave do 'STOP SLAVE SQL_THREAD;'
        //   * Call mysqldump to create the snapshot
        //   * When done if a slave do 'START SLAVE SQL_THREAD;'
        //   * Get master state from the dump - grep "MASTER_LOG_POS" dump.sql.
        //     If slave get state from 'SHOW SLAVE STATUS'
        //   * Save new init info in cluster - bin log name, position, dump
        // 3. Init Slave
        //   * transfer dump to new slave (if dump exists)
        //   * import - mysql < ~/dump.sql
        //   * change master to and start slave
        //!!! Caveat if dumping from master and MyISAM tables are used dump may be inconsistent.
        //   * Only way around it is to lock the database while dumping (or taking a snapshot through LVM which is quicker)
        bootstrapSlaveAsync(getValidReplicationInfo(), slave);
        cluster.getAttribute(MySqlClusterImpl.SLAVE_ID_ADDRESS_MAPPING).put(slave.getId(), slave.getAttribute(MySqlNode.SUBNET_ADDRESS));
    }

    private MySqlNode getMaster() {
        return (MySqlNode) Iterables.find(cluster.getMembers(), MySqlClusterUtils.IS_MASTER);
    }

    private void bootstrapSlaveAsync(final Future<ReplicationSnapshot> replicationInfoFuture, final MySqlNode slave) {
        DynamicTasks.queue("bootstrap slave replication", new Runnable() {
            @Override
            public void run() {
                ReplicationSnapshot replicationSnapshot;
                try {
                    replicationSnapshot = replicationInfoFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw Exceptions.propagate(e);
                }

                MySqlNode master = getMaster();
                String masterAddress = MySqlClusterUtils.validateSqlParam(master.getAttribute(MySqlNode.SUBNET_ADDRESS));
                Integer masterPort = master.getAttribute(MySqlNode.MYSQL_PORT);
                String slaveAddress = MySqlClusterUtils.validateSqlParam(slave.getAttribute(MySqlNode.SUBNET_ADDRESS));
                String username = MySqlClusterUtils.validateSqlParam(cluster.getConfig(MySqlCluster.SLAVE_USERNAME));
                String password = MySqlClusterUtils.validateSqlParam(cluster.getAttribute(MySqlCluster.SLAVE_PASSWORD));

                if (replicationSnapshot.getEntityId() != null) {
                    Entity sourceEntity = Iterables.find(cluster.getMembers(), EntityPredicates.idEqualTo(replicationSnapshot.getEntityId()));
                    String dumpId = FilenameUtils.removeExtension(replicationSnapshot.getSnapshotPath());
                    copyDumpAsync(sourceEntity, slave, replicationSnapshot.getSnapshotPath(), dumpId);
                    DynamicTasks.queue(Effectors.invocation(slave, MySqlNode.IMPORT_DUMP, ImmutableMap.of("path", replicationSnapshot.getSnapshotPath())));
                    //The dump resets the password to whatever is on the source instance, reset it back.
                    //We are able to still login because privileges are not flushed, so we just set the password to the same value.
                    DynamicTasks.queue(Effectors.invocation(slave, MySqlNode.CHANGE_PASSWORD, ImmutableMap.of("password", slave.getAttribute(MySqlNode.PASSWORD))));                        //
                    //Flush privileges to load new users coming from the dump
                    MySqlClusterUtils.executeSqlOnNodeAsync(slave, "FLUSH PRIVILEGES;");
                }

                MySqlClusterUtils.executeSqlOnNodeAsync(master, String.format(
                        "CREATE USER '%s'@'%s' IDENTIFIED BY '%s';\n" +
                        "GRANT REPLICATION SLAVE ON *.* TO '%s'@'%s';\n",
                        username, slaveAddress, password, username, slaveAddress));

                // Executing this will unblock SERVICE_UP wait in the start effector
                String slaveCmd = String.format(
                        "CHANGE MASTER TO " +
                            "MASTER_HOST='%s', " +
                            "MASTER_PORT=%d, " +
                            "MASTER_USER='%s', " +
                            "MASTER_PASSWORD='%s', " +
                            "MASTER_LOG_FILE='%s', " +
                            "MASTER_LOG_POS=%d;\n" +
                        "START SLAVE;\n",
                        masterAddress, masterPort,
                        username, password,
                        replicationSnapshot.getBinLogName(),
                        replicationSnapshot.getBinLogPosition());
                MySqlClusterUtils.executeSqlOnNodeAsync(slave, slaveCmd);
            }
        });
    }

    private void copyDumpAsync(Entity source, Entity dest, String sourceDumpPath, String dumpId) {
        final SshMachineLocation sourceMachine = EffectorTasks.getSshMachine(source);
        final SshMachineLocation destMachine = EffectorTasks.getSshMachine(dest);

        String sourceRunDir = source.getAttribute(MySqlNode.RUN_DIR);
        String privateKeyFile = dumpId + ".id_rsa";
        final Task<String> tempKeyTask = DynamicTasks.queue(SshEffectorTasks.ssh(
                "cd $RUN_DIR",
                "PRIVATE_KEY=" + privateKeyFile,
                "ssh-keygen -t rsa -N '' -f $PRIVATE_KEY -C " + dumpId + " > /dev/null",
                "cat $PRIVATE_KEY.pub")
                .environmentVariable("RUN_DIR", sourceRunDir)
                .machine(sourceMachine)
                .summary("generate private key for slave access")
                .requiringZeroAndReturningStdout())
                .asTask();

        DynamicTasks.queue("add key to authorized_keys", new Runnable() {
            @Override
            public void run() {
                String publicKey = tempKeyTask.getUnchecked();
                DynamicTasks.queue(SshEffectorTasks.ssh(String.format(
                        "cat >> ~/.ssh/authorized_keys <<EOF\n%s\nEOF", 
                        publicKey))
                    .machine(destMachine)
                    .summary("Add key to authorized_keys")
                    .requiringExitCodeZero());
            }
        });

        final ProcessTaskWrapper<Integer> copyTask = SshEffectorTasks.ssh(
                "cd $RUN_DIR",
                String.format(
                    "scp -o 'BatchMode yes' -o 'StrictHostKeyChecking no' -i '%s' '%s' '%s@%s:%s/%s.sql'",
                    privateKeyFile,
                    sourceDumpPath,
                    destMachine.getUser(),
                    dest.getAttribute(MySqlNode.SUBNET_ADDRESS),
                    dest.getAttribute(MySqlNode.RUN_DIR),
                    dumpId))
                .environmentVariable("RUN_DIR", sourceRunDir)
                .machine(sourceMachine)
                .summary("copy database dump to slave")
                .newTask();
        // Let next couple of tasks complete even if this one fails so that we can clean up.
        TaskTags.markInessential(copyTask);
        DynamicTasks.queue(copyTask);

        // Delete private key
        DynamicTasks.queue(SshEffectorTasks.ssh(
                "cd $RUN_DIR",
                "rm " + privateKeyFile)
            .environmentVariable("RUN_DIR", sourceRunDir)
            .machine(sourceMachine)
            .summary("remove private key"));

        DynamicTasks.queue(SshEffectorTasks.ssh(String.format(
                "sed -i'' -e '/%s/d' ~/.ssh/authorized_keys",
                dumpId))
            .machine(destMachine)
            .summary("remove private key from authorized_keys")).asTask();

        // The task will fail if copyTask fails, but only after the private key is deleted.
        DynamicTasks.queue("check for successful copy", new Runnable() {
            @Override
            public void run() {
                copyTask.asTask().getUnchecked();
            }
        });
    }

    private Future<ReplicationSnapshot> getValidReplicationInfo() {
        try {
            try {
                lock.acquire();
            } catch (InterruptedException e) {
                throw Exceptions.propagate(e);
            }
            ReplicationSnapshot replicationSnapshot = getAttributeBlocking(cluster, MySqlCluster.REPLICATION_LAST_SLAVE_SNAPSHOT);
            if (!isReplicationInfoValid(replicationSnapshot)) {
                final MySqlNode snapshotNode = getSnapshotNode();
                final String dumpName = getDumpUniqueId() + ".sql";
                if (MySqlClusterUtils.IS_MASTER.apply(snapshotNode)) {
                    return createMasterReplicationSnapshot(snapshotNode, dumpName);
                } else {
                    return createSlaveReplicationSnapshot(snapshotNode, dumpName);
                }
            }
            return ConcurrentUtils.constantFuture(replicationSnapshot);
        } finally {
            lock.release();
        }
    }

    private Future<ReplicationSnapshot> createMasterReplicationSnapshot(final MySqlNode master, final String dumpName) {
        log.info("MySql cluster " + cluster + ": generating new replication snapshot on master node " + master + " with name " + dumpName);
        String dumpOptions = SNAPSHOT_DUMP_OPTIONS + " --master-data=2" + getDumpDatabases(master);
        ImmutableMap<String, String> params = ImmutableMap.of(
                ExportDumpEffector.PATH.getName(), dumpName,
                ExportDumpEffector.ADDITIONAL_OPTIONS.getName(), dumpOptions);
        DynamicTasks.queue(Effectors.invocation(master, MySqlNode.EXPORT_DUMP, params));
        return DynamicTasks.queue("get master log info from dump", new Callable<ReplicationSnapshot>() {
            @Override
            public ReplicationSnapshot call() throws Exception {
                Pattern masterInfoPattern = Pattern.compile("CHANGE MASTER TO.*MASTER_LOG_FILE\\s*=\\s*'([^']+)'.*MASTER_LOG_POS\\s*=\\s*(\\d+)");
                String masterInfo = DynamicTasks.queue(execSshTask(master, "grep -m1 'CHANGE MASTER TO' " + dumpName, "Extract master replication status from dump")
                        .requiringZeroAndReturningStdout()).asTask().getUnchecked();
                Matcher masterInfoMatcher = masterInfoPattern.matcher(masterInfo);
                if (!masterInfoMatcher.find() || masterInfoMatcher.groupCount() != 2) {
                    throw new IllegalStateException("Master dump doesn't contain replication info: " + masterInfo);
                }
                String masterLogFile = masterInfoMatcher.group(1);
                int masterLogPosition = Integer.parseInt(masterInfoMatcher.group(2));
                ReplicationSnapshot replicationSnapshot = new ReplicationSnapshot(master.getId(), dumpName, masterLogFile, masterLogPosition);
                cluster.sensors().set(MySqlCluster.REPLICATION_LAST_SLAVE_SNAPSHOT, replicationSnapshot);
                return replicationSnapshot;
            }
        });
    }

    private String getDumpDatabases(MySqlNode node) {
        // The config will be inherited from the cluster
        Collection<String> dumpDbs = node.config().get(MySqlCluster.SLAVE_REPLICATE_DUMP_DB);
        if (dumpDbs != null && !dumpDbs.isEmpty()) {
            return " --databases " + Joiner.on(' ').join(Iterables.transform(dumpDbs, BashStringEscapes.wrapBash()));
        } else {
            return " --all-databases";
        }
    }

    private Future<ReplicationSnapshot> createSlaveReplicationSnapshot(final MySqlNode slave, final String dumpName) {
        MySqlClusterUtils.executeSqlOnNodeAsync(slave, "STOP SLAVE SQL_THREAD;");
        try {
            log.info("MySql cluster " + cluster + ": generating new replication snapshot on slave node " + slave + " with name " + dumpName);
            String dumpOptions = SNAPSHOT_DUMP_OPTIONS + getDumpDatabases(slave);
            ImmutableMap<String, String> params = ImmutableMap.of(
                    ExportDumpEffector.PATH.getName(), dumpName,
                    ExportDumpEffector.ADDITIONAL_OPTIONS.getName(), dumpOptions);
            DynamicTasks.queue(Effectors.invocation(slave, MySqlNode.EXPORT_DUMP, params));
            return DynamicTasks.queue("get master log info from slave", new Callable<ReplicationSnapshot>() {
                @Override
                public ReplicationSnapshot call() throws Exception {
                    String slaveStatusRow = slave.executeScript("SHOW SLAVE STATUS \\G");
                    Map<String, String> slaveStatus = MySqlRowParser.parseSingle(slaveStatusRow);
                    String masterLogFile = slaveStatus.get("Relay_Master_Log_File");
                    int masterLogPosition = Integer.parseInt(slaveStatus.get("Exec_Master_Log_Pos"));
                    ReplicationSnapshot replicationSnapshot = new ReplicationSnapshot(slave.getId(), dumpName, masterLogFile, masterLogPosition);
                    cluster.sensors().set(MySqlCluster.REPLICATION_LAST_SLAVE_SNAPSHOT, replicationSnapshot);
                    return replicationSnapshot;
                }
            });
        } finally {
            MySqlClusterUtils.executeSqlOnNodeAsync(slave, "START SLAVE SQL_THREAD;");
        }
    }

    private MySqlNode getSnapshotNode() {
        String snapshotNodeId = cluster.getConfig(MySqlCluster.REPLICATION_PREFERRED_SOURCE);
        if (snapshotNodeId != null) {
            Optional<Entity> preferredNode = Iterables.tryFind(cluster.getMembers(), EntityPredicates.idEqualTo(snapshotNodeId));
            if (preferredNode.isPresent()) {
                return (MySqlNode) preferredNode.get();
            } else {
                log.warn("MySql cluster " + this + " configured with preferred snapshot node " + snapshotNodeId + " but it's not a member. Defaulting to a random slave.");
            }
        }
        return getRandomSlave();
    }

    private MySqlNode getRandomSlave() {
        List<MySqlNode> slaves = getHealhtySlaves();
        if (slaves.size() > 0) {
            return slaves.get(new Random().nextInt(slaves.size()));
        } else {
            return getMaster();
        }
    }

    private ImmutableList<MySqlNode> getHealhtySlaves() {
        return FluentIterable.from(cluster.getMembers())
                   .filter(Predicates.not(MySqlClusterUtils.IS_MASTER))
                   .filter(EntityPredicates.attributeEqualTo(MySqlNode.SERVICE_UP, Boolean.TRUE))
                   .filter(MySqlNode.class)
                   .toList();
    }

    private boolean isReplicationInfoValid(ReplicationSnapshot replicationSnapshot) {
        MySqlNode master = getMaster();
        String dataDir = Strings.nullToEmpty(master.getConfig(MySqlNode.DATA_DIR));
        if (!checkFileExistsOnEntity(master, Os.mergePathsUnix(dataDir, replicationSnapshot.getBinLogName()))) {
            return false;
        }
        if (replicationSnapshot.getEntityId() != null) {
            Optional<Entity> snapshotSlave = Iterables.tryFind(cluster.getChildren(), EntityPredicates.idEqualTo(replicationSnapshot.getEntityId()));
            if (!snapshotSlave.isPresent()) {
                log.info("MySql cluster " + cluster + " missing node " + replicationSnapshot.getEntityId() + " with last snapshot " + replicationSnapshot.getSnapshotPath() + ". Will generate new snapshot.");
                return false;
            }
            if (!checkFileExistsOnEntity(snapshotSlave.get(), replicationSnapshot.getSnapshotPath())) {
                log.info("MySql cluster " + cluster + ", node " + snapshotSlave.get() + " missing replication snapshot " + replicationSnapshot.getSnapshotPath() + ". Will generate new snapshot.");
                return false;
            }
        }
        return true;
    }

    private boolean checkFileExistsOnEntity(Entity entity, String path) {
        String cmd = BashCommands.chain(
                BashCommands.requireTest(String.format("-f \"%s\"", path), "File " + path + " doesn't exist."));
        String summary = "Check if file " + path + " exists";
        return DynamicTasks.queue(execSshTask(entity, cmd, summary).allowingNonZeroExitCode()).asTask().getUnchecked() == 0;
    }

    private ProcessTaskFactory<Integer> execSshTask(Entity entity, String cmd, String summary) {
        SshMachineLocation machine = EffectorTasks.getSshMachine(entity);
        return SshTasks.newSshExecTaskFactory(machine, "cd $RUN_DIR\n" + cmd)
            .allowingNonZeroExitCode()
            .environmentVariable("RUN_DIR", entity.getAttribute(SoftwareProcess.RUN_DIR))
            .summary(summary);
    }

    private <T> T getAttributeBlocking(Entity masterNode, AttributeSensor<T> att) {
        return DynamicTasks.queue(DependentConfiguration.attributeWhenReady(masterNode, att)).getUnchecked();
    }

    private String getDumpUniqueId() {
        return "replication-dump-" + Identifiers.makeRandomId(8) + "-" + new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss").format(new Date());
    }
}

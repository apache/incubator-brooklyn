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
package brooklyn.entity.database.postgresql;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.database.VogellaExampleAccess;
import brooklyn.entity.effector.EffectorTasks;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.salt.SaltConfig;
import brooklyn.entity.salt.SaltLiveTestSupport;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.PortRange;
import brooklyn.location.basic.PortRanges;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;

/**
 * Tests Salt installation of {@link PostgreSqlNode} entity.
 */
public class PostgreSqlSaltLiveTest extends SaltLiveTestSupport {

    private static final Logger log = LoggerFactory.getLogger(PostgreSqlSaltLiveTest.class);

    private PostgreSqlNode psql;

    @Override
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            if (psql != null) psql.stop();
        } finally {
            super.tearDown();
        }
    }

    @Test(groups="Live")
    public void testPostgresStartsAndStops() throws Exception {
        psql = app.createAndManageChild(EntitySpec.create(PostgreSqlNode.class, PostgreSqlNodeSaltImpl.class)
                .configure(SaltConfig.MASTERLESS_MODE, true));

        app.start(ImmutableList.of(targetLocation));

        Entities.submit(psql, SshEffectorTasks.ssh("ps aux | grep [p]ostgres").requiringExitCodeZero());
        SshMachineLocation targetMachine = EffectorTasks.getSshMachine(psql);

        psql.stop();

        try {
            // if host is still contactable ensure postgres is not running
            ProcessTaskWrapper<Integer> t = Entities.submit(app, SshEffectorTasks.ssh("ps aux | grep [p]ostgres").machine(targetMachine).allowingNonZeroExitCode());
            t.getTask().blockUntilEnded(Duration.TEN_SECONDS);
            if (!t.isDone()) {
                Assert.fail("Task not finished yet: "+t.getTask());
            }
            Assert.assertNotEquals(t.get(), (Integer)0, "Task ended with code "+t.get()+"; output: "+t.getStdout() );
        } catch (Exception e) {
            // host has been killed, that is fine
            log.info("Machine "+targetMachine+" destroyed on stop (expected - "+e+")");
        }
    }

    @Test(groups="Live")
    public void testPostgresScriptAndAccess() throws Exception {
        SaltLiveTestSupport.createLocation(mgmt);
        PortRange randomPort = PortRanges.fromString(""+(5420+new Random().nextInt(10))+"+");
        psql = app.createAndManageChild(EntitySpec.create(PostgreSqlNode.class, PostgreSqlNodeSaltImpl.class)
                .configure(SaltConfig.MASTERLESS_MODE, true)
                .configure(PostgreSqlNode.CREATION_SCRIPT_CONTENTS, PostgreSqlIntegrationTest.CREATION_SCRIPT)
                .configure(PostgreSqlNode.POSTGRESQL_PORT, randomPort));

        app.start(ImmutableList.of(targetLocation));

        String url = psql.getAttribute(PostgreSqlNode.DATASTORE_URL);
        log.info("Trying to connect to "+psql+" at "+url);
        Assert.assertNotNull(url);
        Assert.assertTrue(url.contains("542"));

        new VogellaExampleAccess("org.postgresql.Driver", url).readModifyAndRevertDataBase();
    }

}


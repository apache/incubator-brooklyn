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
package org.apache.brooklyn.entity.database.postgresql;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.apache.brooklyn.entity.chef.ChefLiveTestSupport;
import org.apache.brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import org.apache.brooklyn.entity.database.VogellaExampleAccess;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.core.effector.EffectorTasks;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;

/** 
 * Tests Chef installation of PostgreSql. Requires chef-server (knife).
 * <p> 
 * To be able to run repeatedly on the same box, you will need the patched version of the postgresql library,
 * at https://github.com/opscode-cookbooks/postgresql/pull/73 .
 *  
 * @author alex
 *
 */
public class PostgreSqlChefTest extends ChefLiveTestSupport {

    private static final Logger log = LoggerFactory.getLogger(PostgreSqlChefTest.class);
    
    PostgreSqlNode psql;
    
    @Test(groups="Live")
    public void testPostgresStartsAndStops() throws Exception {
        ChefLiveTestSupport.installBrooklynChefHostedConfig(app);
        psql = app.createAndManageChild(PostgreSqlSpecs.specChef());

        app.start(ImmutableList.of(targetLocation));
        
        Entities.submit(psql, SshEffectorTasks.ssh("ps aux | grep [p]ostgres").requiringExitCodeZero());
        SshMachineLocation targetMachine = EffectorTasks.getSshMachine(psql);
        
        psql.stop();
        
        try {
            // if host is still contactable ensure postgres is not running
            ProcessTaskWrapper<Integer> t = Entities.submit(app, SshEffectorTasks.ssh("ps aux | grep [p]ostgres").machine(targetMachine).allowingNonZeroExitCode());
            t.getTask().blockUntilEnded(Duration.TEN_SECONDS);
            if (!t.isDone())
                Assert.fail("Task not finished yet: "+t.getTask());
            Assert.assertNotEquals(t.get(), (Integer)0, "Task ended with code "+t.get()+"; output: "+t.getStdout() );
        } catch (Exception e) {
            // host has been killed, that is fine
            log.info("Machine "+targetMachine+" destroyed on stop (expected - "+e+")");
        }
    }
    
    @Test(groups="Live")
    public void testPostgresScriptAndAccess() throws Exception {
        ChefLiveTestSupport.installBrooklynChefHostedConfig(app);
        PortRange randomPort = PortRanges.fromString(String.format("%d+", 5420 + new Random().nextInt(10)));
        psql = app.createAndManageChild(PostgreSqlSpecs.specChef()
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, PostgreSqlIntegrationTest.CREATION_SCRIPT)
                .configure(PostgreSqlNode.POSTGRESQL_PORT, randomPort)
                .configure(PostgreSqlNode.SHARED_MEMORY, "8MB")
            );

        app.start(ImmutableList.of(targetLocation));

        String url = psql.getAttribute(DatastoreCommon.DATASTORE_URL);
        log.info("Trying to connect to "+psql+" at "+url);
        Assert.assertNotNull(url);
        Assert.assertTrue(url.contains("542"));
        
        new VogellaExampleAccess("org.postgresql.Driver", url).readModifyAndRevertDataBase();
    }

}


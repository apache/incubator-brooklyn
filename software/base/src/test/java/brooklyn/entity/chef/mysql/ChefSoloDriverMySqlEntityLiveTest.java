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
package brooklyn.entity.chef.mysql;

import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.util.task.system.ProcessTaskWrapper;

public class ChefSoloDriverMySqlEntityLiveTest extends AbstractChefToyMySqlEntityLiveTest {

    // test here just so Eclipse IDE picks it up
    @Override @Test(groups="Live")
    public void testMySqlOnProvisioningLocation() throws NoMachinesAvailableException {
        super.testMySqlOnProvisioningLocation();
    }

    @Override
    protected Integer getPid(Entity mysql) {
        ProcessTaskWrapper<Integer> t = Entities.submit(mysql, SshEffectorTasks.ssh("sudo cat "+ChefSoloDriverToyMySqlEntity.PID_FILE));
        return Integer.parseInt(t.block().getStdout().trim());
    }
    
    @Override
    protected Entity createMysql() {
        return app.createAndManageChild(EntitySpec.create(Entity.class, ChefSoloDriverToyMySqlEntity.class).
                additionalInterfaces(SoftwareProcess.class));
    }
    
}

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
package brooklyn.entity.software.mysql;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;


public class DynamicToyMySqlEntityTest extends AbstractToyMySqlEntityTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicToyMySqlEntityTest.class);
    
    protected Entity createMysql() {
        Entity mysql = app.createAndManageChild(DynamicToyMySqlEntityBuilder.spec());
        log.debug("created "+mysql);
        return mysql;
    }

    // put right group on test (also help Eclipse IDE pick it up)
    @Override
    @Test(groups = "Integration")
    public void testMySqlOnProvisioningLocation() throws NoMachinesAvailableException {
        super.testMySqlOnProvisioningLocation();
    }
    
    @Test(groups="Integration")
    public void testMySqlOnMachineLocation() throws NoMachinesAvailableException {
        Entity mysql = createMysql();
        SshMachineLocation lh = targetLocation.obtain(MutableMap.of());
        app.start(Arrays.asList(lh));
        checkStartsRunning(mysql);
        checkIsRunningAndStops(mysql, lh);
    }

}

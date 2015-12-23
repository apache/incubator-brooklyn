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
package org.apache.brooklyn.entity.chef.mysql;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.entity.chef.ChefLiveTestSupport;
import org.apache.brooklyn.entity.chef.ChefServerTasksIntegrationTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

/** Expects knife on the path, but will use Brooklyn registered account,
 * and that account has the mysql recipe installed.
 * <p>
 * See {@link ChefServerTasksIntegrationTest} for more info. */
public class DynamicChefServerToyMySqlEntityLiveTest extends AbstractChefToyMySqlEntityLiveTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicChefServerToyMySqlEntityLiveTest.class);
    
    // test here just so Eclipse IDE picks it up
    @Override @Test(groups="Live")
    public void testMySqlOnProvisioningLocation() throws Exception {
        super.testMySqlOnProvisioningLocation();
    }
    
    @Override
    protected Entity createMysql() {
        ChefLiveTestSupport.installBrooklynChefHostedConfig(app);
        Entity mysql = app.createAndManageChild(DynamicToyMySqlEntityChef.specKnife());
        log.debug("created "+mysql);
        return mysql;
    }
    
}

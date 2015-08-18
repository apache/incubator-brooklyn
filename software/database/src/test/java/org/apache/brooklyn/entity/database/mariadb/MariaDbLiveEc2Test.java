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
package org.apache.brooklyn.entity.database.mariadb;

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import org.apache.brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import org.apache.brooklyn.entity.database.VogellaExampleAccess;

import org.apache.brooklyn.location.jclouds.JcloudsLocation;

import com.google.common.collect.ImmutableList;

@Test(groups = { "Live" })
public class MariaDbLiveEc2Test extends AbstractEc2LiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {
        // TODO For some CentOS VMs (e.g. in AWS 6.3, us-east-1/ami-a96b01c0), currently need to turn off iptables unfortunately.
        // Should really just open the ports in iptables.
        MariaDbNode mariadb = app.createAndManageChild(EntitySpec.create(MariaDbNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, MariaDbIntegrationTest.CREATION_SCRIPT)
                .configure(MariaDbNode.PROVISIONING_PROPERTIES.subKey(JcloudsLocation.STOP_IPTABLES.getName()), true));

        app.start(ImmutableList.of(loc));

        new VogellaExampleAccess("com.mysql.jdbc.Driver", mariadb.getAttribute(DatastoreCommon.DATASTORE_URL)).readModifyAndRevertDataBase();
    }

    @Override
    @Test(enabled=false, groups = "Live")
    public void test_Debian_7_2() throws Exception { } // Disabled because MariaDB not available

    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  

}


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

import java.util.Arrays;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.testng.annotations.Test;
import org.apache.brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import org.apache.brooklyn.entity.database.VogellaExampleAccess;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.net.Protocol;
import org.apache.brooklyn.util.ssh.IptablesCommands;
import org.apache.brooklyn.util.ssh.IptablesCommands.Chain;
import org.apache.brooklyn.util.ssh.IptablesCommands.Policy;

import com.google.common.collect.ImmutableList;

/**
 * The MySqlLiveTest installs MySQL on various operating systems like Ubuntu, CentOS, Red Hat etc. To make sure that
 * MySQL works like expected on these Operating Systems.
 */
public class MySqlLiveRackspaceTest extends MySqlIntegrationTest {
    @Test(groups = {"Live"})
    public void test_Debian_6() throws Exception {
        test("Debian 6");
    }

    @Test(groups = {"Live"})
    public void test_Ubuntu_10_0() throws Exception {
        test("Ubuntu 10.0");
    }

    @Test(groups = {"Live", "Live-sanity"})
    public void test_Ubuntu_12_0() throws Exception {
        test("Ubuntu 12.0");
    }

    @Test(groups = {"Live"})
    public void test_Ubuntu_13() throws Exception {
        test("Ubuntu 13");
    }

    @Test(groups = {"Live"})
    public void test_CentOS_6() throws Exception {
        test("CentOS 6");
    }

    @Test(groups = {"Live"})
    public void test_CentOS_5() throws Exception {
        test("CentOS 5");
    }

    @Test(groups = {"Live"})
    public void test_Fedora() throws Exception {
        test("Fedora ");
    }

    @Test(groups = {"Live"})
    public void test_Red_Hat_Enterprise_Linux_6() throws Exception {
        test("Red Hat Enterprise Linux 6");
    }

    @Test(enabled=false, groups = {"Live"}) // only run this in MySqlIntegrationTest
    public void test_localhost() throws Exception {
        super.test_localhost();
    }

    public void test(String osRegex) throws Exception {
        MySqlNode mysql = app.createAndManageChild(EntitySpec.create(MySqlNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, CREATION_SCRIPT)
                .configure("test.table.name", "COMMENTS"));

        BrooklynProperties brooklynProperties = mgmt.getBrooklynProperties();
        brooklynProperties.put("brooklyn.location.jclouds.rackspace-cloudservers-uk.imageNameRegex", osRegex);
        brooklynProperties.remove("brooklyn.location.jclouds.rackspace-cloudservers-uk.image-id");
        brooklynProperties.remove("brooklyn.location.jclouds.rackspace-cloudservers-uk.imageId");
        brooklynProperties.put("brooklyn.location.jclouds.rackspace-cloudservers-uk.inboundPorts", Arrays.asList(22, 3306));
        JcloudsLocation jcloudsLocation = (JcloudsLocation) mgmt.getLocationRegistry().resolve("jclouds:rackspace-cloudservers-uk");

        app.start(ImmutableList.of(jcloudsLocation));

        SshMachineLocation l = (SshMachineLocation) mysql.getLocations().iterator().next();
        l.execCommands("add iptables rule", ImmutableList.of(IptablesCommands.insertIptablesRule(Chain.INPUT, Protocol.TCP, 3306, Policy.ACCEPT)));

        new VogellaExampleAccess("com.mysql.jdbc.Driver", mysql.getAttribute(DatastoreCommon.DATASTORE_URL)).readModifyAndRevertDataBase();
    } 
}

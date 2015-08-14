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
package brooklyn.entity.brooklynnode;

import java.io.File;
import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.test.EntityTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.brooklynnode.BrooklynNode.ExistingFileBehaviour;
import org.apache.brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class BrooklynClusterIntegrationTest extends BrooklynAppUnitTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynNodeIntegrationTest.class);

    private File pseudoBrooklynPropertiesFile;
    private File pseudoBrooklynCatalogFile;
    private File persistenceDir;
    private LocalhostMachineProvisioningLocation loc;
    private List<LocalhostMachineProvisioningLocation> locs;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        pseudoBrooklynPropertiesFile = Os.newTempFile("brooklynnode-test", ".properties");
        pseudoBrooklynPropertiesFile.delete();

        pseudoBrooklynCatalogFile = Os.newTempFile("brooklynnode-test", ".catalog");
        pseudoBrooklynCatalogFile.delete();

        loc = app.newLocalhostProvisioningLocation();
        locs = ImmutableList.of(loc);
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        try {
            super.tearDown();
        } finally {
            if (pseudoBrooklynPropertiesFile != null) pseudoBrooklynPropertiesFile.delete();
            if (pseudoBrooklynCatalogFile != null) pseudoBrooklynCatalogFile.delete();
            if (persistenceDir != null) Os.deleteRecursively(persistenceDir);
        }
    }

    @Test(groups="Integration")
    public void testCanStartAndStop() throws Exception {
        BrooklynCluster cluster = app.createAndManageChild(EntitySpec.create(BrooklynCluster.class)
                .configure(BrooklynCluster.INITIAL_SIZE, 1)
                .configure(BrooklynNode.WEB_CONSOLE_BIND_ADDRESS, Networking.ANY_NIC)
                .configure(BrooklynNode.ON_EXISTING_PROPERTIES_FILE, ExistingFileBehaviour.DO_NOT_USE));
        app.start(locs);
        Entity brooklynNode = Iterables.find(cluster.getMembers(), Predicates.instanceOf(BrooklynNode.class));
        LOG.info("started "+app+" containing "+cluster+" for "+JavaClassNames.niceClassAndMethod());

        EntityTestUtils.assertAttributeEqualsEventually(cluster, BrooklynNode.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(brooklynNode, BrooklynNode.SERVICE_UP, true);

        cluster.stop();
        EntityTestUtils.assertAttributeEquals(cluster, BrooklynNode.SERVICE_UP, false);
    }    
}

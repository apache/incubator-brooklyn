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
package org.apache.brooklyn.entity.nosql.mongodb;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.software.base.VanillaWindowsProcess;
import org.apache.brooklyn.entity.software.base.test.location.WindowsTestFixture;
import org.apache.brooklyn.location.winrm.WinRmMachineLocation;
import org.apache.brooklyn.test.EntityTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MongoDBWinEc2LiveTest {
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBWinEc2LiveTest.class);

    protected ManagementContextInternal mgmt;
    protected TestApplication app;
    protected MachineProvisioningLocation<WinRmMachineLocation> location;
    protected WinRmMachineLocation machine;

    @BeforeClass(alwaysRun = true)
    public void setUpClass() throws Exception {
        mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());

        location = WindowsTestFixture.setUpWindowsLocation(mgmt);
        machine = location.obtain(ImmutableMap.of());
    }

    @AfterClass(alwaysRun = true)
    public void tearDownClass() throws Exception {
        try {
            try {
                if (location != null)
                    location.release(machine);
            } finally {
                if (mgmt != null)
                    Entities.destroyAll(mgmt);
            }
        } catch (Throwable t) {
            LOG.error("Caught exception in tearDownClass method", t);
        } finally {
            mgmt = null;
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class)
                .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, true);
        app = ApplicationBuilder.newManagedApp(appSpec, mgmt);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        try {
            try {
                if (app != null)
                    Entities.destroy(app);
            } catch (Throwable t) {
                LOG.error("Caught exception in tearDown method", t);
            }
        } finally {
            app = null;
        }
    }

    @Test(groups = "Live")
    public void doTest() {

        ImmutableMap<String, String> installTemplates = new ImmutableMap.Builder<String, String>()
                .put("classpath://org/apache/brooklyn/entity/nosql/mongodb/win/install_mongodb.ps1", "C:\\install_mongodb.ps1")
                .put("classpath://org/apache/brooklyn/entity/nosql/mongodb/win/configure_mongodb.ps1", "C:\\configure_mongodb.ps1")
                .put("classpath://org/apache/brooklyn/entity/nosql/mongodb/win/launch_mongodb.ps1", "C:\\launch_mongodb.ps1")
                .put("classpath://org/apache/brooklyn/entity/nosql/mongodb/win/stop_mongodb.ps1", "C:\\stop_mongodb.ps1")
                .put("classpath://org/apache/brooklyn/entity/nosql/mongodb/win/checkrunning_mongodb.ps1", "C:\\checkrunning_mongodb.ps1")
                .build();

        VanillaWindowsProcess entity = app.createAndManageChild(EntitySpec.create(VanillaWindowsProcess.class)
                .configure(VanillaWindowsProcess.INSTALL_TEMPLATES, installTemplates)
                .configure(VanillaWindowsProcess.INSTALL_POWERSHELL_COMMAND, "C:\\install_mongodb.ps1")
                .configure(VanillaWindowsProcess.CUSTOMIZE_POWERSHELL_COMMAND, "C:\\configure_mongodb.ps1")
                .configure(VanillaWindowsProcess.LAUNCH_POWERSHELL_COMMAND, "C:\\launch_mongodb.ps1")
                .configure(VanillaWindowsProcess.CHECK_RUNNING_POWERSHELL_COMMAND, "C:\\checkrunning_mongodb.ps1")
                .configure(VanillaWindowsProcess.STOP_POWERSHELL_COMMAND, "C:\\stop_mongodb.ps1")
                .configure(VanillaWindowsProcess.PROVISIONING_PROPERTIES, ImmutableMap.<String, Object> of("required.ports", "27017"))
                .configure("mongodb.download.url", "https://fastdl.mongodb.org/win32/mongodb-win32-x86_64-2008plus-ssl-3.0.6-signed.msi")
                .configure("mongodb.instance.name", "Instance1"));

        app.start(ImmutableList.of(machine));
        LOG.info("app started; asserting up");
        EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);

        entity.stop();
        LOG.info("stopping entity");
        EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, false);
    }

    @Test(enabled = false)
    public void testDummy() {
    } // Convince TestNG IDE integration that this really does have test methods

}

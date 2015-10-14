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
package org.apache.brooklyn.core.plan;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import com.google.common.collect.Iterables;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** Tests the sample {@link XmlPlanToSpecTransformer}
 * which illustrates how the {@link PlanToSpecTransformer} can be used. */
public class XmlPlanToSpecTransformerTest {

    private ManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        PlanToSpecFactory.forceAvailable(XmlPlanToSpecTransformer.class);
        mgmt = LocalManagementContextForTests.newInstance();
    }
    
    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        PlanToSpecFactory.clearForced();
        if (mgmt!=null) Entities.destroyAll(mgmt);
    }

    @Test
    public void testSimpleXmlPlanParse() {
        EntitySpec<? extends Application> appSpec = EntityManagementUtils.createEntitySpecForApplication(mgmt, 
            "<root><a_kid foo=\"bar\"/></root>");
        Application app = EntityManagementUtils.createStarting(mgmt, appSpec).get();
        Entities.dumpInfo(app);
        Assert.assertEquals(app.getDisplayName(), "root");
        Entity child = Iterables.getOnlyElement(app.getChildren());
        Assert.assertEquals(child.getDisplayName(), "a_kid");
        Assert.assertEquals(child.config().get(ConfigKeys.newStringConfigKey("foo")), "bar");
    }
    
}

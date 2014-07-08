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
package brooklyn.entity.chef;

import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.test.entity.TestApplication;

public class ChefConfigsTest {

    private TestApplication app = null;

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app!=null) Entities.destroyAll(app.getManagementContext());
        app = null;
    }
    
    @Test
    public void testAddToRunList() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        ChefConfigs.addToLaunchRunList(app, "a", "b");
        Set<? extends String> runs = app.getConfig(ChefConfig.CHEF_LAUNCH_RUN_LIST);
        Assert.assertEquals(runs.size(), 2, "runs="+runs);
        Assert.assertTrue(runs.contains("a"));
        Assert.assertTrue(runs.contains("b"));
    }
    
}

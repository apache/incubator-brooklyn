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
package brooklyn.extras.whirr.hadoop;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.exceptions.Exceptions;

public class WhirrHadoopClusterTest {

    private TestApplication app;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testSizeTooSmall() throws Exception {
        try {
            WhirrHadoopCluster whc = app.createAndManageChild(EntitySpec.create(WhirrHadoopCluster.class)
                    .configure("size", 1));
        } catch (Exception e) {
            IllegalArgumentException iae = Exceptions.getFirstThrowableOfType(e, IllegalArgumentException.class);
            if (iae != null && iae.toString().contains("Min cluster size is 2")) {
                // success
            } else {
                throw e;
            }
        }
    }

    @Test
    public void testDefaultsDontFailInRecipeGeneration() throws Exception {
        WhirrHadoopCluster whc = app.createAndManageChild(EntitySpec.create(WhirrHadoopCluster.class));
        whc.generateWhirrClusterRecipe();
    }

}

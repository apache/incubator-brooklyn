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
package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals
import static org.testng.Assert.assertTrue

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.ConfigMapTest.MyOtherEntity
import brooklyn.entity.basic.ConfigMapTest.MySubEntity
import brooklyn.test.entity.TestApplication

public class ConfigMapGroovyTest {

    private TestApplication app;
    private MySubEntity entity;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = TestApplication.Factory.newManagedInstanceForTests();
        entity = new MySubEntity(app);
        Entities.manage(entity);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testGetConfigOfTypeClosureReturnsClosure() throws Exception {
        MyOtherEntity entity2 = new MyOtherEntity(app);
        entity2.setConfig(MyOtherEntity.CLOSURE_KEY, { return "abc" } );
        Entities.manage(entity2);
        
        Closure configVal = entity2.getConfig(MyOtherEntity.CLOSURE_KEY);
        assertTrue(configVal instanceof Closure, "configVal="+configVal);
        assertEquals(configVal.call(), "abc");
    }


}

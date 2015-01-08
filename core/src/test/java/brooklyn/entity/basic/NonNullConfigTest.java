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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.RequiredConfigEntity;
import brooklyn.test.entity.RequiredConfigEntitySuper;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.exceptions.Exceptions;

public class NonNullConfigTest {

    private ManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = LocalManagementContextForTests.newInstance();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }

    @Test
    public void testSanity() {
        TestApplication app = TestApplication.Factory.newManagedInstanceForTests(managementContext);
        // NON_NULL_CONFIG_WITH_DEFAULT_VALUE should not need to be set
        app.createAndManageChild(EntitySpec.create(RequiredConfigEntity.class)
                .configure(RequiredConfigEntity.NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE, new Object())
                .configure(RequiredConfigEntitySuper.NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE_IN_SUPER, new Object()));
    }

    @Test
    public void testCreateSpecFailsWhenRequiredConfigKeyWithDefaultValueIsSetToNull() {
        TestApplication app = TestApplication.Factory.newManagedInstanceForTests(managementContext);
        try {
            app.createAndManageChild(EntitySpec.create(RequiredConfigEntity.class)
                    .configure(RequiredConfigEntity.NON_NULL_CONFIG_WITH_DEFAULT_VALUE, (Object) null)
                    .configure(RequiredConfigEntity.NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE, new Object())
                    .configure(RequiredConfigEntitySuper.NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE_IN_SUPER, new Object()));
            fail("Creation of entity should have failed because " + RequiredConfigEntity.NON_NULL_CONFIG_WITH_DEFAULT_VALUE +
                    " was set to null");
        } catch (Exception e) {
            Throwable t = Exceptions.getFirstInteresting(e);
            assertEquals(t.getClass(), IllegalArgumentException.class);
            assertTrue(t.getMessage().contains(RequiredConfigEntity.NON_NULL_CONFIG_WITH_DEFAULT_VALUE.getName()),
                    "exception=" + t);
        }
    }

    @Test
    public void testCreateSpecFailsWhenRequiredConfigKeyWithoutDefaultValueIsNotSet() {
        TestApplication app = TestApplication.Factory.newManagedInstanceForTests(managementContext);
        try {
            app.createAndManageChild(EntitySpec.create(RequiredConfigEntity.class)
                    .configure(RequiredConfigEntitySuper.NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE_IN_SUPER, new Object()));
            fail("Creation of entity should have failed because " + RequiredConfigEntity.NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE +
                    " was not set.");
        } catch (Exception e) {
            Throwable t = Exceptions.getFirstInteresting(e);
            assertEquals(t.getClass(), IllegalArgumentException.class);
            assertTrue(t.getMessage().contains(RequiredConfigEntity.NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE.getName()),
                    "exception=" + t);
        }
    }

    @Test
    public void testNullValueOnRequiredConfigInSuperclassCaught() {
        TestApplication app = TestApplication.Factory.newManagedInstanceForTests(managementContext);
        try {
            // Missing a value for NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE_IN_SUPER.
            app.createAndManageChild(EntitySpec.create(RequiredConfigEntity.class)
                    .configure(RequiredConfigEntity.NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE, new Object()));
        } catch (Exception e) {
            Throwable t = Exceptions.getFirstInteresting(e);
            assertEquals(t.getClass(), IllegalArgumentException.class);
            assertTrue(t.getMessage().contains(RequiredConfigEntity.NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE_IN_SUPER.getName()),
                    "exception=" + t);
        }
    }

}

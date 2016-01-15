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
package org.apache.brooklyn.core.test;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.testng.annotations.BeforeMethod;

/**
 * To be extended by live tests.
 * <p>
 * Uses a management context that will not load {@code ~/.brooklyn/catalog.xml} but will
 * read from the default {@code ~/.brooklyn/brooklyn.properties}.
 */
public class BrooklynAppLiveTestSupport extends BrooklynMgmtUnitTestSupport {

    protected TestApplication app;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        if (mgmt!=null) {
            app = mgmt.getEntityManager().createEntity(newAppSpec());
        } else {
            mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());
            app = mgmt.getEntityManager().createEntity(newAppSpec());
        }
    }

    protected EntitySpec<? extends TestApplication> newAppSpec() {
        return EntitySpec.create(TestApplication.class);
    }
}

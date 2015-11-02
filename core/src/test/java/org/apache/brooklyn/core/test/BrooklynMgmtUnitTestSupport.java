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

import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/**
 * To be extended by unit/integration tests.
 * <p>
 * Uses a light-weight management context that will not read {@code ~/.brooklyn/brooklyn.properties}.
 */
public class BrooklynMgmtUnitTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynMgmtUnitTestSupport.class);

    protected ManagementContextInternal mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        if (mgmt == null) {
            mgmt = LocalManagementContextForTests.newInstance();
        }
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            if (mgmt != null) Entities.destroyAll(mgmt);
        } catch (Throwable t) {
            LOG.error("Caught exception in tearDown method", t);
        } finally {
            mgmt = null;
        }
    }

}

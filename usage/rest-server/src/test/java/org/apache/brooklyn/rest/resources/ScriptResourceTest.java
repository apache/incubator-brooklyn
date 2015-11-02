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
package org.apache.brooklyn.rest.resources;

import java.util.Collections;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.rest.domain.ScriptExecutionSummary;
import org.apache.brooklyn.rest.testing.mocks.RestMockApp;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ScriptResourceTest {

    @Test
    public void testGroovy() {
        ManagementContext mgmt = LocalManagementContextForTests.newInstance();
        Application app = mgmt.getEntityManager().createEntity( EntitySpec.create(Application.class, RestMockApp.class) );
        try {
        
            Entities.start(app, Collections.<Location>emptyList());

            ScriptResource s = new ScriptResource();
            s.injectManagementContext(mgmt);

            ScriptExecutionSummary result = s.groovy(null, "def apps = []; mgmt.applications.each { println 'app:'+it; apps << it.id }; apps");
            Assert.assertEquals(Collections.singletonList(app.getId()).toString(), result.getResult());
            Assert.assertTrue(result.getStdout().contains("app:RestMockApp"));
        
        } finally { Entities.destroyAll(mgmt); }
    }
    
}

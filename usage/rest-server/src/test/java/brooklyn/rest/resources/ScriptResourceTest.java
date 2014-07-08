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
package brooklyn.rest.resources;

import java.util.Collections;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.rest.domain.ScriptExecutionSummary;
import brooklyn.rest.testing.mocks.RestMockApp;

public class ScriptResourceTest {

    @Test
    public void testGroovy() {
        RestMockApp app = new RestMockApp();
        Entities.startManagement(app);
        ManagementContext mgmt = app.getManagementContext();
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

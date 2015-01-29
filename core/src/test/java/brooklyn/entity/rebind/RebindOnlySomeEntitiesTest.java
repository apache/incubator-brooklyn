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
package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;

import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.test.entity.TestEntity;

public class RebindOnlySomeEntitiesTest extends RebindTestFixtureWithApp {

    @Test
    public void testRebindOnlySomeSimple() throws Exception {
        TestEntity c1 = origApp.addChild(EntitySpec.create(TestEntity.class));
        Entities.manage(c1);
        
        // XXX
        newApp = rebind();
        
        assertNotSame(newApp, origApp);
        assertEquals(newApp.getId(), origApp.getId());
    }
    
}

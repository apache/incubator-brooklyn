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
package org.apache.brooklyn.core.mgmt.rebind;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.mgmt.rebind.RebindManagerImpl;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ActivePartialRebindTest extends RebindTestFixtureWithApp {

    private static final Logger log = LoggerFactory.getLogger(ActivePartialRebindTest.class);
    
    protected void doPartialRebindOfIds(String ...objectsToRebindIds) {
        RebindManagerImpl rm = (RebindManagerImpl) origManagementContext.getRebindManager();
        rm.rebindPartialActive(null, objectsToRebindIds);        
    }
    
    @Test
    public void testRebindChildSimple() throws Exception {
        TestEntity c1 = origApp.addChild(EntitySpec.create(TestEntity.class));
        AbstractEntity c1r = Entities.deproxy(c1);
        
        doPartialRebindOfIds(c1.getId());
        
        BrooklynObject c2 = origManagementContext.lookup(c1.getId());
        AbstractEntity c2r = Entities.deproxy((Entity)c2);
        
        Assert.assertTrue(c2 == c1, "Proxy instance should be the same: "+c1+" / "+c2);
        Assert.assertFalse(c2r == c1r, "Real instance should NOT be the same: "+c1r+" / "+c2r);
    }

    @Test
    public void testRebindParentSimple() throws Exception {
        TestEntity c1 = origApp.addChild(EntitySpec.create(TestEntity.class));
        
        AbstractEntity origAppr = Entities.deproxy(origApp);
        
        doPartialRebindOfIds(origApp.getId());
        
        BrooklynObject app2 = origManagementContext.lookup(origApp.getId());
        AbstractEntity app2r = Entities.deproxy((Entity)app2);
        
        Assert.assertTrue(app2 == origApp, "Proxy instance should be the same: "+app2+" / "+origApp);
        Assert.assertFalse(app2r == origAppr, "Real instance should NOT be the same: "+app2r+" / "+origAppr);
        
        Assert.assertTrue(c1.getManagementSupport().isDeployed());
        
        // check that child of parent is not a new unmanaged entity
        Entity c1b = origApp.getChildren().iterator().next();
        Assert.assertTrue(c1.getManagementSupport().isDeployed());
        Assert.assertTrue( ((EntityInternal)c1b).getManagementSupport().isDeployed(), "Not deployed: "+c1b );
    }

    @Test(groups="Integration")
    public void testRebindCheckingMemoryLeak() throws Exception {
        TestEntity c1 = origApp.addChild(EntitySpec.create(TestEntity.class));
        c1.config().set(TestEntity.CONF_NAME, Strings.makeRandomId(1000000));
        
        gcAndLog("before");
        long used0 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        for (int i=0; i<200; i++) {
            doPartialRebindOfIds(c1.getId());
            origManagementContext.getGarbageCollector().gcIteration();
            gcAndLog("iteration "+i);
            if (i==5) used0 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); 
        }
        gcAndLog("after");
        long used1 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        Assert.assertTrue(used1 - used0 < 5000000, "Expected leak of less than 5M; leak was: from "+Strings.makeJavaSizeString(used0)+" to "+Strings.makeJavaSizeString(used1));
    }

    private void gcAndLog(String prefix) {
        origManagementContext.getGarbageCollector().gcIteration();
        System.gc(); System.gc();
        log.info(prefix+": "+origManagementContext.getGarbageCollector().getUsageString());
    }

}

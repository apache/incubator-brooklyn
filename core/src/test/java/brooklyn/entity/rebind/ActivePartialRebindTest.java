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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.basic.BrooklynObject;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.text.Strings;

public class ActivePartialRebindTest extends RebindTestFixtureWithApp {

    private static final Logger log = LoggerFactory.getLogger(ActivePartialRebindTest.class);
    
    protected void doPartialRebindOfIds(String ...objectsToRebindIds) {
        RebindManagerImpl rm = (RebindManagerImpl) origManagementContext.getRebindManager();
        rm.rebindPartialActive(null, objectsToRebindIds);        
    }
    
    @Test
    public void testRebindOneSimple() throws Exception {
        TestEntity c1 = origApp.addChild(EntitySpec.create(TestEntity.class));
        Entities.manage(c1);
        AbstractEntity c1r = Entities.deproxy(c1);
        
        doPartialRebindOfIds(c1.getId());
        
        BrooklynObject c2 = origManagementContext.lookup(c1.getId());
        AbstractEntity c2r = Entities.deproxy((Entity)c2);
        
        Assert.assertTrue(c2 == c1, "Proxy instance should be the same: "+c1+" / "+c2);
        Assert.assertFalse(c2r == c1r, "Real instance should NOT be the same: "+c1r+" / "+c2r);
    }

    @Test(groups="Integration")
    public void testRebindCheckingMemoryLeak() throws Exception {
        TestEntity c1 = origApp.addChild(EntitySpec.create(TestEntity.class));
        Entities.manage(c1);
        c1.setConfig(TestEntity.CONF_NAME, Strings.makeRandomId(1000000));
        
        gcAndLog("before");
        long used0 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        for (int i=0; i<500; i++) {
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
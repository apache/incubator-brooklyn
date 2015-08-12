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
package brooklyn.management.ha;

import java.util.Collection;
import java.util.List;

import org.apache.brooklyn.management.ha.HighAvailabilityMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.persister.InMemoryObjectStore;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.location.Location;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableList;

import com.google.common.collect.Iterables;

@Test
public class HighAvailabilityManagerInMemoryTest extends HighAvailabilityManagerTestFixture {

    private static final Logger log = LoggerFactory.getLogger(HighAvailabilityManagerInMemoryTest.class);
    
    protected PersistenceObjectStore newPersistenceObjectStore() {
        return new InMemoryObjectStore();
    }
    
    public void testGetManagementPlaneStatus() throws Exception {
        super.testGetManagementPlaneStatus();
    }

    // extra test that promoteToMaster doesn't interfere with what is managed
    public void testLocationsStillManagedCorrectlyAfterDoublePromotion() throws NoMachinesAvailableException {
        HighAvailabilityManagerImpl ha = (HighAvailabilityManagerImpl) managementContext.getHighAvailabilityManager();
        ha.start(HighAvailabilityMode.MASTER);
        
        TestApplication app = TestApplication.Factory.newManagedInstanceForTests(managementContext);
        
        LocalhostMachineProvisioningLocation l = app.newLocalhostProvisioningLocation();
        l.setConfig(TestEntity.CONF_NAME, "sample1");
        Assert.assertEquals(l.getConfig(TestEntity.CONF_NAME), "sample1");
        
        SshMachineLocation l2 = l.obtain();
        Assert.assertEquals(l2.getConfig(TestEntity.CONF_NAME), "sample1");
        Assert.assertNotNull(l2.getParent(), "Parent not set after dodgy promoteToMaster");
        Assert.assertEquals(l2.getParent().getConfig(TestEntity.CONF_NAME), "sample1");

        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class).location(l).location(l2));
        log.info("Entities managed are: "+managementContext.getEntityManager().getEntities());
        Collection<Location> le = entity.getLocations();
        log.info("Locs at entity are: "+le);
        Collection<Location> lm = managementContext.getLocationManager().getLocations();
        log.info("Locs managed are: "+lm);
        log.info("            objs: "+identities(lm));
        Assert.assertNotNull(entity.getManagementSupport().getManagementContext());
        Assert.assertNotNull( ((EntityInternal)app.getChildren().iterator().next()).getManagementSupport().getManagementContext());
        Assert.assertTrue( ((EntityInternal)app.getChildren().iterator().next()).getManagementSupport().isDeployed());
        checkEntitiesHealthy(app, entity);
        
        managementContext.getRebindManager().forcePersistNow(true, null);
        log.info("Test deliberately doing unnecessary extra promoteToMaster");
        ha.promoteToMaster();
        
        log.info("Entities managed are: "+managementContext.getEntityManager().getEntities());
        Collection<Location> lle = entity.getLocations();
        log.info("Locs at entity(old) are: "+lle);
        log.info("                   objs: "+identities(lle));
        // check entities -- the initial-full promotion previously re-created items, 
        // and plugged them in as children, but only managed the roots
        checkEntitiesHealthy(app, entity);
        
        // assert what's in the location manager is accurate
        Collection<Location> llmm = managementContext.getLocationManager().getLocations();
        log.info("Locs managed are: "+llmm);
        log.info("            objs: "+identities(llmm));
        Assert.assertEquals(llmm, lm);
        SshMachineLocation ll2a = Iterables.getOnlyElement(Iterables.filter(llmm, SshMachineLocation.class));
        Assert.assertEquals(ll2a.getConfig(TestEntity.CONF_NAME), "sample1");
        Assert.assertNotNull(ll2a.getParent(), "Parent not set after dodgy promoteToMaster");
        Assert.assertEquals(ll2a.getParent().getConfig(TestEntity.CONF_NAME), "sample1");
        
        // and what's in the location manager is accurate
        Entity ee = (Entity)managementContext.lookup(entity.getId());
        Collection<Location> llee = ee.getLocations();
        log.info("Locs at entity(lookup) are: "+llee);
        log.info("                      objs: "+identities(llee));
        SshMachineLocation ll2b = Iterables.getOnlyElement(Iterables.filter(llee, SshMachineLocation.class));
        Assert.assertEquals(ll2b.getConfig(TestEntity.CONF_NAME), "sample1");
        Assert.assertNotNull(ll2b.getParent(), "Parent not set after dodgy promoteToMaster");
        Assert.assertEquals(ll2b.getParent().getConfig(TestEntity.CONF_NAME), "sample1");
    }

    private void checkEntitiesHealthy(TestApplication app, TestEntity entity) {
        Assert.assertNotNull(app.getManagementSupport().getManagementContext());
        Assert.assertTrue( app.getManagementSupport().getManagementContext().isRunning() );
        
        Assert.assertNotNull(entity.getManagementSupport().getManagementContext());
        Assert.assertNotNull( ((EntityInternal)app.getChildren().iterator().next()).getManagementSupport().getManagementContext() );
        Assert.assertTrue( ((EntityInternal)app.getChildren().iterator().next()).getManagementSupport().isDeployed());
        Assert.assertTrue( ((EntityInternal)app.getChildren().iterator().next()).getManagementSupport().getManagementContext() instanceof LocalManagementContext );
    }

    @Test(groups="Integration", invocationCount=50)
    public void testLocationsStillManagedCorrectlyAfterDoublePromotionManyTimes() throws NoMachinesAvailableException {
        testLocationsStillManagedCorrectlyAfterDoublePromotion();
    }
    
    private List<String> identities(Collection<?> objs) {
        List<String> result = MutableList.of();
        for (Object obj: objs)
            result.add(Integer.toHexString(System.identityHashCode(obj)));
        return result;
    }
    
}

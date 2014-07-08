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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.Group;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindEnricherTest.MyEnricher;
import brooklyn.location.Location;
import brooklyn.location.basic.Locations;
import brooklyn.mementos.BrooklynMementoManifest;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class RebindPolicyTest extends RebindTestFixtureWithApp {

    /*
     * FIXME Need to decide what to do about policy mementos and restoring.
     * Lots of places register anonymous inner class policies.
     * (e.g. AbstractController registering a AbstractMembershipTrackingPolicy)
     * Also, the entity constructor often re-creates the policy.
     * 
     * See RebindManagerImpl.CheckpointingChangeListener.onChanged(Entity) and
     * MementosGenerator.newEntityMementoBuilder()
     */
    
    @Test
    public void testRestoresSimplePolicyFromConstructor() throws Exception {
        MyPolicy origPolicy = new MyPolicy(MutableMap.of("myfield", "myFieldVal", "myconfigkey", "myConfigVal"));
        origApp.addPolicy(origPolicy);
        runRestoresSimplePolicy();
    }

    @Test
    public void testRestoresDeprecatedPolicyFromConstructorWithoutNoArgs() throws Exception {
        MyPolicyWithoutNoArgConstructor origPolicy = new MyPolicyWithoutNoArgConstructor(MutableMap.of("myfield", "myFieldVal", "myconfigkey", "myConfigVal"));
        origApp.addPolicy(origPolicy);
        runRestoresSimplePolicy();
    }

    @Test
    public void testRestoresSimplePolicyFromPolicySpec() throws Exception {
        origApp.addPolicy(PolicySpec.create(MyPolicy.class)
                .configure("myfield", "myFieldVal")
                .configure(MyPolicy.MY_CONFIG, "myConfigVal"));
        runRestoresSimplePolicy();
    }
    
    protected void runRestoresSimplePolicy() throws Exception {
        MyPolicy origPolicy = (MyPolicy) Iterables.getOnlyElement(origApp.getPolicies());
        assertTrue(origPolicy.isRunning());
        assertTrue(origPolicy.initCalled);
        assertFalse(origPolicy.rebindCalled);
        
        TestApplication newApp = rebind();
        MyPolicy newPolicy = (MyPolicy) Iterables.getOnlyElement(newApp.getPolicies());
        
        assertEquals(newPolicy.myfield, "myFieldVal");
        assertEquals(newPolicy.getConfig(MyPolicy.MY_CONFIG), "myConfigVal");
        assertTrue(newPolicy.isRunning());
        assertFalse(newPolicy.initCalled);
        assertTrue(newPolicy.rebindCalled);
    }

    @Test
    public void testRestoresConfig() throws Exception {
        origApp.addPolicy(PolicySpec.create(MyPolicy.class)
                .configure(MyPolicy.MY_CONFIG_WITH_SETFROMFLAG_NO_SHORT_NAME, "myVal for with setFromFlag noShortName")
                .configure(MyPolicy.MY_CONFIG_WITH_SETFROMFLAG_WITH_SHORT_NAME, "myVal for setFromFlag withShortName")
                .configure(MyPolicy.MY_CONFIG_WITHOUT_SETFROMFLAG, "myVal for witout setFromFlag"));

        newApp = (TestApplication) rebind();
        MyPolicy newPolicy = (MyPolicy) Iterables.getOnlyElement(newApp.getPolicies());
        
        assertEquals(newPolicy.getConfig(MyPolicy.MY_CONFIG_WITH_SETFROMFLAG_NO_SHORT_NAME), "myVal for with setFromFlag noShortName");
        assertEquals(newPolicy.getConfig(MyPolicy.MY_CONFIG_WITH_SETFROMFLAG_WITH_SHORT_NAME), "myVal for setFromFlag withShortName");
        assertEquals(newPolicy.getConfig(MyPolicy.MY_CONFIG_WITHOUT_SETFROMFLAG), "myVal for witout setFromFlag");
    }

    @Test
    public void testExpungesOnEntityUnmanaged() throws Exception {
        Location loc = origManagementContext.getLocationRegistry().resolve("localhost");
        TestEntity entity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
        MyPolicy policy = entity.addPolicy(PolicySpec.create(MyPolicy.class));
        MyEnricher enricher = entity.addEnricher(EnricherSpec.create(MyEnricher.class));

        RebindTestUtils.waitForPersisted(origApp);

        Entities.unmanage(entity);
        Locations.unmanage(loc);
        RebindTestUtils.waitForPersisted(origApp);
        
        BrooklynMementoManifest manifest = loadMementoManifest();
        assertFalse(manifest.getEntityIdToType().containsKey(entity.getId()));
        assertFalse(manifest.getPolicyIdToType().containsKey(policy.getId()));
        assertFalse(manifest.getEnricherIdToType().containsKey(enricher.getId()));
        assertFalse(manifest.getLocationIdToType().containsKey(loc.getId()));
    }

    @Test
    public void testExpungesOnPolicyRemoved() throws Exception {
        TestEntity entity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
        MyPolicy policy = entity.addPolicy(PolicySpec.create(MyPolicy.class));
        MyEnricher enricher = entity.addEnricher(EnricherSpec.create(MyEnricher.class));

        RebindTestUtils.waitForPersisted(origApp);

        entity.removePolicy(policy);
        entity.removeEnricher(enricher);
        RebindTestUtils.waitForPersisted(origApp);
        
        BrooklynMementoManifest manifest = loadMementoManifest();
        assertFalse(manifest.getPolicyIdToType().containsKey(policy.getId()));
        assertFalse(manifest.getEnricherIdToType().containsKey(enricher.getId()));
    }

    @Test
    public void testReboundConfigDoesNotContainId() throws Exception {
        MyPolicy policy = origApp.addPolicy(PolicySpec.create(MyPolicy.class));
        
        newApp = (TestApplication) rebind();
        MyPolicy newPolicy = (MyPolicy) Iterables.getOnlyElement(newApp.getPolicies());

        assertNull(newPolicy.getConfig(ConfigKeys.newStringConfigKey("id")));
        assertEquals(newPolicy.getId(), policy.getId());
    }
    
    @Test
    public void testReconfigurePolicyPersistsChange() throws Exception {
        MyPolicyReconfigurable policy = origApp.addPolicy(PolicySpec.create(MyPolicyReconfigurable.class)
                .configure(MyPolicyReconfigurable.MY_CONFIG, "oldval"));
        policy.setConfig(MyPolicyReconfigurable.MY_CONFIG, "newval");
        
        newApp = (TestApplication) rebind();
        MyPolicyReconfigurable newPolicy = (MyPolicyReconfigurable) Iterables.getOnlyElement(newApp.getPolicies());

        assertEquals(newPolicy.getConfig(MyPolicyReconfigurable.MY_CONFIG), "newval");
    }

    @Test
    public void testIsRebinding() throws Exception {
        origApp.addPolicy(PolicySpec.create(PolicyChecksIsRebinding.class));

        newApp = (TestApplication) rebind();
        PolicyChecksIsRebinding newPolicy = (PolicyChecksIsRebinding) Iterables.getOnlyElement(newApp.getPolicies());

        assertTrue(newPolicy.isRebindingValWhenRebinding());
        assertFalse(newPolicy.isRebinding());
    }
    
    // Previously, policy+enricher was added to entity as part of entity.reconstitute, so other entities might not
    // have been initialised and the relationships not set. If a policy immediately looked at entity's children or
    // at another entity, then it might find those entities' state uninitialised.
    //
    // Longer term, we may want to force policies+enrichers to be inactive until the entity really is managed.
    // However, currently some policies inject an onEvent message during their `setEntity` method (because 
    // their subscription does not give them the current value - only changed values. Doing that sudo-onEvent is
    // a bad idea because even on normal startup the entity might still be in its init method, so we shouldn't be
    // kicking off actions at that point.
    @Test
    public void testPolicyAddedWhenEntityRelationshipsSet() throws Exception {
        BasicGroup origGroup = origApp.createAndManageChild(EntitySpec.create(BasicGroup.class));
        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
        origGroup.addMember(origEntity);
        
        EnricherChecksEntityHierarchy origEnricher = origApp.addEnricher(EnricherSpec.create(EnricherChecksEntityHierarchy.class));
        PolicyChecksEntityHierarchy origPolicy = origApp.addPolicy(PolicySpec.create(PolicyChecksEntityHierarchy.class));
        assertTrue(origEnricher.success);
        assertTrue(origPolicy.success);
        
        newApp = (TestApplication) rebind();
        EnricherChecksEntityHierarchy newEnricher = (EnricherChecksEntityHierarchy) Iterables.getOnlyElement(newApp.getEnrichers());
        PolicyChecksEntityHierarchy newPolicy = (PolicyChecksEntityHierarchy) Iterables.getOnlyElement(newApp.getPolicies());

        assertTrue(newEnricher.success);
        assertTrue(newPolicy.success);
    }
    public static class PolicyChecksEntityHierarchy extends AbstractPolicy {
        transient volatile boolean success;
        @Override
        public void setEntity(EntityLocal entity) {
            super.setEntity(entity);
            assertTrue(entity instanceof TestApplication);
            assertEquals(entity.getChildren().size(), 2);
            assertEquals(((Group)Iterables.find(entity.getChildren(), Predicates.instanceOf(Group.class))).getMembers().size(), 1);
            success = true;
        }
    }
    public static class EnricherChecksEntityHierarchy extends AbstractEnricher {
        transient volatile boolean success;
        @Override
        public void setEntity(EntityLocal entity) {
            super.setEntity(entity);
            assertTrue(entity instanceof TestApplication);
            assertEquals(entity.getChildren().size(), 2);
            assertEquals(((Group)Iterables.find(entity.getChildren(), Predicates.instanceOf(Group.class))).getMembers().size(), 1);
            success = true;
        }
    }
    
    public static class PolicyChecksIsRebinding extends AbstractPolicy {
        boolean isRebindingValWhenRebinding;
        
        public boolean isRebindingValWhenRebinding() {
            return isRebindingValWhenRebinding;
        }
        @Override public boolean isRebinding() {
            return super.isRebinding();
        }
        @Override public void rebind() {
            super.rebind();
            isRebindingValWhenRebinding = isRebinding();
        }
    }
    
    public static class MyPolicy extends AbstractPolicy {
        public static final ConfigKey<String> MY_CONFIG = ConfigKeys.newStringConfigKey("myconfigkey");
        
        @SetFromFlag
        public static final ConfigKey<String> MY_CONFIG_WITH_SETFROMFLAG_NO_SHORT_NAME = ConfigKeys.newStringConfigKey("myconfig.withSetfromflag.noShortName");

        @SetFromFlag("myConfigWithSetFromFlagWithShortName")
        public static final ConfigKey<String> MY_CONFIG_WITH_SETFROMFLAG_WITH_SHORT_NAME = ConfigKeys.newStringConfigKey("myconfig.withSetfromflag.withShortName");

        public static final ConfigKey<String> MY_CONFIG_WITHOUT_SETFROMFLAG = ConfigKeys.newStringConfigKey("myconfig.withoutSetfromflag");

        @SetFromFlag
        String myfield;

        @SuppressWarnings("unused")
        private final Object dummy = new Object(); // so not serializable
        
        public volatile boolean initCalled;
        public volatile boolean rebindCalled;
        
        public MyPolicy() {
        }
        
        public MyPolicy(Map<?,?> flags) {
            super(flags);
        }
        
        @Override
        public void init() {
            super.init();
            initCalled = true;
        }
        
        @Override
        public void rebind() {
            super.rebind();
            rebindCalled = true;
        }
    }
    
    public static class MyPolicyWithoutNoArgConstructor extends MyPolicy {
        public MyPolicyWithoutNoArgConstructor(Map<?,?> flags) {
            super(flags);
        }
    }
    
    public static class MyPolicyReconfigurable extends AbstractPolicy {
        public static final ConfigKey<String> MY_CONFIG = ConfigKeys.newStringConfigKey("myconfig");
        
        public MyPolicyReconfigurable() {
            super();
        }
        
        @Override
        protected <T> void doReconfigureConfig(ConfigKey<T> key, T val) {
            if (MY_CONFIG.equals(key)) {
                // we'd do here whatever reconfig meant; caller will set actual new val
            } else {
                super.doReconfigureConfig(key, val);
            }
        }
    }
}

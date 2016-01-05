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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.rebind.mementos.BrooklynMementoManifest;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.enricher.AbstractEnricher;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.core.mgmt.rebind.RebindEnricherTest.MyEnricher;
import org.apache.brooklyn.core.policy.AbstractPolicy;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
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
        origApp.policies().add(origPolicy);
        runRestoresSimplePolicy();
    }

    @Test
    public void testRestoresDeprecatedPolicyFromConstructorWithoutNoArgs() throws Exception {
        MyPolicyWithoutNoArgConstructor origPolicy = new MyPolicyWithoutNoArgConstructor(MutableMap.of("myfield", "myFieldVal", "myconfigkey", "myConfigVal"));
        origApp.policies().add(origPolicy);
        runRestoresSimplePolicy();
    }

    @Test
    public void testRestoresSimplePolicyFromPolicySpec() throws Exception {
        origApp.policies().add(PolicySpec.create(MyPolicy.class)
                .configure("myfield", "myFieldVal")
                .configure(MyPolicy.MY_CONFIG, "myConfigVal"));
        runRestoresSimplePolicy();
    }
    
    protected void runRestoresSimplePolicy() throws Exception {
        MyPolicy origPolicy = (MyPolicy) Iterables.getOnlyElement(origApp.policies());
        assertTrue(origPolicy.isRunning());
        assertTrue(origPolicy.initCalled);
        assertFalse(origPolicy.rebindCalled);
        
        newApp = rebind();
        MyPolicy newPolicy = (MyPolicy) Iterables.getOnlyElement(newApp.policies());
        
        assertEquals(newPolicy.myfield, "myFieldVal");
        assertEquals(newPolicy.getConfig(MyPolicy.MY_CONFIG), "myConfigVal");
        assertTrue(newPolicy.isRunning());
        assertFalse(newPolicy.initCalled);
        assertTrue(newPolicy.rebindCalled);
    }

    @Test
    public void testRestoresConfig() throws Exception {
        origApp.policies().add(PolicySpec.create(MyPolicy.class)
                .displayName("My Policy")
                .uniqueTag("tagU")
                .tag("tag1").tag("tag2")
                .configure(MyPolicy.MY_CONFIG_WITH_SETFROMFLAG_NO_SHORT_NAME, "myVal for with setFromFlag noShortName")
                .configure(MyPolicy.MY_CONFIG_WITH_SETFROMFLAG_WITH_SHORT_NAME, "myVal for setFromFlag withShortName")
                .configure(MyPolicy.MY_CONFIG_WITHOUT_SETFROMFLAG, "myVal for witout setFromFlag"));

        newApp = rebind();
        MyPolicy newPolicy = (MyPolicy) Iterables.getOnlyElement(newApp.policies());
        
        assertEquals(newPolicy.getDisplayName(), "My Policy");
        
        assertEquals(newPolicy.getUniqueTag(), "tagU");
        assertEquals(newPolicy.tags().getTags(), MutableSet.of("tagU", "tag1", "tag2"));
        
        assertEquals(newPolicy.getConfig(MyPolicy.MY_CONFIG_WITH_SETFROMFLAG_NO_SHORT_NAME), "myVal for with setFromFlag noShortName");
        assertEquals(newPolicy.getConfig(MyPolicy.MY_CONFIG_WITH_SETFROMFLAG_WITH_SHORT_NAME), "myVal for setFromFlag withShortName");
        assertEquals(newPolicy.getConfig(MyPolicy.MY_CONFIG_WITHOUT_SETFROMFLAG), "myVal for witout setFromFlag");
    }

    @Test
    public void testExpungesOnEntityUnmanaged() throws Exception {
        Location loc = origManagementContext.getLocationRegistry().resolve("localhost");
        TestEntity entity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
        MyPolicy policy = entity.policies().add(PolicySpec.create(MyPolicy.class));
        MyEnricher enricher = entity.enrichers().add(EnricherSpec.create(MyEnricher.class));

        RebindTestUtils.waitForPersisted(origApp);

        Entities.unmanage(entity);
        Locations.unmanage(loc);
        RebindTestUtils.waitForPersisted(origApp);
        
        BrooklynMementoManifest manifest = loadMementoManifest();
        assertFalse(manifest.getEntityIdToManifest().containsKey(entity.getId()));
        assertFalse(manifest.getPolicyIdToType().containsKey(policy.getId()));
        assertFalse(manifest.getEnricherIdToType().containsKey(enricher.getId()));
        assertFalse(manifest.getLocationIdToType().containsKey(loc.getId()));
    }

    @Test
    public void testExpungesOnPolicyRemoved() throws Exception {
        TestEntity entity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
        MyPolicy policy = entity.policies().add(PolicySpec.create(MyPolicy.class));
        MyEnricher enricher = entity.enrichers().add(EnricherSpec.create(MyEnricher.class));

        RebindTestUtils.waitForPersisted(origApp);

        entity.policies().remove(policy);
        entity.enrichers().remove(enricher);
        RebindTestUtils.waitForPersisted(origApp);
        
        BrooklynMementoManifest manifest = loadMementoManifest();
        assertFalse(manifest.getPolicyIdToType().containsKey(policy.getId()));
        assertFalse(manifest.getEnricherIdToType().containsKey(enricher.getId()));
    }

    @Test
    public void testReboundConfigDoesNotContainId() throws Exception {
        MyPolicy policy = origApp.policies().add(PolicySpec.create(MyPolicy.class));
        
        newApp = rebind();
        MyPolicy newPolicy = (MyPolicy) Iterables.getOnlyElement(newApp.policies());

        assertNull(newPolicy.getConfig(ConfigKeys.newStringConfigKey("id")));
        assertEquals(newPolicy.getId(), policy.getId());
    }
    
    @Test
    public void testReconfigurePolicyPersistsChange() throws Exception {
        MyPolicyReconfigurable policy = origApp.policies().add(PolicySpec.create(MyPolicyReconfigurable.class)
                .configure(MyPolicyReconfigurable.MY_CONFIG, "oldval"));
        policy.config().set(MyPolicyReconfigurable.MY_CONFIG, "newval");
        
        newApp = rebind();
        MyPolicyReconfigurable newPolicy = (MyPolicyReconfigurable) Iterables.getOnlyElement(newApp.policies());

        assertEquals(newPolicy.getConfig(MyPolicyReconfigurable.MY_CONFIG), "newval");
    }

    @Test
    public void testIsRebinding() throws Exception {
        origApp.policies().add(PolicySpec.create(PolicyChecksIsRebinding.class));

        newApp = rebind();
        PolicyChecksIsRebinding newPolicy = (PolicyChecksIsRebinding) Iterables.getOnlyElement(newApp.policies());

        assertTrue(newPolicy.isRebindingValWhenRebinding());
        assertFalse(newPolicy.isRebinding());
    }
    
    @Test
    public void testPolicyTags() throws Exception {
        Policy origPolicy = origApp.policies().add(PolicySpec.create(MyPolicy.class));
        origPolicy.tags().addTag("foo");
        origPolicy.tags().addTag(origApp);

        newApp = rebind();
        Policy newPolicy = Iterables.getOnlyElement(newApp.policies());

        Asserts.assertEqualsIgnoringOrder(newPolicy.tags().getTags(), ImmutableSet.of("foo", newApp));
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
        
        EnricherChecksEntityHierarchy origEnricher = origApp.enrichers().add(EnricherSpec.create(EnricherChecksEntityHierarchy.class));
        PolicyChecksEntityHierarchy origPolicy = origApp.policies().add(PolicySpec.create(PolicyChecksEntityHierarchy.class));
        assertTrue(origEnricher.success);
        assertTrue(origPolicy.success);
        
        newApp = (TestApplication) rebind();
        EnricherChecksEntityHierarchy newEnricher = (EnricherChecksEntityHierarchy) Iterables.getOnlyElement(newApp.enrichers());
        PolicyChecksEntityHierarchy newPolicy = (PolicyChecksEntityHierarchy) Iterables.getOnlyElement(newApp.policies());

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

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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.io.File;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.api.entity.rebind.RebindManager;
import org.apache.brooklyn.api.entity.rebind.RebindManager.RebindFailureMode;
import org.apache.brooklyn.api.entity.trait.Identifiable;
import org.apache.brooklyn.api.event.AttributeSensor;
import org.apache.brooklyn.api.management.EntityManager;
import org.apache.brooklyn.api.management.ManagementContext;
import org.apache.brooklyn.api.policy.Enricher;
import org.apache.brooklyn.api.policy.EnricherSpec;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.core.management.internal.LocalManagementContext;
import org.apache.brooklyn.core.util.flags.SetFromFlag;
import org.apache.brooklyn.test.entity.LocalManagementContextForTests;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigMap;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityFunctions;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.rebind.RebindEntityTest.MyEntity;
import brooklyn.entity.rebind.RebindEntityTest.MyEntityImpl;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.os.Os;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class RebindFailuresTest extends RebindTestFixtureWithApp {

    @Test
    public void testFailureGeneratingMementoStillPersistsOtherEntities() throws Exception {
        MyEntity origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class));
        MyEntity origFailingE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class)
                .impl(MyEntityFailingImpl.class)
                .configure(MyEntityFailingImpl.FAIL_ON_GENERATE_MEMENTO, true));
        
        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), EntityPredicates.idEqualTo(origE.getId()));
        Optional<Entity> newFailingE = Iterables.tryFind(newApp.getChildren(), EntityPredicates.idEqualTo(origFailingE.getId()));
        
        // Expect origFailingE to never have been persisted, but origE to have worked
        assertNotNull(newE);
        assertFalse(newFailingE.isPresent(), "newFailedE="+newFailingE);
    }

    @Test(invocationCount=10, groups="Integration")
    public void testFailureGeneratingMementoStillPersistsOtherEntitiesRepeatedly() throws Exception {
        testFailureGeneratingMementoStillPersistsOtherEntities();
    }

    @Test
    public void testFailureRebindingEntityWhenFailAtEnd() throws Exception {
        RebindFailureMode danglingRefFailureMode = RebindManager.RebindFailureMode.CONTINUE;
        RebindFailureMode rebindFailureMode = RebindManager.RebindFailureMode.FAIL_AT_END;
        
        MyEntity origFailingE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class)
                .impl(MyEntityFailingImpl.class)
                .configure(MyEntityFailingImpl.FAIL_ON_REBIND, true));
        
        ManagementContext newManagementContext = LocalManagementContextForTests.newInstance();
        EntityManager newEntityManager = newManagementContext.getEntityManager();
        RecordingRebindExceptionHandler exceptionHandler = new RecordingRebindExceptionHandler(danglingRefFailureMode, rebindFailureMode);
        try {
            newApp = rebind(RebindOptions.create().newManagementContext(newManagementContext).exceptionHandler(exceptionHandler));
            fail();
        } catch (Exception e) {
            assertFailureRebindingError(e);
            Assert.assertTrue(e.toString().toLowerCase().contains("rebinding entity"), "Wrong error: "+e);
        }

        // exception handler should have been told about failure
        assertEquals(toIds(exceptionHandler.rebindFailures.keySet()), ImmutableSet.of(origFailingE.getId()));

        // Expect that on failure will have continued with rebind, and then report all problems
        assertEquals(toIds(newEntityManager.getEntities()), ImmutableSet.of(origApp.getId(), origFailingE.getId()));
    }
    
    @Test
    public void testFailureRebindingEntityWhenFailFast() throws Exception {
        RebindFailureMode danglingRefFailureMode = RebindManager.RebindFailureMode.CONTINUE;
        RebindFailureMode rebindFailureMode = RebindManager.RebindFailureMode.FAIL_FAST;
        
        MyEntity origFailingE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class)
                .impl(MyEntityFailingImpl.class)
                .configure(MyEntityFailingImpl.FAIL_ON_REBIND, true));
        
        ManagementContext newManagementContext = LocalManagementContextForTests.newInstance();
        EntityManager newEntityManager = newManagementContext.getEntityManager();
        RecordingRebindExceptionHandler exceptionHandler = new RecordingRebindExceptionHandler(danglingRefFailureMode, rebindFailureMode);
        try {
            newApp = rebind(RebindOptions.create().newManagementContext(newManagementContext).exceptionHandler(exceptionHandler));
            fail();
        } catch (Exception e) {
            assertFailureRebindingError(e);
            Assert.assertTrue(e.toString().toLowerCase().contains("rebinding entity"), "Wrong error: "+e);
        }

        // exception handler should have been told about failure
        assertEquals(toIds(exceptionHandler.rebindFailures.keySet()), ImmutableSet.of(origFailingE.getId()));
        
        // entities will not have been managed
        assertEquals(toIds(newEntityManager.getEntities()), ImmutableSet.of());
    }
    
    @Test
    public void testFailureRebindingEntityWhenContinue() throws Exception {
        RebindFailureMode danglingRefFailureMode = RebindManager.RebindFailureMode.CONTINUE;
        RebindFailureMode rebindFailureMode = RebindManager.RebindFailureMode.CONTINUE;
        
        MyEntity origFailingE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class)
                .impl(MyEntityFailingImpl.class)
                .configure(MyEntityFailingImpl.FAIL_ON_REBIND, true));
        
        ManagementContext newManagementContext = LocalManagementContextForTests.newInstance();
        EntityManager newEntityManager = newManagementContext.getEntityManager();
        RecordingRebindExceptionHandler exceptionHandler = new RecordingRebindExceptionHandler(danglingRefFailureMode, rebindFailureMode);
        newApp = rebind(RebindOptions.create().newManagementContext(newManagementContext).exceptionHandler(exceptionHandler));

        // exception handler should have been told about failure
        assertEquals(toIds(exceptionHandler.rebindFailures.keySet()), ImmutableSet.of(origFailingE.getId()));
        
        // TODO How should brooklyn indicate that this entity's rebind failed? What can we assert?
        assertEquals(toIds(newEntityManager.getEntities()), ImmutableSet.of(origApp.getId(), origFailingE.getId()));
    }
    
    @Test
    public void testFailureRebindingBecauseDirectoryCorrupt() throws Exception {
        RebindFailureMode danglingRefFailureMode = RebindManager.RebindFailureMode.CONTINUE;
        RebindFailureMode rebindFailureMode = RebindManager.RebindFailureMode.FAIL_AT_END;
        
        origManagementContext.getRebindManager().stopPersistence();
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
        File entitiesDir = Os.mkdirs(new File(mementoDir, "entities"));
        Files.write("invalid text", new File(entitiesDir, "mycorruptfile"), Charsets.UTF_8);
        
        LocalManagementContext newManagementContext = LocalManagementContextForTests.newInstance();
        RecordingRebindExceptionHandler exceptionHandler = new RecordingRebindExceptionHandler(danglingRefFailureMode, rebindFailureMode);
        try {
            newApp = rebind(RebindOptions.create().newManagementContext(newManagementContext).exceptionHandler(exceptionHandler));
            fail();
        } catch (Exception e) {
            assertFailureRebindingError(e);
        }
        
        // exception handler should have been told about failure
        assertFalse(exceptionHandler.loadMementoFailures.isEmpty(), "exceptions="+exceptionHandler.loadMementoFailures);
    }

    protected void assertFailureRebindingError(Exception e) {
        if (e.toString().toLowerCase().matches(".*(problem|failure)(s?) rebinding.*")) { 
            // expected
        } else {
            throw Exceptions.propagate(e);
        }
    }

    @Test
    public void testRebindWithFailingPolicyContinuesWithoutPolicy() throws Exception {
        origApp.addPolicy(PolicySpec.create(MyPolicyFailingImpl.class)
                .configure(MyPolicyFailingImpl.FAIL_ON_REBIND, true));
        
        newApp = rebind();
        
        Optional<Policy> newPolicy = Iterables.tryFind(newApp.getPolicies(), Predicates.instanceOf(MyPolicyFailingImpl.class));
        assertFalse(newPolicy.isPresent(), "policy="+newPolicy);
    }

    @Test
    public void testRebindWithFailingEnricherContinuesWithoutEnricher() throws Exception {
        origApp.addEnricher(EnricherSpec.create(MyEnricherFailingImpl.class)
                .configure(MyEnricherFailingImpl.FAIL_ON_REBIND, true));
        
        newApp = rebind();
        
        Optional<Enricher> newEnricher = Iterables.tryFind(newApp.getEnrichers(), Predicates.instanceOf(MyEnricherFailingImpl.class));
        assertFalse(newEnricher.isPresent(), "enricher="+newEnricher);
    }

    private Set<String> toIds(Iterable<? extends Identifiable> instances) {
        return ImmutableSet.copyOf(Iterables.transform(instances, EntityFunctions.id()));
    }
    
    public static class MyPolicyFailingImpl extends AbstractPolicy {
        @SetFromFlag("failOnGenerateMemento")
        public static final ConfigKey<Boolean> FAIL_ON_GENERATE_MEMENTO = ConfigKeys.newBooleanConfigKey("failOnGenerateMemento", "Whether to throw exception when generating memento", false);
        
        @SetFromFlag("failOnRebind")
        public static final ConfigKey<Boolean> FAIL_ON_REBIND = ConfigKeys.newBooleanConfigKey("failOnRebind", "Whether to throw exception when rebinding", false);
        
        @Override
        public ConfigMap getConfigMap() {
            if (Boolean.TRUE.equals(getConfig(FAIL_ON_GENERATE_MEMENTO))) {
                throw new RuntimeException("Simulating failure in "+this+", which will cause memento-generation to fail");
            } else {
                return super.getConfigMap();
            }
        }
        
        @Override
        public void rebind() {
            if (Boolean.TRUE.equals(getConfig(FAIL_ON_REBIND))) {
                throw new RuntimeException("Simulating failure in "+this+", which will cause rebind to fail");
            }
        }
    }

    public static class MyEnricherFailingImpl extends AbstractEnricher {
        @SetFromFlag("failOnGenerateMemento")
        public static final ConfigKey<Boolean> FAIL_ON_GENERATE_MEMENTO = ConfigKeys.newBooleanConfigKey("failOnGenerateMemento", "Whether to throw exception when generating memento", false);
        
        @SetFromFlag("failOnRebind")
        public static final ConfigKey<Boolean> FAIL_ON_REBIND = ConfigKeys.newBooleanConfigKey("failOnRebind", "Whether to throw exception when rebinding", false);
        
        @Override
        public ConfigMap getConfigMap() {
            if (Boolean.TRUE.equals(getConfig(FAIL_ON_GENERATE_MEMENTO))) {
                throw new RuntimeException("Simulating failure in "+this+", which will cause memento-generation to fail");
            } else {
                return super.getConfigMap();
            }
        }
        
        @Override
        public void rebind() {
            if (Boolean.TRUE.equals(getConfig(FAIL_ON_REBIND))) {
                throw new RuntimeException("Simulating failure in "+this+", which will cause rebind to fail");
            }
        }
    }

    public static class MyEntityFailingImpl extends MyEntityImpl implements MyEntity {
        @SetFromFlag("failOnGenerateMemento")
        public static final ConfigKey<Boolean> FAIL_ON_GENERATE_MEMENTO = ConfigKeys.newBooleanConfigKey("failOnGenerateMemento", "Whether to throw exception when generating memento", false);
        
        @SetFromFlag("failOnRebind")
        public static final ConfigKey<Boolean> FAIL_ON_REBIND = ConfigKeys.newBooleanConfigKey("failOnRebind", "Whether to throw exception when rebinding", false);
        
        @SuppressWarnings("rawtypes")
        @Override
        public Map<AttributeSensor, Object> getAllAttributes() {
            if (Boolean.TRUE.equals(getConfig(FAIL_ON_GENERATE_MEMENTO))) {
                throw new RuntimeException("Simulating failure in "+this+", which will cause memento-generation to fail");
            } else {
                return super.getAllAttributes();
            }
        }
        
        @Override
        public void rebind() {
            if (Boolean.TRUE.equals(getConfig(FAIL_ON_REBIND))) {
                throw new RuntimeException("Simulating failure in "+this+", which will cause rebind to fail");
            }
        }
    }
}

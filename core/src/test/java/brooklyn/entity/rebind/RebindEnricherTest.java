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

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.enricher.Enrichers;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.policy.Enricher;
import brooklyn.policy.EnricherSpec;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.StringFunctions;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class RebindEnricherTest extends RebindTestFixtureWithApp {

    private static final Logger log = LoggerFactory.getLogger(RebindEnricherTest.class);
    
    public static AttributeSensor<String> METRIC1 = Sensors.newStringSensor("RebindEnricherTest.metric1");
    public static AttributeSensor<String> METRIC2 = Sensors.newStringSensor("RebindEnricherTest.metric2");
    
    private DynamicCluster origCluster;
    private TestEntity origEntity;
    private SimulatedLocation origLoc;

    @Override
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        origCluster = origApp.createAndManageChild(EntitySpec.create(DynamicCluster.class).configure("memberSpec", EntitySpec.create(TestEntity.class)));
        origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
        origLoc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
    }
    
    @Test
    public void testPropagatingEnricher() throws Exception {
        origApp.addEnricher(Enrichers.builder()
                .propagating(METRIC1)
                .from(origEntity)
                .build());
        
        TestApplication newApp = rebind();
        TestEntity newEntity = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));

        newEntity.setAttribute(METRIC1, "myval");
        EntityTestUtils.assertAttributeEqualsEventually(newApp, METRIC1, "myval");
    }

    @Test
    public void testPropagatingAllEnricher() throws Exception {
        origApp.addEnricher(Enrichers.builder()
                .propagatingAll()
                .from(origEntity)
                .build());
        
        TestApplication newApp = rebind();
        TestEntity newEntity = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));

        newEntity.setAttribute(METRIC1, "myval");
        EntityTestUtils.assertAttributeEqualsEventually(newApp, METRIC1, "myval");
    }

    @Test
    public void testPropagatingAsEnricher() throws Exception {
        origApp.addEnricher(Enrichers.builder()
                .propagating(ImmutableMap.of(METRIC1, METRIC2))
                .from(origEntity)
                .build());
        
        TestApplication newApp = rebind();
        TestEntity newEntity = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));

        newEntity.setAttribute(METRIC1, "myval");
        EntityTestUtils.assertAttributeEqualsEventually(newApp, METRIC2, "myval");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCombiningEnricher() throws Exception {
        origApp.addEnricher(Enrichers.builder()
                .combining(METRIC1, METRIC2)
                .from(origEntity)
                .computing(StringFunctions.joiner(","))
                .publishing(METRIC2)
                .build());
        
        TestApplication newApp = rebind();
        TestEntity newEntity = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));

        newEntity.setAttribute(METRIC1, "myval");
        newEntity.setAttribute(METRIC2, "myval2");
        EntityTestUtils.assertAttributeEventually(newApp, METRIC2, Predicates.or(Predicates.equalTo("myval,myval2"), Predicates.equalTo("myval2,myval")));
    }

    @Test
    public void testAggregatingMembersEnricher() throws Exception {
        origApp.start(ImmutableList.of(origLoc));
        origCluster.resize(2);
        
        origApp.addEnricher(Enrichers.builder()
                .aggregating(METRIC1)
                .from(origCluster)
                .fromMembers()
                .computing(StringFunctions.joiner(","))
                .publishing(METRIC2)
                .build());
        
        TestApplication newApp = rebind();
        DynamicCluster newCluster = (DynamicCluster) Iterables.find(newApp.getChildren(), Predicates.instanceOf(DynamicCluster.class));

        int i = 1;
        for (Entity member : newCluster.getMembers()) {
            ((EntityInternal)member).setAttribute(METRIC1, "myval"+(i++));
        }
        EntityTestUtils.assertAttributeEventually(newApp, METRIC2, Predicates.or(Predicates.equalTo("myval1,myval2"), Predicates.equalTo("myval2,myval1")));
    }
    
    @Test
    public void testRestoresConfig() throws Exception {
        origApp.addEnricher(EnricherSpec.create(MyEnricher.class)
                .displayName("My Enricher")
                .uniqueTag("tagU")
                .tag("tag1").tag("tag2")
                .configure(MyEnricher.MY_CONFIG_WITH_SETFROMFLAG_NO_SHORT_NAME, "myVal for with setFromFlag noShortName")
                .configure(MyEnricher.MY_CONFIG_WITH_SETFROMFLAG_WITH_SHORT_NAME, "myVal for setFromFlag withShortName")
                .configure(MyEnricher.MY_CONFIG_WITHOUT_SETFROMFLAG, "myVal for witout setFromFlag"));

        newApp = (TestApplication) rebind();
        MyEnricher newEnricher = (MyEnricher) Iterables.getOnlyElement(newApp.getEnrichers());
        
        assertEquals(newEnricher.getDisplayName(), "My Enricher");
        
        assertEquals(newEnricher.getUniqueTag(), "tagU");
        assertEquals(newEnricher.getTagSupport().getTags(), MutableSet.of("tagU", "tag1", "tag2"));
        
        assertEquals(newEnricher.getConfig(MyEnricher.MY_CONFIG_WITH_SETFROMFLAG_NO_SHORT_NAME), "myVal for with setFromFlag noShortName");
        assertEquals(newEnricher.getConfig(MyEnricher.MY_CONFIG_WITH_SETFROMFLAG_WITH_SHORT_NAME), "myVal for setFromFlag withShortName");
        assertEquals(newEnricher.getConfig(MyEnricher.MY_CONFIG_WITHOUT_SETFROMFLAG), "myVal for witout setFromFlag");
    }

    @Test
    public void testReboundConfigDoesNotContainId() throws Exception {
        MyEnricher policy = origApp.addEnricher(EnricherSpec.create(MyEnricher.class));
        
        newApp = (TestApplication) rebind();
        MyEnricher newEnricher = (MyEnricher) Iterables.getOnlyElement(newApp.getEnrichers());

        assertNull(newEnricher.getConfig(ConfigKeys.newStringConfigKey("id")));
        assertEquals(newEnricher.getId(), policy.getId());
    }

    @Test
    public void testIsRebinding() throws Exception {
        origApp.addEnricher(EnricherSpec.create(EnricherChecksIsRebinding.class));

        newApp = (TestApplication) rebind();
        EnricherChecksIsRebinding newEnricher = (EnricherChecksIsRebinding) Iterables.getOnlyElement(newApp.getEnrichers());

        assertTrue(newEnricher.isRebindingValWhenRebinding());
        assertFalse(newEnricher.isRebinding());
    }
    
    @Test
    public void testPolicyTags() throws Exception {
        Enricher origEnricher = origApp.addEnricher(EnricherSpec.create(MyEnricher.class));
        origEnricher.getTagSupport().addTag("foo");
        origEnricher.getTagSupport().addTag(origApp);

        newApp = rebind();
        Enricher newEnricher = Iterables.getOnlyElement(newApp.getEnrichers());

        Asserts.assertEqualsIgnoringOrder(newEnricher.getTagSupport().getTags(), ImmutableSet.of("foo", newApp));
    }

    public static class EnricherChecksIsRebinding extends AbstractEnricher {
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
    
    public static class MyEnricher extends AbstractEnricher {
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
        
        public MyEnricher() {
        }
        
        public MyEnricher(Map<?,?> flags) {
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
    
    public static class MyEnricherWithoutNoArgConstructor extends MyEnricher {
        public MyEnricherWithoutNoArgConstructor(Map<?,?> flags) {
            super(flags);
        }
    }
    
    public static class MyTestEntityWithEnricher extends TestEntityImpl {
        @Override
        protected void initEnrichers() {
            // don't add default ones
            addEnricher(EnricherSpec.create(MyEnricher.class).uniqueTag("x").tag(Identifiers.makeRandomId(8)));
            addEnricher(EnricherSpec.create(MyEnricher.class));
        }
        @Override
        public void onManagementStarting() {
            super.onManagementStarted();
            addEnricher(EnricherSpec.create(MyEnricher.class).uniqueTag("y").tag(Identifiers.makeRandomId(8)));
        }
        @Override
        public void onManagementStarted() {
            super.onManagementStarted();
            addEnricher(EnricherSpec.create(MyEnricher.class).uniqueTag("z").tag(Identifiers.makeRandomId(8)));
            // all the enrichers above should not be added on rebind, but this one will be:
            addEnricher(EnricherSpec.create(MyEnricher.class).uniqueTag( Identifiers.makeRandomId(8) ).tag(Identifiers.makeRandomId(8)));
        }
    }

    @Test
    public void testEntityCreatingItsEnricherDoesNotReCreateItUnlessUniqueTagDifferent() throws Exception {
        TestEntity e1 = origApp.createAndManageChild(EntitySpec.create(TestEntity.class, MyTestEntityWithEnricher.class));
        Collection<Enricher> e1e = e1.getEnrichers();
        log.info("enrichers1: "+e1e);
        Entities.dumpInfo(e1);
        assertEquals(e1e.size(), 5);

        newApp = (TestApplication) rebind();
        Entity e2 = Iterables.getOnlyElement( Entities.descendants(newApp, EntityPredicates.idEqualTo(e1.getId())) );
        Collection<Enricher> e2e = e2.getEnrichers();
        log.info("enrichers2: "+e2e);
        Entities.dumpInfo(e2);
        
        assertEquals(e2e.size(), e1e.size()+1);
    }

}

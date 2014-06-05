package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
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

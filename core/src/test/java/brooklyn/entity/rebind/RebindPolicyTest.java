package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.test.entity.TestApplication;
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
        
        public transient volatile boolean initCalled;
        public transient volatile boolean rebindCalled;
        
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
        
        // TODO When AbstractPolicy declares rebind; @Override
        public void rebind() {
            // TODO super.rebind();
            rebindCalled = true;
        }
    }
    
    public static class MyPolicyWithoutNoArgConstructor extends MyPolicy {
        public MyPolicyWithoutNoArgConstructor(Map<?,?> flags) {
            super(flags);
        }
    }
}
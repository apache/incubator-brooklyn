package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;

import java.util.Collection;
import java.util.Map;

import org.testng.annotations.Test;

import brooklyn.policy.Policy;
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
    @Test(enabled=false)
    public void testRestoresSimplePolicy() throws Exception {
        MyPolicy origPolicy = new MyPolicy(MutableMap.of("myfield", "myval"));
        origApp.addPolicy(origPolicy);
        
        TestApplication newApp = rebind();
        Collection<Policy> policies = newApp.getPolicies();
        MyPolicy newPolicy = (MyPolicy) Iterables.get(policies, 0);
        
        assertEquals(newPolicy.myfield, origPolicy.myfield);
    }

    public static class MyPolicy extends AbstractPolicy {
        @SetFromFlag
        String myfield;

        @SuppressWarnings("unused")
        private final Object dummy = new Object(); // so not serializable
        
        public MyPolicy() {
        }
        
        public MyPolicy(Map<?,?> flags) {
            super(flags);
        }
    }
}

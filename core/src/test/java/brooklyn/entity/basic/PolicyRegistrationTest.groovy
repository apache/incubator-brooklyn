package brooklyn.entity.basic

import static org.testng.Assert.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.policy.Policy
import brooklyn.policy.basic.AbstractPolicy

class PolicyRegistrationTest {
    private AbstractApplication app
    private AbstractEntity entity
    private Policy policy1
    private Policy policy2

    @BeforeMethod
    public void setUp() {
        app = new AbstractApplication() {}
        entity = new AbstractEntity(owner:app) {}
        policy1 = new AbstractPolicy() {}
        policy2 = new AbstractPolicy() {}
    }
    
    @Test
    public void testGetPoliciesIsInitiallyEmpty() {
        assertEquals entity.policies, []
    }

    @Test(expectedExceptions = [ UnsupportedOperationException.class ])
    public void testGetPoliciesReturnsImmutableCollection() {
        entity.policies.add(policy1);
        fail
    }

    @Test
    public void testAddAndRemovePolicies() {
        entity.addPolicy policy1
        assertEquals entity.policies, [policy1]
        
        entity.addPolicy policy2
        assertEquals entity.policies, [policy1, policy2]
        
        entity.removePolicy policy1
        assertEquals entity.policies, [policy2]
        
        entity.removePolicy policy2
        assertEquals entity.policies, []
    }
    
    @Test
    public void testRemoveAllPolicies() {
        entity.addPolicy policy1
        entity.addPolicy policy2
        entity.removeAllPolicies()
        
        assertEquals entity.policies, []
    }
}

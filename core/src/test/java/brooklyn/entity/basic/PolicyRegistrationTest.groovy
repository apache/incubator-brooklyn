package brooklyn.entity.basic

import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.policy.Policy
import brooklyn.policy.basic.AbstractPolicy

class PolicyRegistrationTest {

    private AbstractApplication app
    private AbstractEntity entity;
    private Policy policy1;
    private Policy policy2;

    @BeforeMethod
    public void setUp() {
        app = new AbstractApplication() {}
        entity = new AbstractEntity(owner:app) {}
        policy1 = new AbstractPolicy() {}
        policy2 = new AbstractPolicy() {}
    }
    
    @Test
    public void testGetPoliciesIsInitiallyEmpty() {
        Assert.assertEquals(entity.getPolicies(), []);
    }

    @Test
    public void testGetPoliciesReturnsImmutableCollection() {
        try {
            entity.getPolicies().add(policy1);
            Assert.fail();
        } catch (UnsupportedOperationException e) {
            // success
        }
    }

    @Test
    public void testAddAndRemovePolicies() {
        entity.addPolicy(policy1);
        Assert.assertEquals(entity.getPolicies(), [policy1]);
        
        entity.addPolicy(policy2);
        Assert.assertEquals(entity.getPolicies(), [policy1, policy2]);
        
        entity.removePolicy(policy1);
        Assert.assertEquals(entity.getPolicies(), [policy2]);
        
        entity.removePolicy(policy2);
        Assert.assertEquals(entity.getPolicies(), []);
    }
}

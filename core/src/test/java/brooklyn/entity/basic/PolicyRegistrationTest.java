package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.policy.Policy;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.test.entity.TestApplicationImpl;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.MutableMap;

import com.google.common.collect.ImmutableList;

public class PolicyRegistrationTest {
    private TestApplicationImpl app;
    private TestEntityImpl entity;
    private Policy policy1;
    private Policy policy2;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = new TestApplicationImpl();
        entity = new TestEntityImpl(MutableMap.of("parent", app));
        policy1 = new AbstractPolicy() {};
        policy2 = new AbstractPolicy() {};
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app);
    }
    
    @Test
    public void testGetPoliciesIsInitiallyEmpty() {
        assertEquals(entity.getPolicies(), ImmutableList.of());
    }

    @Test(expectedExceptions = { UnsupportedOperationException.class })
    public void testGetPoliciesReturnsImmutableCollection() {
        entity.getPolicies().add(policy1);
        fail();
    }

    @Test
    public void testAddAndRemovePolicies() {
        entity.addPolicy(policy1);
        assertEquals(entity.getPolicies(), ImmutableList.of(policy1));
        
        entity.addPolicy(policy2);
        assertEquals(entity.getPolicies(), ImmutableList.of(policy1, policy2));
        
        entity.removePolicy(policy1);
        assertEquals(entity.getPolicies(), ImmutableList.of(policy2));
        
        entity.removePolicy(policy2);
        assertEquals(entity.getPolicies(), ImmutableList.of());
    }
    
    @Test
    public void testRemoveAllPolicies() {
        entity.addPolicy(policy1);
        entity.addPolicy(policy2);
        entity.removeAllPolicies();
        
        assertEquals(entity.getPolicies(), ImmutableList.of());
    }
}

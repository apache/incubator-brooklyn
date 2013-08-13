package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.Collection;
import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class PolicyRegistrationTest {

    private static final int TIMEOUT_MS = 10*1000;
    
    private TestApplication app;
    private TestEntity entity;
    private Policy policy1;
    private Policy policy2;

    private List<PolicyDescriptor> added;
    private List<PolicyDescriptor> removed;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        policy1 = new AbstractPolicy() {};
        policy2 = new AbstractPolicy() {};
        
        added = Lists.newCopyOnWriteArrayList();
        removed = Lists.newCopyOnWriteArrayList();
        
        app.subscribe(entity, AbstractEntity.POLICY_ADDED, new SensorEventListener<PolicyDescriptor>() {
            @Override public void onEvent(SensorEvent<PolicyDescriptor> event) {
                added.add(event.getValue());
            }});
        app.subscribe(entity, AbstractEntity.POLICY_REMOVED, new SensorEventListener<PolicyDescriptor>() {
                @Override public void onEvent(SensorEvent<PolicyDescriptor> event) {
                    removed.add(event.getValue());
                }});
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
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
        assertEqualsEventually(added, ImmutableList.of(new PolicyDescriptor(policy1)));
        
        entity.addPolicy(policy2);
        assertEquals(entity.getPolicies(), ImmutableList.of(policy1, policy2));
        assertEqualsEventually(added, ImmutableList.of(new PolicyDescriptor(policy1), new PolicyDescriptor(policy2)));
        
        entity.removePolicy(policy1);
        assertEquals(entity.getPolicies(), ImmutableList.of(policy2));
        assertEqualsEventually(removed, ImmutableList.of(new PolicyDescriptor(policy1)));
        
        entity.removePolicy(policy2);
        assertEquals(entity.getPolicies(), ImmutableList.of());
        assertEqualsEventually(removed, ImmutableList.of(new PolicyDescriptor(policy1), new PolicyDescriptor(policy2)));
    }
    
    @Test
    public void testRemoveAllPolicies() {
        entity.addPolicy(policy1);
        entity.addPolicy(policy2);
        entity.removeAllPolicies();
        
        assertEquals(entity.getPolicies(), ImmutableList.of());
        assertCollectionEqualsEventually(removed, ImmutableSet.of(new PolicyDescriptor(policy1), new PolicyDescriptor(policy2)));
    }
    
    private <T> void assertEqualsEventually(final T actual, final T expected) {
        TestUtils.assertEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
                @Override public void run() {
                    assertEquals(actual, expected, "actual="+actual);
                }});
    }
    
    // Ignores order of vals in collection, but asserts each same size and same elements 
    private <T> void assertCollectionEqualsEventually(final Collection<? extends T> actual, final Collection<? extends T> expected) {
        TestUtils.assertEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
                @Override public void run() {
                    assertEquals(ImmutableSet.copyOf(actual), ImmutableSet.copyOf(expected), "actual="+actual);
                    assertEquals(actual.size(), expected.size(), "actual="+actual);
                }});
    }
}

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
package org.apache.brooklyn.entity.core;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.util.Collection;
import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityNoEnrichersImpl;
import org.apache.brooklyn.entity.core.AbstractEntity;
import org.apache.brooklyn.entity.lifecycle.PolicyDescriptor;
import org.apache.brooklyn.policy.core.AbstractPolicy;
import org.apache.brooklyn.test.TestUtils;
import org.apache.brooklyn.util.collections.MutableMap;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class PolicyRegistrationTest extends BrooklynAppUnitTestSupport {

    private static final int TIMEOUT_MS = 10*1000;
    
    private TestEntity entity;
    private Policy policy1;
    private Policy policy2;

    private List<PolicyDescriptor> added;
    private List<PolicyDescriptor> removed;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
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
    public void testAddPolicySpec() {
        EntitySpecTest.MyPolicy policy = entity.addPolicy(PolicySpec.create(EntitySpecTest.MyPolicy.class));
        assertNotNull(policy);
        assertEquals(entity.getPolicies(), ImmutableList.of(policy));
        assertEqualsEventually(added, ImmutableList.of(new PolicyDescriptor(policy)));
    }
    
    @Test
    public void testAddEnricherSpec() {
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class, TestEntityNoEnrichersImpl.class));
        EntitySpecTest.MyEnricher enricher = entity2.addEnricher(EnricherSpec.create(EntitySpecTest.MyEnricher.class));
        assertNotNull(enricher);
        assertEquals(entity2.getEnrichers(), ImmutableList.of(enricher));
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

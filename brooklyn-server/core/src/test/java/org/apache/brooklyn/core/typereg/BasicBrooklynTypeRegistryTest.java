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
package org.apache.brooklyn.core.typereg;

import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.core.test.BrooklynMgmtUnitTestSupport;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableSet;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class BasicBrooklynTypeRegistryTest extends BrooklynMgmtUnitTestSupport {

    private BasicBrooklynTypeRegistry registry() {
        return (BasicBrooklynTypeRegistry) mgmt.getTypeRegistry();
    }
    
    private void add(RegisteredType type) {
        add(type, false);
    }
    private void add(RegisteredType type, boolean canForce) {
        registry().addToLocalUnpersistedTypeRegistry(type, canForce);
    }
    
    private final static RegisteredType SAMPLE_TYPE = RegisteredTypes.bean("item.A", "1", new BasicTypeImplementationPlan("ignore", null), String.class);
    private final static RegisteredType SAMPLE_TYPE2 = RegisteredTypes.bean("item.A", "2", new BasicTypeImplementationPlan("ignore", null), String.class);
    
    @Test
    public void testAddAndGet() {
        Assert.assertFalse( Iterables.contains(registry().getAll(), SAMPLE_TYPE) );
        Assert.assertNull( registry().get(SAMPLE_TYPE.getSymbolicName(), SAMPLE_TYPE.getVersion()) );
        Assert.assertNull( registry().get(SAMPLE_TYPE.getId()) );
        add(SAMPLE_TYPE);
        
        Assert.assertTrue( Iterables.contains(registry().getAll(), SAMPLE_TYPE) );
        Assert.assertEquals( registry().get(SAMPLE_TYPE.getSymbolicName(), SAMPLE_TYPE.getVersion()), SAMPLE_TYPE );
        Assert.assertEquals( registry().get(SAMPLE_TYPE.getId()), SAMPLE_TYPE );
        
        Assert.assertTrue( Iterables.contains(registry().getMatching(
            RegisteredTypePredicates.symbolicName(SAMPLE_TYPE.getSymbolicName())), SAMPLE_TYPE) );
    }

    @Test
    public void testCantAddSameIdUnlessSameInstanceOrForced() {
        add(SAMPLE_TYPE);
        RegisteredType sampleTypeClone = RegisteredTypes.bean("item.A", "1", new BasicTypeImplementationPlan("ignore", null), String.class);
        add(sampleTypeClone, true);
        Assert.assertNotEquals( registry().get(SAMPLE_TYPE.getId()), SAMPLE_TYPE );
        
        add(SAMPLE_TYPE, true);
        Assert.assertEquals( registry().get(SAMPLE_TYPE.getId()), SAMPLE_TYPE );

        try {
            add(sampleTypeClone);
            Asserts.shouldHaveFailedPreviously();
        } catch (Exception e) {
            Asserts.expectedFailureContains(e, SAMPLE_TYPE.getSymbolicName());
        }
        
        // only one entry
        Assert.assertEquals( Iterables.size(registry().getMatching(
            RegisteredTypePredicates.symbolicName(SAMPLE_TYPE.getSymbolicName()))), 1);
        // unversioned request returns sample
        Assert.assertEquals( registry().get(SAMPLE_TYPE.getSymbolicName()), SAMPLE_TYPE );
    }

    @Test
    public void testGettingBestVersion() {
        add(SAMPLE_TYPE);
        add(SAMPLE_TYPE2);
        
        Assert.assertTrue( Iterables.contains(registry().getAll(), SAMPLE_TYPE) );
        Assert.assertTrue( Iterables.contains(registry().getAll(), SAMPLE_TYPE2) );
        Assert.assertEquals( registry().get(SAMPLE_TYPE.getId()), SAMPLE_TYPE );
        Assert.assertEquals( registry().get(SAMPLE_TYPE2.getId()), SAMPLE_TYPE2 );
        Assert.assertNotEquals( registry().get(SAMPLE_TYPE2.getId()), SAMPLE_TYPE );
        
        Assert.assertEquals( Iterables.size(registry().getMatching(
            RegisteredTypePredicates.symbolicName(SAMPLE_TYPE.getSymbolicName()))), 2);
        
        // unversioned request returns latest
        Assert.assertEquals( registry().get(SAMPLE_TYPE.getSymbolicName()), SAMPLE_TYPE2 );
    }

    @Test
    public void testGetWithFilter() {
        add(SAMPLE_TYPE);
        
        Assert.assertEquals( Iterables.size(registry().getMatching(Predicates.and(
            RegisteredTypePredicates.symbolicName(SAMPLE_TYPE.getSymbolicName()),
            RegisteredTypePredicates.subtypeOf(String.class)
            ))), 1 );
        Assert.assertTrue( Iterables.isEmpty(registry().getMatching(Predicates.and(
                RegisteredTypePredicates.symbolicName(SAMPLE_TYPE.getSymbolicName()),
                RegisteredTypePredicates.subtypeOf(Integer.class)
            ))) );
    }
    
    @Test
    public void testGetWithContext() {
        add(SAMPLE_TYPE);
        Assert.assertEquals( registry().get(SAMPLE_TYPE.getId(),  
            RegisteredTypeLoadingContexts.bean(String.class)), SAMPLE_TYPE );
        Assert.assertEquals( registry().get(SAMPLE_TYPE.getId(),  
            RegisteredTypeLoadingContexts.bean(Integer.class)), null );
    }

    @Test
    public void testAlias() {
        add(SAMPLE_TYPE);
        add(SAMPLE_TYPE2);
        
        RegisteredType sampleType15WithAliases = RegisteredTypes.addAliases(
            RegisteredTypes.bean("item.A", "1.1", new BasicTypeImplementationPlan("ignore", null), String.class),
            MutableList.of("my_a", "the_a"));
        add(sampleType15WithAliases);
        Assert.assertEquals(sampleType15WithAliases.getAliases(), MutableSet.of("my_a", "the_a"));
        
        Assert.assertEquals( Iterables.size(registry().getMatching(
            RegisteredTypePredicates.symbolicName(SAMPLE_TYPE.getSymbolicName()))), 3);
        
        Assert.assertEquals( registry().get("my_a"), sampleType15WithAliases );
        Assert.assertEquals( registry().get("the_a"), sampleType15WithAliases );
        Assert.assertEquals( registry().get(sampleType15WithAliases.getId()), sampleType15WithAliases );
        
        // but unadorned type still returns v2
        Assert.assertEquals( registry().get(sampleType15WithAliases.getSymbolicName()), SAMPLE_TYPE2 );
        
        // and filters work
        Assert.assertEquals( registry().getMatching(RegisteredTypePredicates.alias("the_a")),
            MutableList.of(sampleType15WithAliases) );
        Assert.assertEquals( registry().get("my_a",  
            RegisteredTypeLoadingContexts.bean(String.class)), sampleType15WithAliases );
        Assert.assertEquals( registry().get("my_a",  
            RegisteredTypeLoadingContexts.bean(Integer.class)), null );
    }

    @Test
    public void testTags() {
        add(SAMPLE_TYPE);
        add(SAMPLE_TYPE2);
        
        RegisteredType sampleType15WithTags = RegisteredTypes.addTags(
            RegisteredTypes.bean("item.A", "1.1", new BasicTypeImplementationPlan("ignore", null), String.class),
            MutableList.of("my_a", "the_a"));
        add(sampleType15WithTags);
        Assert.assertEquals(sampleType15WithTags.getTags(), MutableSet.of("my_a", "the_a"));
        
        Assert.assertEquals( Iterables.size(registry().getMatching(
            RegisteredTypePredicates.symbolicName(SAMPLE_TYPE.getSymbolicName()))), 3);
        
        Assert.assertEquals( registry().get(sampleType15WithTags.getId()), sampleType15WithTags );
        
        // and filters work
        Assert.assertEquals( registry().getMatching(RegisteredTypePredicates.tag("the_a")),
            MutableList.of(sampleType15WithTags) );
        
        // but can't lookup by tag as a get
        Assert.assertEquals( registry().get("my_a"), null );
        
        // and unadorned type still returns v2
        Assert.assertEquals( registry().get(sampleType15WithTags.getSymbolicName()), SAMPLE_TYPE2 );
        
    }

}

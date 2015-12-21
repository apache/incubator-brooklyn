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
package org.apache.brooklyn.util.collections;

import java.util.Arrays;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

@Test
public class MutableListTest {

    public void testBuilderAddArray() throws Exception {
        List<Object> vals = MutableList.builder().addAll(new Object[] {1,2,3}).build();
        Assert.assertEquals(vals, ImmutableList.of(1,2,3));
    }
    
    public void testBuilderAddVarargs() throws Exception {
        List<Object> vals = MutableList.builder().add(1,2,3).build();
        Assert.assertEquals(vals, ImmutableList.of(1,2,3));
    }
    
    public void testBuilderAddIfNotNull() throws Exception {
        List<Object> vals = MutableList.builder().addIfNotNull(1).addIfNotNull(null).build();
        Assert.assertEquals(vals, ImmutableList.of(1));
    }
    
    public void testBuilderAddIterable() throws Exception {
        List<Object> vals = MutableList.builder().addAll(ImmutableList.of(1,2)).addAll(ImmutableList.of(2,3)).build();
        Assert.assertEquals(vals, ImmutableList.of(1,2,2,3));
    }
    
    public void testBuilderAddIterator() throws Exception {
        List<Object> vals = MutableList.builder().addAll(ImmutableList.of(1,2).iterator()).build();
        Assert.assertEquals(vals, ImmutableList.of(1,2));
    }
    
    public void testBuilderRemoval() throws Exception {
        List<Object> vals = MutableList.builder()
                .add(1,2,3)
                .remove(2)
                .add(4)
                .build();
        Assert.assertEquals(vals, ImmutableList.of(1,3,4));
    }
    
    public void testBuilderRemoveAll() throws Exception {
        List<Object> vals = MutableList.builder()
                .add(1,2,3)
                .removeAll(ImmutableList.of(2,3))
                .add(4)
                .build();
        Assert.assertEquals(vals, ImmutableList.of(1,4));
    }
    
    public void testEqualsExact() {
        List<Object> a = MutableList.<Object>of("a", 1, "b", false);
        List<Object> b = MutableList.<Object>of("a", 1, "b", false);
        Assert.assertEquals(a, b);
    }
    
    public void testNotEqualsUnordered() {
        List<Object> a = MutableList.<Object>of("a", 1, "b", false);
        List<Object> b = MutableList.<Object>of("b", false, "a", 1);
        Assert.assertNotEquals(a, b);
    }

    public void testEqualsDifferentTypes() {
        List<Object> a = MutableList.<Object>of("a", 1, "b", false);
        List<Object> b = Arrays.<Object>asList("a", 1, "b", false);
        Assert.assertEquals(a, b);
        Assert.assertEquals(b, a);
    }

    public void testEqualsDifferentTypes2() {
        List<Object> a = MutableList.<Object>of("http");
        List<String> b = Arrays.<String>asList("http");
        Assert.assertEquals(a, b);
        Assert.assertEquals(b, a);
    }

    public void testContainingNullAndUnmodifiable() {
        MutableList<Object> x = MutableList.<Object>of("x", null);
        Assert.assertTrue(x.contains(null));
        
        List<Object> x1 = x.asUnmodifiable();
        List<Object> x2 = x.asUnmodifiableCopy();
        List<Object> x3 = x.asImmutableCopy();
        
        x.remove(null);
        Assert.assertFalse(x.contains(null));
        Assert.assertFalse(x1.contains(null));
        Assert.assertTrue(x2.contains(null));
        Assert.assertTrue(x3.contains(null));
        
        try { x1.remove("x"); Assert.fail(); } catch (Exception e) { /* expected */ }
        try { x2.remove("x"); Assert.fail(); } catch (Exception e) { /* expected */ }
        try { x3.remove("x"); Assert.fail(); } catch (Exception e) { /* expected */ }
        
        Assert.assertTrue(x1.contains("x"));
        Assert.assertTrue(x2.contains("x"));
        Assert.assertTrue(x3.contains("x"));
    }
    
}

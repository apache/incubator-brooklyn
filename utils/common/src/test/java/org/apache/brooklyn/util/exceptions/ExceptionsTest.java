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
package org.apache.brooklyn.util.exceptions;

import java.util.ConcurrentModificationException;
import java.util.concurrent.ExecutionException;

import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.ExceptionsTest;
import org.apache.brooklyn.util.exceptions.PropagatedRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ExceptionsTest {

    private static final Logger log = LoggerFactory.getLogger(ExceptionsTest.class);
    
    @Test
    public void test12CollapseCompound() throws Exception {
        RuntimeException e = Exceptions.create("test1", MutableSet.of(new IllegalStateException("test2"), new IllegalStateException("test3")));
        assert12StandardChecks(e, false);
    }
    
    @Test
    public void test12CollapsePropagatedExecutionCompoundBoring() throws Exception {
        RuntimeException e = new PropagatedRuntimeException("test1", 
            new ExecutionException(Exceptions.create(MutableSet.of(new IllegalStateException("test2"), new IllegalStateException("test3")))));
        assert12StandardChecks(e, true);
    }

    @Test
    public void test12CollapsePropagatedCompoundConcMod() throws Exception {
        RuntimeException e = new PropagatedRuntimeException("test1", 
            new ExecutionException(Exceptions.create(MutableSet.of(new ConcurrentModificationException("test2"), new ConcurrentModificationException("test3")))));
        assert12StandardChecks(e, true);
        assertContains(e, "ConcurrentModification");
    }
    
    @Test
    public void testCollapseTextWhenExceptionMessageEmpty() throws Exception {
        String text = Exceptions.collapseText(new ExecutionException(new IllegalStateException()));
        Assert.assertNotNull(text);
    }
    
    private void assert12StandardChecks(RuntimeException e, boolean isPropagated) {
        String collapseText = Exceptions.collapseText(e);
        log.info("Exception collapsing got: "+collapseText+" ("+e+")");
        assertContains(e, "test1");
        assertContains(e, "test2");
        
        if (isPropagated)
            assertContains(e, "2 errors, including");
        else
            assertContains(e, "2 errors including");
        
        assertNotContains(e, "IllegalState");
        assertNotContains(e, "CompoundException");
        Assert.assertFalse(collapseText.contains("Propagated"), "should NOT have had Propagated: "+collapseText);
        
        if (isPropagated)
            Assert.assertTrue(e.toString().contains("Propagate"), "SHOULD have had Propagated: "+e);
        else
            Assert.assertFalse(e.toString().contains("Propagate"), "should NOT have had Propagated: "+e);
    }
    
    private static void assertContains(Exception e, String keyword) {
        Assert.assertTrue(e.toString().contains(keyword), "Missing keyword '"+keyword+"' in exception toString: "+e);
        Assert.assertTrue(Exceptions.collapseText(e).contains(keyword), "Missing keyword '"+keyword+"' in collapseText: "+Exceptions.collapseText(e));
    }
    private static void assertNotContains(Exception e, String keyword) {
        Assert.assertFalse(e.toString().contains(keyword), "Unwanted keyword '"+keyword+"' in exception toString: "+e);
        Assert.assertFalse(Exceptions.collapseText(e).contains(keyword), "Unwanted keyword '"+keyword+"' in collapseText: "+Exceptions.collapseText(e));
    }
    
}

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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.ConcurrentModificationException;
import java.util.concurrent.ExecutionException;

import org.apache.brooklyn.util.collections.MutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;

public class ExceptionsTest {

    private static final Logger log = LoggerFactory.getLogger(ExceptionsTest.class);
    
    @Test
    public void testPropagateRuntimeException() throws Exception {
        NullPointerException tothrow = new NullPointerException("simulated");
        try {
            throw Exceptions.propagate(tothrow);
        } catch (NullPointerException e) {
            assertEquals(e, tothrow);
        }
    }
    
    @Test
    public void testPropagateCheckedException() throws Exception {
        Exception tothrow = new Exception("simulated");
        try {
            throw Exceptions.propagate(tothrow);
        } catch (RuntimeException e) {
            assertEquals(e.getCause(), tothrow);
        }
    }
    
    @Test
    public void testPropagateCheckedExceptionWithMessage() throws Exception {
        String extraMsg = "my message";
        Exception tothrow = new Exception("simulated");
        try {
            throw Exceptions.propagate(extraMsg, tothrow);
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "my message");
            assertEquals(e.getCause(), tothrow);
        }
    }
    
    @Test
    public void testPropagateRuntimeExceptionIgnoresMessage() throws Exception {
        NullPointerException tothrow = new NullPointerException("simulated");
        try {
            throw Exceptions.propagate("my message", tothrow);
        } catch (NullPointerException e) {
            assertEquals(e, tothrow);
        }
    }
    
    @Test
    public void testPropagateIfFatalPropagatesInterruptedException() throws Exception {
        InterruptedException tothrow = new InterruptedException("simulated");
        try {
            Exceptions.propagateIfFatal(tothrow);
            fail();
        } catch (RuntimeException e) {
            assertTrue(Thread.interrupted()); // note this clears the interrupted flag as well
            assertEquals(e.getCause(), tothrow);
        }
    }
    
    @Test
    public void testPropagateIfFatalPropagatesRuntimeInterruptedException() throws Exception {
        RuntimeInterruptedException tothrow = new RuntimeInterruptedException(new InterruptedException("simulated"));
        try {
            Exceptions.propagateIfFatal(tothrow);
            fail();
        } catch (RuntimeInterruptedException e) {
            assertTrue(Thread.interrupted()); // note this clears the interrupted flag as well
            assertEquals(e, tothrow);
        }
    }
    
    @Test
    public void testPropagateIfFatalPropagatesError() throws Exception {
        Error tothrow = new Error();
        try {
            Exceptions.propagateIfFatal(tothrow);
            fail();
        } catch (Error e) {
            assertEquals(e, tothrow);
        }
    }
    
    @Test
    public void testPropagateIfFatalDoesNotPropagatesNormalException() throws Exception {
        Exception e = new Exception();
        Exceptions.propagateIfFatal(e);
        
        RuntimeException re = new RuntimeException();
        Exceptions.propagateIfFatal(re);
        
        Throwable t = new Throwable();
        Exceptions.propagateIfFatal(t);
    }
    
    @Test
    public void testGetFirstThrowableOfType() throws Exception {
        NullPointerException npe = new NullPointerException("simulated");
        IllegalStateException ise = new IllegalStateException("simulated", npe);
        
        assertEquals(Exceptions.getFirstThrowableOfType(ise, IllegalStateException.class), ise);
        assertEquals(Exceptions.getFirstThrowableOfType(ise, NullPointerException.class), npe);
        assertNull(Exceptions.getFirstThrowableOfType(ise, IndexOutOfBoundsException.class));
    }
    
    @Test
    public void testGetFirstThrowableMatching() throws Exception {
        NullPointerException npe = new NullPointerException("simulated");
        IllegalStateException ise = new IllegalStateException("simulated", npe);
        
        assertEquals(Exceptions.getFirstThrowableMatching(ise, Predicates.instanceOf(IllegalStateException.class)), ise);
        assertEquals(Exceptions.getFirstThrowableMatching(ise, Predicates.instanceOf(NullPointerException.class)), npe);
        assertNull(Exceptions.getFirstThrowableMatching(ise, Predicates.alwaysFalse()));
    }
    
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

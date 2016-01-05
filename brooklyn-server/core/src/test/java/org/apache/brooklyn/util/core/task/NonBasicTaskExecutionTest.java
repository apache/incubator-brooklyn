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
package org.apache.brooklyn.util.core.task;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.BasicExecutionManager;
import org.apache.brooklyn.util.core.task.BasicTask;
import org.apache.brooklyn.util.core.task.ForwardingTask;
import org.apache.brooklyn.util.core.task.TaskInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test the operation of the {@link BasicTask} class.
 *
 * TODO clarify test purpose
 */
public class NonBasicTaskExecutionTest {
    private static final Logger log = LoggerFactory.getLogger(NonBasicTaskExecutionTest.class);
 
    private static final int TIMEOUT_MS = 10*1000;
    
    public static class ConcreteForwardingTask<T> extends ForwardingTask<T> {
        private final TaskInternal<T> delegate;

        ConcreteForwardingTask(TaskInternal<T> delegate) {
            this.delegate = delegate;
        }
        
        @Override
        protected TaskInternal<T> delegate() {
            return delegate;
        }
    }
    
    private BasicExecutionManager em;
    private Map<Integer,String> data;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        em = new BasicExecutionManager("mycontext");
        data = Collections.synchronizedMap(new HashMap<Integer,String>());
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (em != null) em.shutdownNow();
    }
    
    @Test
    public void runSimpleTask() throws Exception {
        TaskInternal<Object> t = new ConcreteForwardingTask<Object>(new BasicTask<Object>(new Callable<Object>() {
            @Override public Object call() {
                return data.put(1, "b");
            }}));
        data.put(1, "a");
        Task<?> t2 = em.submit(MutableMap.of("tag", "A"), t);
        assertEquals("a", t.get());
        assertEquals("a", t2.get());
        assertSame(t, t2, "t="+t+"; t2="+t2);
        assertEquals("b", data.get(1));
    }
    
    @Test
    public void runBasicTaskWithWaits() throws Exception {
        final CountDownLatch signalStarted = new CountDownLatch(1);
        final CountDownLatch allowCompletion = new CountDownLatch(1);
        final TaskInternal<Object> t = new ConcreteForwardingTask<Object>(new BasicTask<Object>(new Callable<Object>() {
            @Override public Object call() throws Exception {
                Object result = data.put(1, "b");
                signalStarted.countDown();
                assertTrue(allowCompletion.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
                return result;
            }}));
        data.put(1, "a");

        Task<?> t2 = em.submit(MutableMap.of("tag", "A"), t);
        assertEquals(t, t2);
        assertFalse(t.isDone());
        
        assertTrue(signalStarted.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals("b", data.get(1));
        assertFalse(t.isDone());
        
        log.debug("runBasicTaskWithWaits, BasicTask status: {}", t.getStatusDetail(false));
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                t.getStatusDetail(false).toLowerCase().contains("waiting");
            }});
        // "details="+t.getStatusDetail(false))
        
        allowCompletion.countDown();
        assertEquals("a", t.get());
    }
}

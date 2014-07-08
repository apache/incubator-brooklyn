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
package brooklyn.util.mutex;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

public class WithMutexesTest {

    @Test
    public void testOneAcquisitionAndRelease() throws InterruptedException {
        MutexSupport m = new MutexSupport();
        Map<String, SemaphoreWithOwners> sems;
        SemaphoreWithOwners s;
        try {
            m.acquireMutex("foo", "something foo");
            sems = m.getAllSemaphores();
            Assert.assertEquals(sems.size(), 1);
            s = sems.get("foo");
            Assert.assertEquals(s.getDescription(), "something foo");
            Assert.assertEquals(s.getOwningThreads(), Arrays.asList(Thread.currentThread()));
            Assert.assertEquals(s.getRequestingThreads(), Collections.emptyList());
            Assert.assertTrue(s.isInUse());
            Assert.assertTrue(s.isCallingThreadAnOwner());
        } finally {
            m.releaseMutex("foo");
        }
        Assert.assertFalse(s.isInUse());
        Assert.assertFalse(s.isCallingThreadAnOwner());
        Assert.assertEquals(s.getDescription(), "something foo");
        Assert.assertEquals(s.getOwningThreads(), Collections.emptyList());
        Assert.assertEquals(s.getRequestingThreads(), Collections.emptyList());
        
        sems = m.getAllSemaphores();
        Assert.assertEquals(sems, Collections.emptyMap());
    }

    @Test(groups = "Integration")  //just because it takes a wee while
    public void testBlockingAcquisition() throws InterruptedException {
        final MutexSupport m = new MutexSupport();
        m.acquireMutex("foo", "something foo");
        
        Assert.assertFalse(m.tryAcquireMutex("foo", "something else"));

        Thread t = new Thread() {
            public void run() {
                try {
                    m.acquireMutex("foo", "thread 2 foo");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                m.releaseMutex("foo");
            }
        };
        t.start();
        
        t.join(500);
        Assert.assertTrue(t.isAlive());
        Assert.assertEquals(m.getSemaphore("foo").getRequestingThreads(), Arrays.asList(t));

        m.releaseMutex("foo");
        
        t.join(1000);
        Assert.assertFalse(t.isAlive());

        Assert.assertEquals(m.getAllSemaphores(), Collections.emptyMap());
    }

    
    public static class SampleWithMutexesDelegatingMixin implements WithMutexes {
        
        /* other behaviour would typically go here... */
        
        WithMutexes mutexSupport = new MutexSupport();
        
        @Override
        public void acquireMutex(String mutexId, String description) throws InterruptedException {
            mutexSupport.acquireMutex(mutexId, description);
        }

        @Override
        public boolean tryAcquireMutex(String mutexId, String description) {
            return mutexSupport.tryAcquireMutex(mutexId, description);
        }

        @Override
        public void releaseMutex(String mutexId) {
            mutexSupport.releaseMutex(mutexId);
        }

        @Override
        public boolean hasMutex(String mutexId) {
            return mutexSupport.hasMutex(mutexId);
        }
    }
    
    @Test
    public void testDelegatingMixinPattern() throws InterruptedException {
        WithMutexes m = new SampleWithMutexesDelegatingMixin();
        m.acquireMutex("foo", "sample");
        Assert.assertTrue(m.hasMutex("foo"));
        Assert.assertFalse(m.hasMutex("bar"));
        m.releaseMutex("foo");
        Assert.assertFalse(m.hasMutex("foo"));
    }
}

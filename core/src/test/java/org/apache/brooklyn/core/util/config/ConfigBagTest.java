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
package org.apache.brooklyn.core.util.config;

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.util.config.ConfigBag;
import org.apache.brooklyn.core.util.config.ConfigBagTest;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ConfigKeys;

public class ConfigBagTest {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(ConfigBagTest.class);
    
    private static final ConfigKey<String> K1 = ConfigKeys.newStringConfigKey("k1");
    private static final ConfigKey<String> K2 = ConfigKeys.newStringConfigKey("k2");
    private static final ConfigKey<String> K3 = ConfigKeys.newStringConfigKey("k3");
    
    @Test
    public void testPutAndGet() {
        ConfigBag bag = ConfigBag.newInstance();
        bag.put(K1, "v1");
        assertEquals(bag.get(K1), "v1");
    }
    
    @Test
    public void testPutStringAndGet() {
        ConfigBag bag = ConfigBag.newInstance();
        bag.putAsStringKey(K1.getName(), "v1");
        assertEquals(bag.get(K1), "v1");
    }
    
    @Test
    public void testUnused() {
        ConfigBag bag = ConfigBag.newInstance();
        bag.put(K1, "v1");
        bag.put(K2, "v2a");
        assertEquals(bag.get(K1), "v1");
        assertEquals(bag.getUnusedConfig().size(), 1);
        assertEquals(bag.peek(K2), "v2a");
        assertEquals(bag.getUnusedConfig().size(), 1);
        assertEquals(bag.get(K2), "v2a");
        Assert.assertTrue(bag.getUnusedConfig().isEmpty());
    }

    @Test
    public void testOrder() {
        ConfigBag bag = ConfigBag.newInstance();
        bag.put(K1, "v1");
        bag.put(K2, "v2");
        bag.put(K3, "v3");
        Assert.assertEquals(MutableList.copyOf(bag.getAllConfig().keySet()), MutableList.of(K1.getName(), K2.getName(), K3.getName()));
        Assert.assertEquals(MutableList.copyOf(bag.getAllConfig().values()), MutableList.of("v1", "v2", "v3"));
    }
        
    @Test
    public void testCopyOverwriteAndGet() {
        ConfigBag bag1 = ConfigBag.newInstance();
        bag1.put(K1, "v1");
        bag1.put(K2, "v2a");
        bag1.put(K3, "v3");
        assertEquals(bag1.get(K1), "v1");
        
        ConfigBag bag2 = ConfigBag.newInstanceCopying(bag1).putAll(MutableMap.of(K2, "v2b"));
        assertEquals(bag1.getUnusedConfig().size(), 2);
        assertEquals(bag2.getUnusedConfig().size(), 2);
        
        assertEquals(bag2.get(K1), "v1");
        assertEquals(bag1.get(K2), "v2a");
        assertEquals(bag1.getUnusedConfig().size(), 1);
        assertEquals(bag2.getUnusedConfig().size(), 2);
        
        assertEquals(bag2.get(K2), "v2b");
        assertEquals(bag2.getUnusedConfig().size(), 1);
        
        assertEquals(bag2.get(K3), "v3");
        assertEquals(bag2.getUnusedConfig().size(), 0);
        assertEquals(bag1.getUnusedConfig().size(), 1);
    }
    
    @Test
    public void testCopyExtendingAndGet() {
        ConfigBag bag1 = ConfigBag.newInstance();
        bag1.put(K1, "v1");
        bag1.put(K2, "v2a");
        bag1.put(K3, "v3");
        assertEquals(bag1.get(K1), "v1");
        
        ConfigBag bag2 = ConfigBag.newInstanceExtending(bag1, null).putAll(MutableMap.of(K2, "v2b"));
        assertEquals(bag1.getUnusedConfig().size(), 2);
        assertEquals(bag2.getUnusedConfig().size(), 2, "unused are: "+bag2.getUnusedConfig());
        
        assertEquals(bag2.get(K1), "v1");
        assertEquals(bag1.get(K2), "v2a");
        assertEquals(bag1.getUnusedConfig().size(), 1);
        assertEquals(bag2.getUnusedConfig().size(), 2);
        
        assertEquals(bag2.get(K2), "v2b");
        assertEquals(bag2.getUnusedConfig().size(), 1);
        
        assertEquals(bag2.get(K3), "v3");
        assertEquals(bag2.getUnusedConfig().size(), 0);
        // when extended, the difference is that parent is also marked
        assertEquals(bag1.getUnusedConfig().size(), 0);
    }

    @Test
    public void testConcurrent() throws InterruptedException {
        ConfigBag bag = ConfigBag.newInstance();
        bag.put(K1, "v1");
        bag.put(K2, "v2");
        bag.put(K3, "v3");
        runConcurrentTest(bag, 10, Duration.millis(50));
    }
    
    @Test(groups="Integration")
    public void testConcurrentBig() throws InterruptedException {
        ConfigBag bag = ConfigBag.newInstance();
        bag.put(K1, "v1");
        bag.put(K2, "v2");
        bag.put(K3, "v3");
        runConcurrentTest(bag, 20, Duration.seconds(5));
    }
    
    private void runConcurrentTest(final ConfigBag bag, int numThreads, Duration time) throws InterruptedException {
        List<Thread> threads = MutableList.of();
        final Map<Thread,Exception> exceptions = new ConcurrentHashMap<Thread,Exception>();
        final AtomicInteger successes = new AtomicInteger();
        for (int i=0; i<numThreads; i++) {
            Thread t = new Thread() {
                @Override
                public void run() {
                    try {
                        while (!interrupted()) {
                            if (Math.random()<0.9)
                                bag.put(ConfigKeys.newStringConfigKey("k"+((int)(10*Math.random()))), "v"+((int)(10*Math.random())));
                            if (Math.random()<0.8)
                                bag.get(ConfigKeys.newStringConfigKey("k"+((int)(10*Math.random()))));
                            if (Math.random()<0.2)
                                bag.copy(bag);
                            if (Math.random()<0.6)
                                bag.remove(ConfigKeys.newStringConfigKey("k"+((int)(10*Math.random()))));
                            successes.incrementAndGet();
                        }
                    } catch (Exception e) {
                        exceptions.put(Thread.currentThread(), e);
                        Exceptions.propagateIfFatal(e);
                    }
                }
            };
            t.setName("ConfigBagTest-concurrent-thread-"+i);
            threads.add(t);
        }
        for (Thread t: threads) t.start();
        time.countdownTimer().waitForExpiry();
        for (Thread t: threads) t.interrupt();
        for (Thread t: threads) t.join();
        Assert.assertTrue(exceptions.isEmpty(), "Got "+exceptions.size()+"/"+numThreads+" exceptions ("+successes.get()+" successful): "+exceptions);
    }
    
}

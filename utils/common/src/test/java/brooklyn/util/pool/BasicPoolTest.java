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
package brooklyn.util.pool;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.internal.annotations.Sets;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

public class BasicPoolTest {

    private List<Integer> closedVals;
    private Supplier<Integer> supplier;
    Function<Integer,Void> closer;
    private ListeningExecutorService executor;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        // Note we use final theCounter variable, rather than just a field, to guarantee 
        // that each sequential test run won't be accessing the same field value if a test
        // doesn't tear down and keeps executing in another thread for some reason.
        
        final AtomicInteger theCounter = new AtomicInteger(0);
        closedVals = new CopyOnWriteArrayList<Integer>();
        
        supplier = new Supplier<Integer>() {
            @Override public Integer get() {
                return theCounter.getAndIncrement();
            }
        };
        closer = new Function<Integer,Void>() {
            @Override public Void apply(@Nullable Integer input) {
                closedVals.add(input);
                return null;
            }
        };
        executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (executor != null) executor.shutdownNow();
    }
    
    @Test
    public void testGeneratesNewValuesWhenRequired() throws Exception {
        Pool<Integer> pool = BasicPool.<Integer>builder().supplier(supplier).build();
        
        Lease<Integer> lease1 = pool.leaseObject();
        assertEquals(lease1.leasedObject(), (Integer)0);
        
        Lease<Integer> lease2 = pool.leaseObject();
        assertEquals(lease2.leasedObject(), (Integer)1);
    }
    
    @Test
    public void testReusesReturnedVals() throws Exception {
        Pool<Integer> pool = BasicPool.<Integer>builder().supplier(supplier).build();
        
        Lease<Integer> lease1 = pool.leaseObject();
        assertEquals(lease1.leasedObject(), (Integer)0);
        lease1.close();
        
        Lease<Integer> lease2 = pool.leaseObject();
        assertEquals(lease2.leasedObject(), (Integer)0);
    }
    
    @Test
    public void testGeneratesNewIfOnlyUnviableValsInPool() throws Exception {
        Pool<Integer> pool = BasicPool.<Integer>builder().supplier(supplier).viabilityChecker(Predicates.alwaysFalse()).closer(closer).build();
        
        Lease<Integer> lease1 = pool.leaseObject();
        assertEquals(lease1.leasedObject(), (Integer)0);
        lease1.close();
        
        Lease<Integer> lease2 = pool.leaseObject();
        assertEquals(lease2.leasedObject(), (Integer)1);
        
        // Expect the "unviable" resource to have been closed
        assertEquals(closedVals, ImmutableList.of(0));
    }
    
    @Test
    public void testReusesOnlyViableVals() throws Exception {
        Pool<Integer> pool = BasicPool.<Integer>builder().supplier(supplier).viabilityChecker(Predicates.equalTo(1)).build();
        
        Lease<Integer> lease1 = pool.leaseObject();
        Lease<Integer> lease2 = pool.leaseObject();
        Lease<Integer> lease3 = pool.leaseObject();
        
        lease1.close();
        lease2.close();
        lease3.close();
        
        Lease<Integer> lease4 = pool.leaseObject();
        assertEquals(lease4.leasedObject(), (Integer)1);
    }
    
    @Test
    public void testClosesResourcesInPool() throws Exception {
        Pool<Integer> pool = BasicPool.<Integer>builder().supplier(supplier).closer(closer).build();
        
        Lease<Integer> lease1 = pool.leaseObject();
        Lease<Integer> lease2 = pool.leaseObject();
        lease1.close();
        lease2.close();

        pool.close();
        assertEquals(closedVals, ImmutableList.of(0, 1));
    }
    
    @Test
    public void testClosesResourceReturnedAfterPoolIsClosed() throws Exception {
        Pool<Integer> pool = BasicPool.<Integer>builder().supplier(supplier).closer(closer).build();
        
        Lease<Integer> lease1 = pool.leaseObject();
        pool.close();
        assertEquals(closedVals, ImmutableList.of());
        
        lease1.close();
        assertEquals(closedVals, ImmutableList.of(lease1.leasedObject()));
    }
    
    @Test
    public void testDoesNotReuseUnviableVals() throws Exception {
        Pool<Integer> pool = BasicPool.<Integer>builder().supplier(supplier).viabilityChecker(Predicates.alwaysFalse()).build();
        
        Lease<Integer> lease1 = pool.leaseObject();
        assertEquals(lease1.leasedObject(), (Integer)0);
        lease1.close();
        
        Lease<Integer> lease2 = pool.leaseObject();
        assertEquals(lease2.leasedObject(), (Integer)1);
    }
    
    @Test
    public void testConcurrentCallsNeverHaveSameVal() throws Exception {
        final Pool<Integer> pool = BasicPool.<Integer>builder().supplier(supplier).build();
        final Set<Lease<Integer>> leases = Collections.newSetFromMap(new ConcurrentHashMap<Lease<Integer>, Boolean>());
        List<ListenableFuture<?>> futures = Lists.newArrayList();
        
        for (int i = 0; i < 1000; i++) {
            futures.add(executor.submit(new Runnable() {
                public void run() {
                    leases.add(pool.leaseObject());
                }
            }));
        }
        Futures.allAsList(futures).get();
        
        Set<Integer> currentlyLeased = Sets.newLinkedHashSet();
        for (Lease<Integer> lease : leases) {
            boolean val = currentlyLeased.add(lease.leasedObject());
            if (!val) fail("duplicate="+lease.leasedObject()+"; vals="+leases);
        }
    }
}

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
package org.apache.brooklyn.core.test.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.entity.group.DynamicClusterImpl;
import org.apache.brooklyn.util.collections.QuorumCheck.QuorumChecks;

import com.google.common.collect.ImmutableList;

/**
* Mock cluster entity for testing.
*/
public class TestClusterImpl extends DynamicClusterImpl implements TestCluster {
    private volatile int size;

    private final List<Integer> desiredSizeHistory = Collections.synchronizedList(new ArrayList<Integer>());
    private final List<Integer> sizeHistory = Collections.synchronizedList(new ArrayList<Integer>());
    
    public TestClusterImpl() {
    }
    
    @Override
    public void init() {
        super.init();
        sizeHistory.add(size);
        size = getConfig(INITIAL_SIZE);
        sensors().set(Startable.SERVICE_UP, true);
    }
    
    @Override
    protected void initEnrichers() {
        // say this is up if it has no children 
        config().set(UP_QUORUM_CHECK, QuorumChecks.atLeastOneUnlessEmpty());
        
        super.initEnrichers();
    }
    
    @Override
    public Integer resize(Integer desiredSize) {
        desiredSizeHistory.add(desiredSize);
        int achievableSize = Math.min(desiredSize, getConfig(MAX_SIZE));
        
        if (achievableSize != size) {
            this.sizeHistory.add(achievableSize);
            this.size = achievableSize;
        }
        
        if (desiredSize > achievableSize) {
            throw new InsufficientCapacityException("Simulating insufficient capacity (desiredSize="+desiredSize+"; maxSize="+getConfig(MAX_SIZE)+"; newSize="+size+")");
        }

        return size;
    }
    
    @Override
    public List<Integer> getSizeHistory() {
        synchronized (sizeHistory) {
            return ImmutableList.copyOf(sizeHistory);
        }
    }
    
    @Override
    public List<Integer> getDesiredSizeHistory() {
        synchronized (desiredSizeHistory) {
            return ImmutableList.copyOf(desiredSizeHistory);
        }
    }
    
    @Override
    public void stop() {
        size = 0;
        super.stop();
    }
    
    @Override
    public Integer getCurrentSize() {
        return size;
    }
}

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
package brooklyn.test.entity;

import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.trait.Startable;

/**
* Mock cluster entity for testing.
*/
public class TestClusterImpl extends DynamicClusterImpl implements TestCluster {
    private volatile int size;

    public TestClusterImpl() {
    }
    
    @Override
    public void init() {
        super.init();
        size = getConfig(INITIAL_SIZE);
        setAttribute(Startable.SERVICE_UP, true);
    }
    
    @Override
    public Integer resize(Integer desiredSize) {
        this.size = desiredSize;
        return size;
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

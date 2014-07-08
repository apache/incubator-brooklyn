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
package io.brooklyn.camp.brooklyn;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.location.Location;

public class TestEntityWithInitConfigImpl extends AbstractEntity implements TestEntityWithInitConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TestEntityWithInitConfigImpl.class);
    private Entity entityCachedOnInit;
    
    @Override
    public void init() {
        super.init();
        entityCachedOnInit = getConfig(TEST_ENTITY);
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        LOG.trace("Starting {}", this);
    }

    @Override
    public void stop() {
        LOG.trace("Stopping {}", this);
    }

    @Override
    public void restart() {
        LOG.trace("Restarting {}", this);
    }

    public Entity getEntityCachedOnInit() {
        return entityCachedOnInit;
    }
}

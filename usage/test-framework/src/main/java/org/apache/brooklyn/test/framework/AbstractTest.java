/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.test.framework;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.methods.DslComponent;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

/**
 * Abstract base class for tests, providing common target lookup.
 */
public abstract class AbstractTest extends AbstractEntity implements BaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractTest.class);

    /**
     * Find the target entity using "target" config key, if entity provided directly in config, or by doing an implicit
     * lookup using DSL ($brooklyn:component("myNginX")), if id of entity provided as "targetId" config key.
     *
     * @return The target entity.
     * @throws @RuntimeException if no target can be determined.
     */
    public Entity resolveTarget() {
        Entity entity = getConfig(TARGET_ENTITY);
        if (null == entity) {
            entity = getTargetById();
        }
        return entity;
    }

    private Entity getTargetById() {
        String targetId = getConfig(TARGET_ID);
        final Task<Entity> targetLookup = new DslComponent(targetId).newTask();
        Entity entity = null;
        try {
            entity = Tasks.resolveValue(targetLookup, Entity.class, getExecutionContext(), "Finding entity " + targetId);
            LOG.debug("Found target by id {}", targetId);
        } catch (final ExecutionException | InterruptedException e) {
            LOG.error("Error finding target {}", targetId);
            Exceptions.propagate(e);
        }
        return entity;
    }
}

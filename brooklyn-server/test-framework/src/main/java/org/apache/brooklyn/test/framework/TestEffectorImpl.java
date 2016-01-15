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

import static org.apache.brooklyn.test.framework.TestFrameworkAssertions.getAssertions;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.mgmt.internal.EffectorUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.time.Duration;

/**
 *
 */
public class TestEffectorImpl extends TargetableTestComponentImpl implements TestEffector {
    private static final Logger LOG = LoggerFactory.getLogger(TestEffectorImpl.class);


    /**
     * {@inheritDoc}
     */
    public void start(Collection<? extends Location> locations) {
        if (!getChildren().isEmpty()) {
            throw new RuntimeException(String.format("The entity [%s] cannot have child entities", getClass().getName()));
        }
        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);
        final Entity targetEntity = resolveTarget();
        final String effectorName = getConfig(EFFECTOR_NAME);
        final Map<String, ?> effectorParams = getConfig(EFFECTOR_PARAMS);
        final Duration timeout = getConfig(TIMEOUT);
        try {
            Maybe<Effector<?>> effector = EffectorUtils.findEffectorDeclared(targetEntity, effectorName);
            if (effector.isAbsentOrNull()) {
                throw new AssertionError(String.format("No effector with name [%s]", effectorName));
            }
            final Task<?> effectorTask;
            if (effectorParams == null || effectorParams.isEmpty()) {
                effectorTask = Entities.invokeEffector(this, targetEntity, effector.get());
            } else {
                effectorTask = Entities.invokeEffector(this, targetEntity, effector.get(), effectorParams);
            }

            final Object effectorResult = effectorTask.get(timeout);

            final List<Map<String, Object>> assertions = getAssertions(this, ASSERTIONS);
            if(assertions != null && !assertions.isEmpty()){
                TestFrameworkAssertions.checkAssertions(ImmutableMap.of("timeout", timeout), assertions, effectorName, new Supplier<String>() {
                    @Override
                    public String get() {
                        return (String)effectorResult;
                    }
                });
            }

            //Add result of effector to sensor
            sensors().set(EFFECTOR_RESULT, effectorResult);
            sensors().set(SERVICE_UP, true);
            ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        } catch (Throwable t) {
            sensors().set(SERVICE_UP, false);
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void stop() {
        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPING);
        sensors().set(SERVICE_UP, false);
    }

    /**
     * {@inheritDoc}
     */
    public void restart() {
        final Collection<Location> locations = Lists.newArrayList(getLocations());
        stop();
        start(locations);
    }
}

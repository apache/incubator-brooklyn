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

package org.apache.brooklyn.test.framework.entity;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.test.framework.entity.TestEntity;

import java.util.Collection;

/**
 * @author m4rkmckenna on 27/10/2015.
 */
public class TestEntityImpl extends AbstractEntity implements TestEntity {
    @Override
    public void start(final Collection<? extends Location> locations) {
    }

    @Override
    public void stop() {

    }

    @Override
    public void restart() {
    }

    @Override
    public void simpleEffector() {
        sensors().set(SIMPLE_EFFECTOR_INVOKED, Boolean.TRUE);
    }

    @Override
    public TestPojo complexEffector(@EffectorParam(name = "stringValue") final String stringValue, @EffectorParam(name = "booleanValue") final Boolean booleanValue, @EffectorParam(name = "longValue") final Long longValue) {
        sensors().set(COMPLEX_EFFECTOR_INVOKED, Boolean.TRUE);
        sensors().set(COMPLEX_EFFECTOR_STRING, stringValue);
        sensors().set(COMPLEX_EFFECTOR_BOOLEAN, booleanValue);
        sensors().set(COMPLEX_EFFECTOR_LONG, longValue);
        return new TestPojo(stringValue, booleanValue, longValue);
    }

    @Override
    public String effectorReturnsString(@EffectorParam(name = "stringToReturn") String stringToReturn) {
        return stringToReturn;
    }
}

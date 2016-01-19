/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.brooklyn.entity.software.base;

import static org.testng.Assert.assertEquals;

import java.util.Collection;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class SameServerDriverLifecycleEffectorTasksTest extends BrooklynAppUnitTestSupport {

    @ImplementedBy(EntityWithConfigImpl.class)
    public interface EntityWithConfig extends Entity {
        PortAttributeSensorAndConfigKey PORT = new PortAttributeSensorAndConfigKey(
                "port", "port", PortRanges.fromString("1234"));
        ConfigKey<Integer> INTEGER = ConfigKeys.newIntegerConfigKey(
                "test.integer", "int", 1);
        ConfigKey<Double> DOUBLE = ConfigKeys.newDoubleConfigKey(
                "test.double", "double", 2.0);
        ConfigKey<String> STRING = ConfigKeys.newStringConfigKey(
                "test.string", "string", "3");
        ConfigKey<Integer> REGEX_PORT = ConfigKeys.newIntegerConfigKey(
                "my.port", "int", null);
    }

    public static class EntityWithConfigImpl extends AbstractEntity implements EntityWithConfig {
    }

    @Test
    public void testGetRequiredOpenPorts() {
        SameServerEntity entity = app.createAndManageChild(EntitySpec.create(SameServerEntity.class).child(
                EntitySpec.create(EntityWithConfig.class)
                        // Previously SSDLET coerced everything TypeCoercions could handle to a port!
                        .configure(EntityWithConfig.INTEGER, 1)
                        .configure(EntityWithConfig.DOUBLE, 2.0)
                        .configure(EntityWithConfig.STRING, "3")));
        SameServerDriverLifecycleEffectorTasks effectorTasks = new SameServerDriverLifecycleEffectorTasks();
        Collection<Integer> requiredPorts = effectorTasks.getRequiredOpenPorts(entity);
        final ImmutableSet<Integer> expected = ImmutableSet.of(22, 1234);
        assertEquals(requiredPorts, expected,
                "expected=" + Iterables.toString(expected) + ", actual=" + Iterables.toString(requiredPorts));
    }

    @Test
    public void testGetRequiredOpenPortsByConfigName() {
        SameServerEntity entity = app.createAndManageChild(EntitySpec.create(SameServerEntity.class).child(
                EntitySpec.create(EntityWithConfig.class)
                        // Previously SSDLET coerced everything TypeCoercions could handle to a port!
                        .configure(EntityWithConfig.INTEGER, 1)
                        .configure(EntityWithConfig.DOUBLE, 2.0)
                        .configure(EntityWithConfig.STRING, "3")
                        .configure(EntityWithConfig.REGEX_PORT, 4321)));
        SameServerDriverLifecycleEffectorTasks effectorTasks = new SameServerDriverLifecycleEffectorTasks();
        Collection<Integer> requiredPorts = effectorTasks.getRequiredOpenPorts(entity);
        final ImmutableSet<Integer> expected = ImmutableSet.of(22, 1234, 4321);
        assertEquals(requiredPorts, expected,
                "expected=" + Iterables.toString(expected) + ", actual=" + Iterables.toString(requiredPorts));
    }

    @Test
    public void testGetRequiredOpenPortsNoAutoInfer() {
        SameServerEntity entity = app.createAndManageChild(EntitySpec.create(SameServerEntity.class)
                .child(
                        EntitySpec.create(EntityWithConfig.class)
                                // Previously SSDLET coerced everything TypeCoercions could handle to a port!
                                .configure(EntityWithConfig.INTEGER, 1)
                                .configure(EntityWithConfig.DOUBLE, 2.0)
                                .configure(EntityWithConfig.STRING, "3")
                                .configure(EntityWithConfig.REGEX_PORT, 4321)
                                .configure(SameServerEntity.INBOUND_PORTS_AUTO_INFER, false)));
        SameServerDriverLifecycleEffectorTasks effectorTasks = new SameServerDriverLifecycleEffectorTasks();
        Collection<Integer> requiredPorts = effectorTasks.getRequiredOpenPorts(entity);
        final ImmutableSet<Integer> expected = ImmutableSet.of(22);
        assertEquals(requiredPorts, expected,
                "expected=" + Iterables.toString(expected) + ", actual=" + Iterables.toString(requiredPorts));
    }

    @Test
    public void testGetRequiredOpenPortsWithCustomLoginPort() {
        SameServerEntity entity = app.createAndManageChild(EntitySpec.create(SameServerEntity.class)
                .configure(SameServerEntity.REQUIRED_OPEN_LOGIN_PORTS, ImmutableSet.of(2022))
                .child(
                        EntitySpec.create(EntityWithConfig.class)
                                // Previously SSDLET coerced everything TypeCoercions could handle to a port!
                                .configure(EntityWithConfig.INTEGER, 1)
                                .configure(EntityWithConfig.DOUBLE, 2.0)
                                .configure(EntityWithConfig.STRING, "3")));
        SameServerDriverLifecycleEffectorTasks effectorTasks = new SameServerDriverLifecycleEffectorTasks();
        Collection<Integer> requiredPorts = effectorTasks.getRequiredOpenPorts(entity);
        final ImmutableSet<Integer> expected = ImmutableSet.of(2022, 1234);
        assertEquals(requiredPorts, expected,
                "expected=" + Iterables.toString(expected) + ", actual=" + Iterables.toString(requiredPorts));
    }
}

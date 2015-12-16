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

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import java.util.Map;

/**
 * Entity that invokes an effector on another entity
 *
 * @author m4rkmckenna
 */
@ImplementedBy(value = TestEffectorImpl.class)
public interface TestEffector extends BaseTest {

    @SetFromFlag(nullable = false)
    ConfigKey<String> EFFECTOR_NAME = ConfigKeys.newConfigKey(String.class, "effector", "The name of the effector to invoke");

    ConfigKey<Map<String, ?>> EFFECTOR_PARAMS = ConfigKeys.newConfigKey(new TypeToken<Map<String, ?>>() {
    }, "params", "The parameters to pass to the effector", ImmutableMap.<String, Object>of());

    AttributeSensorAndConfigKey<Object, Object> EFFECTOR_RESULT = ConfigKeys.newSensorAndConfigKey(Object.class, "result", "The result of invoking the effector");

}
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
package brooklyn.test.policy;

import java.util.Collections;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.reflect.TypeToken;

public class TestPolicy extends AbstractPolicy {
    @SetFromFlag("confName")
    public static final ConfigKey<String> CONF_NAME = ConfigKeys.newStringConfigKey("test.confName", "Configuration key, my name", "defaultval");
    
    @SetFromFlag("confFromFunction")
    public static final ConfigKey<String> CONF_FROM_FUNCTION = ConfigKeys.newStringConfigKey("test.confFromFunction", "Configuration key, from function", "defaultval");
    
    @SetFromFlag("attributeSensor")
    public static final ConfigKey<AttributeSensor<String>> TEST_ATTRIBUTE_SENSOR = BasicConfigKey.builder(new TypeToken<AttributeSensor<String>>(){})
        .name("test.attributeSensor")
        .build();
    
    public TestPolicy() {
        this(Collections.emptyMap());
    }
    
    public TestPolicy(Map<?, ?> properties) {
        super(properties);
    }

    public Map<?, ?> getLeftoverProperties() {
        return Collections.unmodifiableMap(leftoverProperties);
    }

    @Override
    protected <T> void doReconfigureConfig(ConfigKey<T> key, T val) {
        // no-op
    }
}

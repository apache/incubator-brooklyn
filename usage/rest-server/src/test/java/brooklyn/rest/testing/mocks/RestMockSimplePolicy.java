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
package brooklyn.rest.testing.mocks;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.flags.SetFromFlag;

public class RestMockSimplePolicy extends AbstractPolicy {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(RestMockSimplePolicy.class);

    public RestMockSimplePolicy() {
        super();
    }

    @SuppressWarnings("rawtypes")
    public RestMockSimplePolicy(Map flags) {
        super(flags);
    }

    @SetFromFlag("sampleConfig")
    public static final ConfigKey<String> SAMPLE_CONFIG = BasicConfigKey.builder(String.class)
            .name("brooklyn.rest.mock.sample.config")
            .description("Mock sample config")
            .defaultValue("DEFAULT_VALUE")
            .reconfigurable(true)
            .build();

    @SetFromFlag
    public static final ConfigKey<Integer> INTEGER_CONFIG = BasicConfigKey.builder(Integer.class)
            .name("brooklyn.rest.mock.sample.integer")
            .description("Mock integer config")
            .defaultValue(1)
            .reconfigurable(true)
            .build();

    @Override
    protected <T> void doReconfigureConfig(ConfigKey<T> key, T val) {
        // no-op
    }
}

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

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.util.flags.SetFromFlag;

/**
 * Provides a config key that must be non-null but does not specify a default value.
 */
public interface RequiredConfigEntitySuper extends TestEntity {

    @SetFromFlag("nonNullNoDefaultSuper")
    ConfigKey<Object> NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE_IN_SUPER = ConfigKeys.builder(Object.class)
            .name("test.conf.non-null.super.without-default")
            .description("Configuration key that must not be null")
            .nonNull()
            .build();

}

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
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(RequiredConfigEntityImpl.class)
public interface RequiredConfigEntity extends RequiredConfigEntitySuper {

    @SetFromFlag("nonNullDefault")
    ConfigKey<Object> NON_NULL_CONFIG_WITH_DEFAULT_VALUE = ConfigKeys.builder(Object.class)
            .name("test.conf.non-null.with-default")
            .description("Configuration key that must not be null")
            .defaultValue(new Object())
            .nonNull()
            .build();

    @SetFromFlag("nonNullNoDefault")
    ConfigKey<Object> NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE = ConfigKeys.builder(Object.class)
            .name("test.conf.non-null.without-default")
            .description("Configuration key that must not be null")
            .nonNull()
            .build();


    @SetFromFlag("nullable")
    ConfigKey<Object> NULLABLE_CONFIG = ConfigKeys.builder(Object.class)
            .name("test.conf.nullable")
            .description("Nullable configuration key with no default value")
            .build();
}

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
package org.apache.brooklyn.test.osgi.entities;


import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.policy.basic.AbstractPolicy;
import org.apache.brooklyn.core.util.flags.SetFromFlag;

import brooklyn.entity.basic.ConfigKeys;

public class SimplePolicy extends AbstractPolicy {
    @SetFromFlag("config1")
    public static final ConfigKey<String> CONFIG1 = ConfigKeys.newStringConfigKey("config1");

    @SetFromFlag("config2")
    public static final ConfigKey<String> CONFIG2 = ConfigKeys.newStringConfigKey("config2");

    @SetFromFlag("config3")
    public static final ConfigKey<String> CONFIG3 = ConfigKeys.newStringConfigKey("config3");
}

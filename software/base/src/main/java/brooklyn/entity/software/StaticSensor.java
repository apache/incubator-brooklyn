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
package brooklyn.entity.software;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.effector.AddSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.config.ConfigBag;

public class StaticSensor<T> extends AddSensor<Integer> {

    public static final ConfigKey<Integer> STATIC_VALUE = ConfigKeys.newConfigKey(Integer.class, "static.value");

    private final Integer value;

    public StaticSensor(ConfigBag params) {
        super(params);
        value = params.get(STATIC_VALUE);
    }

    @Override
    public void apply(EntityLocal entity) {
        super.apply(entity);
        entity.setAttribute(Sensors.newIntegerSensor(name), value);
    }
}

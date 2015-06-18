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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.effector.AddSensor;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.guava.Maybe;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;

public class StaticSensor<T> extends AddSensor<T> {

    private static final Logger log = LoggerFactory.getLogger(StaticSensor.class);
    
    public static final ConfigKey<Object> STATIC_VALUE = ConfigKeys.newConfigKey(Object.class, "static.value");

    private final Object value;

    public StaticSensor(ConfigBag params) {
        super(params);
        value = params.get(STATIC_VALUE);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void apply(EntityLocal entity) {
        super.apply(entity);
        
        Maybe<T> v = Tasks.resolving(value).as((Class<T>)sensor.getType()).timeout(Duration.millis(200)).getMaybe();
        if (v.isPresent()) {
            log.debug(this+" setting sensor "+sensor+" to "+v.get());
            entity.setAttribute(sensor, v.get());
        } else {
            log.debug(this+" not setting sensor "+sensor+"; cannot resolve "+value);
        }
    }
}

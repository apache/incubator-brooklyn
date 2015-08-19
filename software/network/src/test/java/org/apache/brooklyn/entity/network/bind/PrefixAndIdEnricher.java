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
package org.apache.brooklyn.entity.network.bind;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.sensor.core.Sensors;
import org.apache.brooklyn.sensor.enricher.AbstractEnricher;

import com.google.common.reflect.TypeToken;

public class PrefixAndIdEnricher extends AbstractEnricher {

    public static final AttributeSensor<String> SENSOR = Sensors.newStringSensor(
            "prefixandid.sensor");

    public static final ConfigKey<String> PREFIX = ConfigKeys.newStringConfigKey(
            "prefixandid.prefix", "Sets SENSOR to prefix+entity id");

    public static final ConfigKey<AttributeSensor<?>> MONITOR = ConfigKeys.newConfigKey(new TypeToken<AttributeSensor<?>>() {},
            "prefixandid.attributetomonitor", "Changes on this sensor are monitored and the prefix/id republished");

    public PrefixAndIdEnricher() {
    }

    @Override
    public void setEntity(final EntityLocal entity) {
        super.setEntity(entity);
        subscribe(entity, getConfig(MONITOR), new SensorEventListener<Object>() {
            @Override
            public void onEvent(SensorEvent<Object> event) {
                entity.setAttribute(SENSOR, getConfig(PREFIX) + entity.getId());
            }
        });
    }

}

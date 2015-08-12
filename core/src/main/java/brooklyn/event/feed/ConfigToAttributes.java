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
package brooklyn.event.feed;

import org.apache.brooklyn.management.ManagementContext;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.Sensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.TemplatedStringAttributeSensorAndConfigKey;


/** Simple config adapter for setting {@link AttributeSensorAndConfigKey} sensor values from the config value or config default */ 
public class ConfigToAttributes {

    //normally just applied once, statically, not registered...
    public static void apply(EntityLocal entity) {
        for (Sensor<?> it : entity.getEntityType().getSensors()) {
            if (it instanceof AttributeSensorAndConfigKey) {
                apply(entity, (AttributeSensorAndConfigKey<?,?>)it);
            }
        }
    }

    /**
     * Convenience for ensuring an individual sensor is set from its config key
     * (e.g. sub-classes of DynamicWebAppCluster that don't want to set HTTP_PORT etc!)
     */
    public static <T> T apply(EntityLocal entity, AttributeSensorAndConfigKey<?,T> key) {
        T v = entity.getAttribute(key);
        if (v!=null) return v;
        v = key.getAsSensorValue(entity);
        if (v!=null) entity.setAttribute(key, v);
        return v;
    }

    /**
     * Convenience for transforming a config value (e.g. processing a {@link TemplatedStringAttributeSensorAndConfigKey}),
     * outside of the context of an entity.
     */
    public static <T> T transform(ManagementContext managementContext, AttributeSensorAndConfigKey<?,T> key) {
        return key.getAsSensorValue(managementContext);
    }
}

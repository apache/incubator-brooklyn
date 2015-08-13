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
package brooklyn.entity.software.winrm;

import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.basic.EntityLocal;
import org.apache.brooklyn.api.entity.proxying.EntityInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.event.basic.Sensors;
import brooklyn.event.feed.windows.WindowsPerformanceCounterFeed;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;

import com.google.common.reflect.TypeToken;

public class WindowsPerformanceCounterSensors implements EntityInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(WindowsPerformanceCounterSensors.class);

    public final static ConfigKey<Set<Map<String, String>>> PERFORMANCE_COUNTERS = ConfigKeys.newConfigKey(new TypeToken<Set<Map<String, String>>>(){}, "performance.counters");

    protected final Set<Map<String, String>> sensors;

    public WindowsPerformanceCounterSensors(ConfigBag params) {
        sensors = params.get(PERFORMANCE_COUNTERS);
    }

    public WindowsPerformanceCounterSensors(Map<String, String> params) {
        this(ConfigBag.newInstance(params));
    }

    @Override
    public void apply(EntityLocal entity) {
        WindowsPerformanceCounterFeed.Builder builder = WindowsPerformanceCounterFeed.builder()
                .entity(entity);
        for (Map<String, String> sensorConfig : sensors) {
            String name = sensorConfig.get("name");
            String sensorType = sensorConfig.get("sensorType");
            Class<?> clazz;
            try {
                clazz = Strings.isNonEmpty(sensorType)
                        ? ((EntityInternal)entity).getManagementContext().getCatalog().getRootClassLoader().loadClass(sensorType) 
                        : String.class;
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Could not load type "+sensorType+" for sensor "+name, e);
            }
            builder.addSensor(sensorConfig.get("counter"), Sensors.newSensor(clazz, name, sensorConfig.get("description")));
        }
        builder.build();
    }
}

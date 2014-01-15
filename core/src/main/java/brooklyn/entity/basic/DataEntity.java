/*
 * Copyright 2014 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;

import com.google.common.base.Supplier;
import com.google.common.reflect.TypeToken;

/**
 * An entity that supplies data as {@link AttributeSensor} values.
 * <p>
 * Usage:
 * <pre>{@code
 * EntitySpec.create(DataEntity.class)
 *          .configure(SENSOR_DATA_MAP, MutableMap.builder()
 *                  .put(Sensors.newStringSensor("string.data"), new Supplier&lt;String&gt;() { ... })
 *                  .put(Sensors.newLongSensor("long.data"), new Supplier&lt;Long&gt;() { ... })
 *                  .build());
 * }</pre>
 */
@ImplementedBy(DataEntityImpl.class)
public interface DataEntity extends Entity, Startable {

    ConfigKey<Map<AttributeSensor<?>, Supplier<?>>> SENSOR_DATA_MAP = ConfigKeys.newConfigKey(
            new TypeToken<Map<AttributeSensor<?>, Supplier<?>>>() { },
            "data.sensorSupplierMap", "Map linking Sensors and data Suppliers");

}

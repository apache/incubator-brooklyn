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
package brooklyn.rest.resources;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.rest.api.SensorApi;
import brooklyn.rest.domain.SensorSummary;
import brooklyn.rest.transform.SensorTransformer;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class SensorResource extends AbstractBrooklynRestResource implements SensorApi {

    private static final Logger log = LoggerFactory.getLogger(SensorResource.class);

    @SuppressWarnings("rawtypes")
    @Override
    public List<SensorSummary> list(final String application, final String entityToken) {
        final EntityLocal entity = brooklyn().getEntity(application, entityToken);

        return Lists.newArrayList(transform(filter(entity.getEntityType().getSensors(), AttributeSensor.class),
                new Function<AttributeSensor, SensorSummary>() {
                    @Override
                    public SensorSummary apply(AttributeSensor sensor) {
                        return SensorTransformer.sensorSummary(entity, sensor);
                    }
                }));
    }

    @Override
    public Map<String, Object> batchSensorRead(final String application, final String entityToken, final Boolean raw) {
        final EntityLocal entity = brooklyn().getEntity(application, entityToken);
        Map<String, Object> sensorMap = Maps.newHashMap();
        @SuppressWarnings("rawtypes")
        Iterable<AttributeSensor> sensors = filter(entity.getEntityType().getSensors(), AttributeSensor.class);

        for (AttributeSensor<?> sensor : sensors) {
            Object value = entity.getAttribute(findSensor(entity, sensor.getName()));
            if (Boolean.FALSE.equals(raw)) {
                value = applyDisplayValueHint(sensor, value);
            }
            sensorMap.put(sensor.getName(), getValueForDisplay(value, true, false));
        }
        return sensorMap;
    }

    protected Object get(boolean preferJson, String application, String entityToken, String sensorName, Boolean raw) {
        final EntityLocal entity = brooklyn().getEntity(application, entityToken);
        AttributeSensor<?> sensor = findSensor(entity, sensorName);
        Object value = entity.getAttribute(sensor);
        if (Boolean.FALSE.equals(raw)) {
            value = applyDisplayValueHint(sensor, value);
        }
        return getValueForDisplay(value, preferJson, true);
    }

    @Override
    public String getPlain(String application, String entityToken, String sensorName, final Boolean raw) {
        return (String) get(false, application, entityToken, sensorName, raw);
    }

    @Override
    public Object get(final String application, final String entityToken, String sensorName, final Boolean raw) {
        return get(true, application, entityToken, sensorName, raw);
    }

    public static final Object applyDisplayValueHint(AttributeSensor<?> sensor, Object initialValue) {
        Iterable<RendererHints.DisplayValue> hints = Iterables.filter(RendererHints.getHintsFor(sensor), RendererHints.DisplayValue.class);
        if (Iterables.size(hints) > 1) {
            log.warn("Multiple display value hints set for sensor {}; Only one will be applied, using first", sensor);
        }

        Optional<RendererHints.DisplayValue> hint = Optional.fromNullable(Iterables.getFirst(hints, null));
        if (hint.isPresent()) {
            return hint.get().getDisplayValue(initialValue);
        } else {
            return initialValue;
        }
    }

    private AttributeSensor<?> findSensor(EntityLocal entity, String name) {
        Sensor<?> s = entity.getEntityType().getSensor(name);
        if (s instanceof AttributeSensor) return (AttributeSensor<?>) s;
        return new BasicAttributeSensor<Object>(Object.class, name);
    }

}

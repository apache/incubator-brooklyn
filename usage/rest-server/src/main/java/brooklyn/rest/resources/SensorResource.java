/*
 * Copyright 2013-2014 by Cloudsoft Corporation Limited
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
package brooklyn.rest.resources;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.rest.api.SensorApi;
import brooklyn.rest.domain.SensorSummary;
import brooklyn.rest.transform.SensorTransformer;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class SensorResource extends AbstractBrooklynRestResource implements SensorApi {

    @Override
    public List<SensorSummary> list(final String application, final String entityToken) {
        final EntityLocal entity = brooklyn().getEntity(application, entityToken);

        return Lists.newArrayList(transform(filter(
                entity.getEntityType().getSensors(),
                new Predicate<Sensor<?>>() {
                    @Override
                    public boolean apply(@Nullable Sensor<?> input) {
                        return input instanceof AttributeSensor;
                    }
                }),
                new Function<Sensor<?>, SensorSummary>() {
                    @Override
                    public SensorSummary apply(Sensor<?> sensor) {
                        return SensorTransformer.sensorSummary(entity, sensor);
                    }
                }));
    }

    @Override
    public Map<String, Object> batchSensorRead(final String application, final String entityToken) {
        final EntityLocal entity = brooklyn().getEntity(application, entityToken);
        Map<String, Object> sensorMap = Maps.newHashMap();
        List<Sensor<?>> sensors = Lists.newArrayList(filter(entity.getEntityType().getSensors(),
                new Predicate<Sensor<?>>() {
                    @Override
                    public boolean apply(@Nullable Sensor<?> input) {
                        return input instanceof AttributeSensor;
                    }
                }));

        for (Sensor<?> sensor : sensors) {
            Object value = entity.getAttribute(findSensor(entity, sensor.getName()));
            Iterable<RendererHints.DisplayValue> hints = Iterables.filter(RendererHints.getHintsFor(sensor), RendererHints.DisplayValue.class);
            for (RendererHints.DisplayValue dv : hints) value = dv.getDisplayValue(value);
            sensorMap.put(sensor.getName(), getValueForDisplay(value, true, false));
        }
        return sensorMap;
    }

    protected Object get(boolean preferJson, String application, String entityToken, String sensorName) {
        final EntityLocal entity = brooklyn().getEntity(application, entityToken);
        AttributeSensor<?> sensor = findSensor(entity, sensorName);
        Object value = entity.getAttribute(sensor);
        Iterable<RendererHints.DisplayValue> hints = Iterables.filter(RendererHints.getHintsFor(sensor), RendererHints.DisplayValue.class);
        for (RendererHints.DisplayValue dv : hints) value = dv.getDisplayValue(value);
        return getValueForDisplay(value, preferJson, true);
    }

    @Override
    public String getPlain(String application, String entityToken, String sensorName) {
        return (String) get(false, application, entityToken, sensorName);
    }

    @Override
    public Object get(final String application, final String entityToken, String sensorName) {
        return get(true, application, entityToken, sensorName);
    }

    private AttributeSensor<?> findSensor(EntityLocal entity, String name) {
        Sensor<?> s = entity.getEntityType().getSensor(name);
        if (s instanceof AttributeSensor) return (AttributeSensor<?>) s;
        return new BasicAttributeSensor<Object>(Object.class, name);
    }

}

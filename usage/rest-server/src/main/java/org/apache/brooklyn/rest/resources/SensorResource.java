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
package org.apache.brooklyn.rest.resources;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.internal.EntityLocal;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.core.mgmt.entitlement.Entitlements;
import org.apache.brooklyn.entity.core.EntityInternal;
import org.apache.brooklyn.rest.api.SensorApi;
import org.apache.brooklyn.rest.domain.SensorSummary;
import org.apache.brooklyn.rest.filter.HaHotStateRequired;
import org.apache.brooklyn.rest.transform.SensorTransformer;
import org.apache.brooklyn.rest.util.WebResourceUtils;
import org.apache.brooklyn.sensor.core.BasicAttributeSensor;
import org.apache.brooklyn.util.core.task.ValueResolver;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@HaHotStateRequired
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
            sensorMap.put(sensor.getName(), 
                resolving(value).preferJson(true).asJerseyOutermostReturnValue(false).raw(raw).context(entity).timeout(Duration.ZERO).renderAs(sensor).resolve());
        }
        return sensorMap;
    }

    protected Object get(boolean preferJson, String application, String entityToken, String sensorName, Boolean raw) {
        final EntityLocal entity = brooklyn().getEntity(application, entityToken);
        AttributeSensor<?> sensor = findSensor(entity, sensorName);
        Object value = entity.getAttribute(sensor);
        return resolving(value).preferJson(preferJson).asJerseyOutermostReturnValue(true).raw(raw).context(entity).timeout(ValueResolver.PRETTY_QUICK_WAIT).renderAs(sensor).resolve();
    }

    @Override
    public String getPlain(String application, String entityToken, String sensorName, final Boolean raw) {
        return (String) get(false, application, entityToken, sensorName, raw);
    }

    @Override
    public Object get(final String application, final String entityToken, String sensorName, final Boolean raw) {
        return get(true, application, entityToken, sensorName, raw);
    }

    private AttributeSensor<?> findSensor(EntityLocal entity, String name) {
        Sensor<?> s = entity.getEntityType().getSensor(name);
        if (s instanceof AttributeSensor) return (AttributeSensor<?>) s;
        return new BasicAttributeSensor<Object>(Object.class, name);
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void setFromMap(String application, String entityToken, Map newValues) {
        final EntityLocal entity = brooklyn().getEntity(application, entityToken);
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.MODIFY_ENTITY, entity)) {
            throw WebResourceUtils.unauthorized("User '%s' is not authorized to modify entity '%s'",
                Entitlements.getEntitlementContext().user(), entity);
        }

        if (log.isDebugEnabled())
            log.debug("REST user "+Entitlements.getEntitlementContext()+" setting sensors "+newValues);
        for (Object entry: newValues.entrySet()) {
            String sensorName = Strings.toString(((Map.Entry)entry).getKey());
            Object newValue = ((Map.Entry)entry).getValue();
            
            AttributeSensor sensor = findSensor(entity, sensorName);
            entity.setAttribute(sensor, newValue);
        }
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void set(String application, String entityToken, String sensorName, Object newValue) {
        final EntityLocal entity = brooklyn().getEntity(application, entityToken);
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.MODIFY_ENTITY, entity)) {
            throw WebResourceUtils.unauthorized("User '%s' is not authorized to modify entity '%s'",
                Entitlements.getEntitlementContext().user(), entity);
        }
        
        AttributeSensor sensor = findSensor(entity, sensorName);
        if (log.isDebugEnabled())
            log.debug("REST user "+Entitlements.getEntitlementContext()+" setting sensor "+sensorName+" to "+newValue);
        entity.setAttribute(sensor, newValue);
    }
    
    @Override
    public void delete(String application, String entityToken, String sensorName) {
        final EntityLocal entity = brooklyn().getEntity(application, entityToken);
        AttributeSensor<?> sensor = findSensor(entity, sensorName);
        if (log.isDebugEnabled())
            log.debug("REST user "+Entitlements.getEntitlementContext()+" deleting sensor "+sensorName);
        ((EntityInternal)entity).removeAttribute(sensor);
    }
    
}

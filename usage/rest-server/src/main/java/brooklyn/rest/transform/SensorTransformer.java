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
package brooklyn.rest.transform;

import java.net.URI;

import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.rest.domain.SensorSummary;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.URLParamEncoder;
import brooklyn.util.text.Strings;

import com.google.common.collect.Iterables;

public class SensorTransformer {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(SensorTransformer.class);

    public static SensorSummary sensorSummaryForCatalog(Sensor<?> sensor) {
        return new SensorSummary(sensor.getName(), sensor.getTypeName(),
                sensor.getDescription(), null);
    }

    @SuppressWarnings("rawtypes")
    public static SensorSummary sensorSummary(Entity entity, Sensor<?> sensor) {
        String applicationUri = "/v1/applications/" + entity.getApplicationId();
        String entityUri = applicationUri + "/entities/" + entity.getId();
        String selfUri = entityUri + "/sensors/" + URLParamEncoder.encode(sensor.getName());

        MutableMap.Builder<String, URI> lb = MutableMap.<String, URI>builder()
                .put("self", URI.create(selfUri))
                .put("application", URI.create(applicationUri))
                .put("entity", URI.create(entityUri))
                .put("action:json", URI.create(selfUri));

        Iterable<RendererHints.NamedAction> hints = Iterables.filter(RendererHints.getHintsFor(sensor), RendererHints.NamedAction.class);
        for (RendererHints.NamedAction na : hints) addNamedAction(lb, na , entity, sensor);

        return new SensorSummary(sensor.getName(), sensor.getTypeName(), sensor.getDescription(), lb.build());
    }

    @SuppressWarnings("rawtypes")
    private static void addNamedAction(MutableMap.Builder<String, URI> lb, RendererHints.NamedAction na , Entity entity, Sensor<?> sensor) {
        if (na instanceof RendererHints.NamedActionWithUrl) {
            try {
                String v = ((RendererHints.NamedActionWithUrl) na).getUrl(entity, (AttributeSensor<?>) sensor);
                if (Strings.isNonBlank(v)) {
                    String action = na.getActionName().toLowerCase();
                    lb.putIfAbsent("action:"+action, URI.create(v));
                }
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                log.warn("Unable to make use of URL sensor "+sensor+" on "+entity+": "+e);
            }
        }
    }
}

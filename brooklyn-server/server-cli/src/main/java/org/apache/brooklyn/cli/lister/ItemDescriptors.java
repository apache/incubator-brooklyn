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
package org.apache.brooklyn.cli.lister;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.objs.BrooklynDynamicType;
import org.apache.brooklyn.core.objs.BrooklynTypes;
import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.EntityType;
import org.apache.brooklyn.api.location.LocationResolver;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.BrooklynType;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.rest.domain.EffectorSummary;
import org.apache.brooklyn.rest.domain.EntityConfigSummary;
import org.apache.brooklyn.rest.domain.SensorSummary;
import org.apache.brooklyn.rest.domain.SummaryComparators;
import org.apache.brooklyn.rest.transform.EffectorTransformer;
import org.apache.brooklyn.rest.transform.EntityTransformer;
import org.apache.brooklyn.rest.transform.SensorTransformer;
import org.apache.brooklyn.util.exceptions.RuntimeInterruptedException;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ItemDescriptors {

    private static final Logger LOG = LoggerFactory.getLogger(ItemDescriptors.class);
    
    public static List<Map<String, Object>> toItemDescriptors(Iterable<? extends Class<? extends BrooklynObject>> types, boolean headingsOnly) {
        return toItemDescriptors(types, headingsOnly, null);
    }
    
    public static List<Map<String, Object>> toItemDescriptors(Iterable<? extends Class<? extends BrooklynObject>> types, boolean headingsOnly, final String sortField) {
        List<Map<String, Object>> itemDescriptors = Lists.newArrayList();
        
        for (Class<? extends BrooklynObject> type : types) {
            try {
                Map<String, Object> itemDescriptor = toItemDescriptor(type, headingsOnly);
                itemDescriptors.add(itemDescriptor);
            } catch (Throwable throwable) {
                if (throwable instanceof InterruptedException)
                    throw new RuntimeInterruptedException((InterruptedException) throwable);
                if (throwable instanceof RuntimeInterruptedException)
                    throw (RuntimeInterruptedException) throwable;

                LOG.warn("Could not load "+type+": "+throwable);
            }
        }
        
        if (!Strings.isNullOrEmpty(sortField)) {
            Collections.sort(itemDescriptors, new Comparator<Map<String, Object>>() {
                @Override public int compare(Map<String, Object> id1, Map<String, Object> id2) {
                    Object o1 = id1.get(sortField);
                    Object o2 = id2.get(sortField);
                    if (o1 == null) {
                        return o2 == null ? 0 : 1;
                    }
                    if (o2 == null) {
                        return -1;
                    }
                    return o1.toString().compareTo(o2.toString());
                }
            });
        }
        
        return itemDescriptors;
    }
    
    public static Map<String,Object> toItemDescriptor(Class<? extends BrooklynObject> clazz, boolean headingsOnly) {
        BrooklynDynamicType<?, ?> dynamicType = BrooklynTypes.getDefinedBrooklynType(clazz);
        BrooklynType type = dynamicType.getSnapshot();
        ConfigKey<?> version = dynamicType.getConfigKey(BrooklynConfigKeys.SUGGESTED_VERSION.getName());
        
        Map<String,Object> result = Maps.newLinkedHashMap();
        
        result.put("type", clazz.getName());
        if (version != null) {
            result.put("defaultVersion", version.getDefaultValue());
        }
        
        Catalog catalogAnnotation = clazz.getAnnotation(Catalog.class);
        if (catalogAnnotation != null) {
            result.put("name", catalogAnnotation.name());
            result.put("description", catalogAnnotation.description());
            result.put("iconUrl", catalogAnnotation.iconUrl());
        }
        
        Deprecated deprecatedAnnotation = clazz.getAnnotation(Deprecated.class);
        if (deprecatedAnnotation != null) {
            result.put("deprecated", true);
        }
        
        if (!headingsOnly) {
            Set<EntityConfigSummary> config = Sets.newTreeSet(SummaryComparators.nameComparator());
            Set<SensorSummary> sensors = Sets.newTreeSet(SummaryComparators.nameComparator());
            Set<EffectorSummary> effectors = Sets.newTreeSet(SummaryComparators.nameComparator());

            for (ConfigKey<?> x: type.getConfigKeys()) {
                config.add(EntityTransformer.entityConfigSummary(x, dynamicType.getConfigKeyField(x.getName())));
            }
            result.put("config", config);
            
            if (type instanceof EntityType) {
                for (Sensor<?> x: ((EntityType)type).getSensors())
                    sensors.add(SensorTransformer.sensorSummaryForCatalog(x));
                result.put("sensors", sensors);
                
                for (Effector<?> x: ((EntityType)type).getEffectors())
                    effectors.add(EffectorTransformer.effectorSummaryForCatalog(x));
                result.put("effectors", effectors);
            }
        }
        
        return result;
    }
    
    public static Object toItemDescriptors(List<LocationResolver> resolvers) {
        return toItemDescriptors(resolvers, false);
    }
    
    public static Object toItemDescriptors(List<LocationResolver> resolvers, Boolean sort) {
        List<Object> result = Lists.newArrayList();
        for (LocationResolver resolver : resolvers) {
            result.add(toItemDescriptor(resolver));
        }
        if (sort) {
            Collections.sort(result, new Comparator<Object>() {
                @Override public int compare(Object o1, Object o2) {
                    String s1 = o1 == null ? "" : o1.toString();
                    String s2 = o2 == null ? "" : o2.toString();
                    return s1.compareTo(s2);
                }
            });
        }
        return result;
    }

    public static Object toItemDescriptor(LocationResolver resolver) {
        // TODO Get javadoc of LocationResolver? Could use docklet? But that would give dependency here
        // on com.sun.javadoc.*
        return resolver.getPrefix();
    }
}

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
package brooklyn.event.feed.jmx;

import java.util.List;
import java.util.Map;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

public class JmxValueFunctions {

    private static final Logger log = LoggerFactory.getLogger(JmxValueFunctions.class);
    
    /**
     * @return a closure that converts a TabularDataSupport to a map.
     */
    public static Function<TabularData, Map> tabularDataToMap() {
        return new Function<TabularData, Map>() {
            @Override public Map apply(TabularData input) {
                return tabularDataToMap(input);
            }};
    }

    public static Function<TabularData, Map> tabularDataToMapOfMaps() {
        return new Function<TabularData, Map>() {
            @Override public Map apply(TabularData input) {
                return tabularDataToMapOfMaps(input);
            }};
    }

    public static Function<CompositeData,Map> compositeDataToMap() {
        return new Function<CompositeData, Map>() {
            @Override public Map apply(CompositeData input) {
                return compositeDataToMap(input);
            }};
    }
    
    public static Map tabularDataToMap(TabularData table) {
        Map<String, Object> result = Maps.newLinkedHashMap();
        for (Object entry : table.values()) {
            CompositeData data = (CompositeData) entry; //.getValue()
            for (String key : data.getCompositeType().keySet()) {
                Object old = result.put(key, data.get(key));
                if (old != null) {
                    log.warn("tablularDataToMap has overwritten key {}", key);
                }
            }
        }
        return result;
    }
    
    public static Map<List<?>, Map<String, Object>> tabularDataToMapOfMaps(TabularData table) {
        Map<List<?>, Map<String, Object>> result = Maps.newLinkedHashMap();
        for (Object k : table.keySet()) {
            final Object[] kValues = ((List<?>)k).toArray();
            CompositeData v = (CompositeData) table.get(kValues);
            result.put((List<?>)k, compositeDataToMap(v));
        }
        return result;
    }
    
    public static Map<String, Object> compositeDataToMap(CompositeData data) {
        Map<String, Object> result = Maps.newLinkedHashMap();
        for (String key : data.getCompositeType().keySet()) {
            Object old = result.put(key, data.get(key));
            if (old != null) {
                log.warn("compositeDataToMap has overwritten key {}", key);
            }
        }
        return result;
    }
}

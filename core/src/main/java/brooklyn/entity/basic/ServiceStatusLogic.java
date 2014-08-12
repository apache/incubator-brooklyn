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
package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.enricher.Enrichers;
import brooklyn.enricher.basic.UpdatingMap;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.policy.EnricherSpec;
import brooklyn.util.collections.CollectionFunctionals;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Functionals;

import com.google.common.base.Function;
import com.google.common.base.Functions;

/** Logic, sensors and enrichers, and conveniences, for computing service status */ 
public class ServiceStatusLogic {

    public static final AttributeSensor<Boolean> SERVICE_UP = Attributes.SERVICE_UP;
    public static final AttributeSensor<Map<String,Object>> SERVICE_NOT_UP_INDICATORS = Attributes.SERVICE_NOT_UP_INDICATORS;
    
    private ServiceStatusLogic() {}
    
    public static class ServiceNotUpLogic {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public static final EnricherSpec<?> newEnricherForServiceUpIfNoNotUpIndicators() {
            return Enrichers.builder()
                .transforming(SERVICE_NOT_UP_INDICATORS).publishing(Attributes.SERVICE_UP)
                .computing( /* cast hacks to support removing */ (Function)
                    Functionals.<Map<String,?>>
                        ifNotEquals(null).<Object>apply(Functions.forPredicate(CollectionFunctionals.<String>mapSizeEquals(0)))
                        .defaultValue(Entities.REMOVE) )
                .uniqueTag("service.isUp if no service.notUp.indicators")
                .build();
        }
        
        /** puts the given value into the {@link Attributes#SERVICE_NOT_UP_INDICATORS} map as if the 
         * {@link UpdatingMap} enricher for the given sensor reported this value (including {@link Entities#REMOVE}) */
        public static void updateMapFromSensor(EntityLocal entity, Sensor<?> sensor, Object value) {
            updateMapSensor(entity, Attributes.SERVICE_NOT_UP_INDICATORS, sensor.getName(), value);
        }
    }
    

    @SuppressWarnings("unchecked")
    public static <TKey,TVal> void updateMapSensor(EntityLocal entity, AttributeSensor<Map<TKey,TVal>> sensor,
            TKey key, Object v) {
        Map<TKey, TVal> map = entity.getAttribute(sensor);

        // TODO synchronize
        
        boolean created = (map==null);
        if (created) map = MutableMap.of();
                
        boolean changed;
        if (v == Entities.REMOVE) {
            changed = map.containsKey(key);
            if (changed)
                map.remove(key);
        } else {
            TVal oldV = map.get(key);
            if (oldV==null)
                changed = (v!=null || !map.containsKey(key));
            else
                changed = !oldV.equals(v);
            if (changed)
                map.put(key, (TVal)v);
        }
        if (changed || created)
            entity.setAttribute(sensor, map);
    }
    
}

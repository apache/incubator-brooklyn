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
package org.apache.brooklyn.enricher.stock;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.core.task.ValueResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.reflect.TypeToken;

//@Catalog(name="Transformer", description="Transforms attributes of an entity; see Enrichers.builder().transforming(...)")
@SuppressWarnings("serial")
public class Transformer<T,U> extends AbstractTransformer<T,U> {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(Transformer.class);

    // exactly one of these should be supplied to set a value
    public static ConfigKey<?> TARGET_VALUE = ConfigKeys.newConfigKey(Object.class, "enricher.targetValue");
    public static ConfigKey<Function<?, ?>> TRANSFORMATION_FROM_VALUE = ConfigKeys.newConfigKey(new TypeToken<Function<?, ?>>() {}, "enricher.transformation");
    public static ConfigKey<Function<?, ?>> TRANSFORMATION_FROM_EVENT = ConfigKeys.newConfigKey(new TypeToken<Function<?, ?>>() {}, "enricher.transformation.fromevent");
    
    public Transformer() {
    }

    /** returns a function for transformation, for immediate use only (not for caching, as it may change) */
    @Override
    @SuppressWarnings("unchecked")
    protected Function<SensorEvent<T>, U> getTransformation() {
        MutableSet<Object> suppliers = MutableSet.of();
        suppliers.addIfNotNull(config().getRaw(TARGET_VALUE).orNull());
        suppliers.addIfNotNull(config().getRaw(TRANSFORMATION_FROM_EVENT).orNull());
        suppliers.addIfNotNull(config().getRaw(TRANSFORMATION_FROM_VALUE).orNull());
        checkArgument(suppliers.size()==1,  
            "Must set exactly one of: %s, %s, %s", TARGET_VALUE.getName(), TRANSFORMATION_FROM_VALUE.getName(), TRANSFORMATION_FROM_EVENT.getName());
        
        Function<?, ?> fromEvent = config().get(TRANSFORMATION_FROM_EVENT);
        if (fromEvent != null) {  
            return (Function<SensorEvent<T>, U>) fromEvent;
        }
        
        final Function<T, U> fromValueFn = (Function<T, U>) config().get(TRANSFORMATION_FROM_VALUE);
        if (fromValueFn != null) {
            // named class not necessary as result should not be serialized
            return new Function<SensorEvent<T>, U>() {
                @Override public U apply(SensorEvent<T> input) {
                    return fromValueFn.apply(input.getValue());
                }
                @Override
                public String toString() {
                    return ""+fromValueFn;
                }
            };
        }

        // from target value
        // named class not necessary as result should not be serialized
        final Object targetValueRaw = config().getRaw(TARGET_VALUE).orNull();
        return new Function<SensorEvent<T>, U>() {
            @Override public U apply(SensorEvent<T> input) {
                // evaluate immediately, or return null
                // PRETTY_QUICK/200ms seems a reasonable compromise for tasks which require BG evaluation
                // but which are non-blocking
                // TODO better would be to have a mode in which tasks are not permitted to block on
                // external events; they can submit tasks and block on them (or even better, have a callback architecture);
                // however that is a non-trivial refactoring
                return (U) Tasks.resolving(targetValueRaw).as(targetSensor.getType())
                    .context(entity)
                    .description("Computing sensor "+targetSensor+" from "+targetValueRaw)
                    .timeout(ValueResolver.PRETTY_QUICK_WAIT)
                    .getMaybe().orNull();
            }
            public String toString() {
                return ""+targetValueRaw;
            }
        };
    }
    
}

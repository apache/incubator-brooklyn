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
package brooklyn.enricher.basic;

import static com.google.common.base.Preconditions.checkArgument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.reflect.TypeToken;

//@Catalog(name="Transformer", description="Transforms attributes of an entity; see Enrichers.builder().transforming(...)")
@SuppressWarnings("serial")
public class Transformer<T,U> extends AbstractEnricher implements SensorEventListener<T> {

    private static final Logger LOG = LoggerFactory.getLogger(Transformer.class);

    // exactly one of these should be supplied to set a value
    public static ConfigKey<?> TARGET_VALUE = ConfigKeys.newConfigKey(Object.class, "enricher.targetValue");
    public static ConfigKey<Function<?, ?>> TRANSFORMATION_FROM_VALUE = ConfigKeys.newConfigKey(new TypeToken<Function<?, ?>>() {}, "enricher.transformation");
    public static ConfigKey<Function<?, ?>> TRANSFORMATION_FROM_EVENT = ConfigKeys.newConfigKey(new TypeToken<Function<?, ?>>() {}, "enricher.transformation.fromevent");
    
    public static ConfigKey<Entity> PRODUCER = ConfigKeys.newConfigKey(Entity.class, "enricher.producer");

    public static ConfigKey<Sensor<?>> SOURCE_SENSOR = ConfigKeys.newConfigKey(new TypeToken<Sensor<?>>() {}, "enricher.sourceSensor");

    public static ConfigKey<Sensor<?>> TARGET_SENSOR = ConfigKeys.newConfigKey(new TypeToken<Sensor<?>>() {}, "enricher.targetSensor");
    
//    protected Function<? super SensorEvent<T>, ? extends U> transformation;
    protected Entity producer;
    protected Sensor<T> sourceSensor;
    protected Sensor<U> targetSensor;

    public Transformer() {
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);

        Function<SensorEvent<T>, U> transformation = getTransformation();
        this.producer = getConfig(PRODUCER) == null ? entity: getConfig(PRODUCER);
        this.sourceSensor = (Sensor<T>) getRequiredConfig(SOURCE_SENSOR);
        Sensor<?> targetSensorSpecified = getConfig(TARGET_SENSOR);
        this.targetSensor = targetSensorSpecified!=null ? (Sensor<U>) targetSensorSpecified : (Sensor<U>) this.sourceSensor;
        if (producer.equals(entity) && targetSensorSpecified==null) {
            LOG.error("Refusing to add an enricher which reads and publishes on the same sensor: "+
                producer+"."+sourceSensor+" (computing "+transformation+")");
            // we don't throw because this error may manifest itself after a lengthy deployment, 
            // and failing it at that point simply because of an enricher is not very pleasant
            // (at least not until we have good re-run support across the board)
            return;
        }
        
        subscribe(producer, sourceSensor, this);
        
        if (sourceSensor instanceof AttributeSensor) {
            Object value = producer.getAttribute((AttributeSensor<?>)sourceSensor);
            // TODO would be useful to have a convenience to "subscribeAndThenIfItIsAlreadySetRunItOnce"
            if (value!=null) {
                onEvent(new BasicSensorEvent(sourceSensor, producer, value, -1));
            }
        }
    }

    /** returns a function for transformation, for immediate use only (not for caching, as it may change) */
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
                // 200ms seems a reasonable compromise for tasks which require BG evaluation
                // but which are non-blocking
                // TODO better would be to have a mode in which tasks are not permitted to block on
                // external events; they can submit tasks and block on them (or even better, have a callback architecture);
                // however that is a non-trivial refactoring
                return (U) Tasks.resolving(targetValueRaw).as(targetSensor.getType())
                    .context( ((EntityInternal)entity).getExecutionContext() )
                    .description("Computing sensor "+targetSensor+" from "+targetValueRaw)
                    .timeout(Duration.millis(200))
                    .getMaybe().orNull();
            }
            public String toString() {
                return ""+targetValueRaw;
            }
        };
    }

    @Override
    public void onEvent(SensorEvent<T> event) {
        emit(targetSensor, compute(event));
    }

    protected Object compute(SensorEvent<T> event) {
        U result = getTransformation().apply(event);
        if (LOG.isTraceEnabled())
            LOG.trace("Enricher "+this+" computed "+result+" from "+event);
        return result;
    }
}

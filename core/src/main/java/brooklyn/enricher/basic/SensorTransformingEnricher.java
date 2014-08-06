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

import groovy.lang.Closure;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;

/**
 * @deprecated since 0.7.0; use {@link Enrichers.builder()}
 * @see Transformer if need to sub-class
 */
public class SensorTransformingEnricher<T,U> extends AbstractTypeTransformingEnricher {

    private Function<? super T, ? extends U> transformation;

    public SensorTransformingEnricher(Entity producer, Sensor<T> source, Sensor<U> target, Function<? super T, ? extends U> transformation) {
        super(producer, source, target);
        this.transformation = transformation;
        this.uniqueTag = JavaClassNames.simpleClassName(getClass())+":"+source.getName()+"*->"+target.getName();;
    }

    public SensorTransformingEnricher(Entity producer, Sensor<T> source, Sensor<U> target, Closure transformation) {
        this(producer, source, target, GroovyJavaMethods.functionFromClosure(transformation));
    }

    public SensorTransformingEnricher(Sensor<T> source, Sensor<U> target, Function<T,U> transformation) {
        this(null, source, target, transformation);
    }

    public SensorTransformingEnricher(Sensor<T> source, Sensor<U> target, Closure transformation) {
        this(null, source, target, GroovyJavaMethods.functionFromClosure(transformation));
    }

    @Override
    public void onEvent(SensorEvent event) {
        if (accept((T)event.getValue())) {
            if (target instanceof AttributeSensor)
                entity.setAttribute((AttributeSensor)target, compute((T)event.getValue()));
            else 
                entity.emit(target, compute((T)event.getValue()));
        }
    }

    protected boolean accept(T value) {
        return true;
    }

    protected U compute(T value) {
        return transformation.apply(value);
    }

    /** 
     * creates an enricher which listens to a source (from the producer), 
     * transforms it and publishes it under the target
     * 
     * Instead, consider calling:
     * <pre>
     * {@code
     * addEnricher(Enrichers.builder()
     *         .transforming(source)
     *         .publishing(target)
     *         .from(producer)
     *         .computing(transformation)
     *         .build());
     * }
     * </pre>
     * 
     * @deprecated since 0.7.0; use {@link Enrichers.builder()}
     */
    public static <U,V> SensorTransformingEnricher<U,V> newInstanceTransforming(Entity producer, AttributeSensor<U> source,
            Function<U,V> transformation, AttributeSensor<V> target) {
        return new SensorTransformingEnricher<U,V>(producer, source, target, transformation);
    }

    /** as {@link #newInstanceTransforming(Entity, AttributeSensor, Function, AttributeSensor)}
     * using the same sensor as the source and the target */
    public static <T> SensorTransformingEnricher<T,T> newInstanceTransforming(Entity producer, AttributeSensor<T> sensor,
            Function<T,T> transformation) {
        return newInstanceTransforming(producer, sensor, transformation, sensor);
    }
}

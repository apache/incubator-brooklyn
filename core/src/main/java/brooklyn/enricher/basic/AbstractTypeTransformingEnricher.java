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

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.util.flags.SetFromFlag;

/**
 * Convenience base for transforming a single sensor into a single new sensor of the same type
 * 
 * @deprecated since 0.7.0; use {@link Enrichers.builder()}
 */
public abstract class AbstractTypeTransformingEnricher<T,U> extends AbstractEnricher implements SensorEventListener<T> {
    
    @SetFromFlag
    private Entity producer;
    
    @SetFromFlag
    private Sensor<T> source;
    
    @SetFromFlag
    protected Sensor<U> target;

    public AbstractTypeTransformingEnricher() { // for rebind
    }
    
    public AbstractTypeTransformingEnricher(Entity producer, Sensor<T> source, Sensor<U> target) {
        this.producer = producer;
        this.source = source;
        this.target = target;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        if (producer==null) producer = entity;
        subscribe(producer, source, this);
        
        if (source instanceof AttributeSensor) {
            Object value = producer.getAttribute((AttributeSensor)source);
            // TODO Aled didn't you write a convenience to "subscribeAndRunIfSet" ? (-Alex)
            if (value!=null)
                onEvent(new BasicSensorEvent(source, producer, value));
        }
    }
}

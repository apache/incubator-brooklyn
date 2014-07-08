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

import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicSensorEvent;

/** 
 * enricher which adds multiple sensors on an entity to produce a new sensor
 * 
 * Instead, consider calling:
 * <pre>
 * {@code
 * addEnricher(Enrichers.builder()
 *         .combining(sources)
 *         .publishing(target)
 *         .computeSum()
 *         .build());
 * }
 * </pre>
 * <p>
 * 
 * @deprecated since 0.7.0; use {@link Enrichers.builder()}
 * @see Combiner if need to sub-class
 */
public class AddingEnricher extends AbstractEnricher implements SensorEventListener {

    private Sensor[] sources;
    private Sensor<? extends Number> target;

    public AddingEnricher(Sensor sources[], Sensor<? extends Number> target) {
        this.sources = sources;
        this.target = target;
    }

    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        
        for (Sensor source: sources) {
            subscribe(entity, source, this);
            if (source instanceof AttributeSensor) {
                Object value = entity.getAttribute((AttributeSensor)source);
                if (value!=null)
                    onEvent(new BasicSensorEvent(source, entity, value));
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void onEvent(SensorEvent event) {
        Number value = recompute();
        Number typedValue = cast(value, (Class<? extends Number>)target.getType());
        if (target instanceof AttributeSensor) {
            entity.setAttribute((AttributeSensor)target, typedValue);
        } else if (typedValue!=null)
            entity.emit((Sensor)target, typedValue);
    }

    @SuppressWarnings("unchecked")
    public static <V> V cast(Number value, Class<V> type) {
        if (value==null) return null;
        if (type.isInstance(value)) return (V)value;
        
        if (type==Integer.class) return (V) (Integer) (int)Math.round(value.doubleValue());
        if (type==Long.class) return (V) (Long) Math.round(value.doubleValue());
        if (type==Double.class) return (V) (Double) value.doubleValue();
        if (type==Float.class) return (V) (Float) value.floatValue();
        if (type==Byte.class) return (V) (Byte) (byte)Math.round(value.doubleValue());
        if (type==Short.class) return (V) (Short) (short)Math.round(value.doubleValue());
        
        throw new UnsupportedOperationException("conversion of mathematical operation to "+type+" not supported");
    }

    protected Number recompute() {
        if (sources.length==0) return null;
        Double result = 0d;
        for (Sensor source: sources) {
            Object value = entity.getAttribute((AttributeSensor) source);
            if (value==null) return null;
            result += ((Number)value).doubleValue();
        }
        return result;
    }

}

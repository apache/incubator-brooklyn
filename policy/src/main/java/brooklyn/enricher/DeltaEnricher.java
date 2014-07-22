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
package brooklyn.enricher;

import static brooklyn.util.JavaGroovyEquivalents.elvis;
import brooklyn.enricher.basic.AbstractTransformingEnricher;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.util.flags.TypeCoercions;

/**
 * Converts an absolute sensor into a delta sensor (i.e. the diff between the current and previous value)
 */
public class DeltaEnricher<T extends Number> extends AbstractTransformingEnricher<T> {
    Number last = 0;

    public DeltaEnricher() { // for rebinding
    }
    
    public DeltaEnricher(Entity producer, Sensor<T> source, AttributeSensor<T> target) {
        super(producer, source, target);
    }
    
    @Override
    public void onEvent(SensorEvent<T> event) {
        Number current = elvis(event.getValue(), 0);
        double newVal = current.doubleValue() - last.doubleValue();
        entity.setAttribute((AttributeSensor<T>)target, TypeCoercions.coerce(newVal, target.getTypeToken()));
        last = current;
    }
}

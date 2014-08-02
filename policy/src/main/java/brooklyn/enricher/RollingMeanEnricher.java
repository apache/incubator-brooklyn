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

import java.util.LinkedList;

import brooklyn.enricher.basic.AbstractTypeTransformingEnricher;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.javalang.JavaClassNames;


/**
* Transforms a sensor into a rolling average based on a fixed window size. This is useful for smoothing sample type metrics, 
* such as latency or CPU time
*/
public class RollingMeanEnricher<T extends Number> extends AbstractTypeTransformingEnricher<T,Double> {
    private LinkedList<T> values = new LinkedList<T>();
    
    @SetFromFlag
    int windowSize;

    public RollingMeanEnricher() { // for rebinding
    }
    
    public RollingMeanEnricher(Entity producer, AttributeSensor<T> source, AttributeSensor<Double> target,
            int windowSize) {
        super(producer, source, target);
        this.windowSize = windowSize;
        if (source!=null && target!=null)
            this.uniqueTag = JavaClassNames.simpleClassName(getClass())+":"+source.getName()+"->"+target.getName();
    }
    
    /** @returns null when no data has been received or windowSize is 0 */
    public Double getAverage() {
        pruneValues();
        return values.size() == 0 ? null : sum(values) / values.size();
    }
    
    @Override
    public void onEvent(SensorEvent<T> event) {
        values.addLast(event.getValue());
        pruneValues();
        entity.setAttribute((AttributeSensor<Double>)target, getAverage());
    }
    
    private void pruneValues() {
        while(windowSize > -1 && values.size() > windowSize) {
            values.removeFirst();
        }
    }
    
    private double sum(Iterable<? extends Number> vals) {
        double result = 0;
        for (Number val : vals) {
            result += val.doubleValue();
        }
        return result;
    }
}

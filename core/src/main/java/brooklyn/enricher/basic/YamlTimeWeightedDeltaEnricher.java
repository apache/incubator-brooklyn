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

import org.apache.brooklyn.api.event.SensorEvent;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.util.flags.TypeCoercions;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.basic.AbstractTransformer;
import brooklyn.entity.basic.ConfigKeys;

import com.google.common.base.Function;

/**
 * Converts an absolute count sensor into a delta sensor (i.e. the diff between the current and previous value),
 * presented as a units/timeUnit based on the event timing.
 * <p>
 * For example, given a requests.count sensor, this can make a requests.per_sec sensor with {@link #DELTA_PERIOD} set to "1s" (the default).
 * <p>
 * Suitable for configuration from YAML.
 */
public class YamlTimeWeightedDeltaEnricher<T extends Number> extends AbstractTransformer<T,Double> {
    private static final Logger LOG = LoggerFactory.getLogger(YamlTimeWeightedDeltaEnricher.class);
    
    transient Object lock = new Object();
    Number lastValue;
    long lastTime = -1;
    
    public static ConfigKey<Duration> DELTA_PERIOD = ConfigKeys.newConfigKey(Duration.class, "enricher.delta.period",
        "Duration that this delta should compute for, default per second", Duration.ONE_SECOND);
    
    @Override
    protected Function<SensorEvent<T>, Double> getTransformation() {
        return new Function<SensorEvent<T>, Double>() {
            @Override
            public Double apply(SensorEvent<T> event) {
                synchronized (lock) {
                    Double current = TypeCoercions.coerce(event.getValue(), Double.class);

                    if (current == null) return null;

                    long eventTime = event.getTimestamp();
                    long unitMillis = getConfig(DELTA_PERIOD).toMilliseconds();
                    Double result = null;

                    if (eventTime > 0 && eventTime > lastTime) {
                        if (lastValue == null || lastTime < 0) {
                            // cannot calculate time-based delta with a single value
                            if (LOG.isTraceEnabled()) LOG.trace("{} received event but no last value so will not emit, null -> {} at {}", new Object[] {this, current, eventTime}); 
                        } else {
                            double duration = eventTime - lastTime;
                            result = (current - lastValue.doubleValue()) / (duration / unitMillis);
                        }
                    }

                    lastValue = current;
                    lastTime = eventTime;

                    return result;
                }
            }
        };
    }
    
}

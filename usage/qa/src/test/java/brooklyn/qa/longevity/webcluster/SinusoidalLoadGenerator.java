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
package brooklyn.qa.longevity.webcluster;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;

import com.google.common.base.Throwables;
import com.google.common.reflect.TypeToken;

/**
 * Periodically publishes values in the range of 0 to #amplitude. 
 * The value varies sinusoidally over time.
 */
public class SinusoidalLoadGenerator extends AbstractEnricher {

    private static final Logger LOG = LoggerFactory.getLogger(SinusoidalLoadGenerator.class);

    public static final ConfigKey<AttributeSensor<Double>> TARGET = ConfigKeys.newConfigKey(new TypeToken<AttributeSensor<Double>>() {}, "target");
    
    public static final ConfigKey<Long> PUBLISH_PERIOD_MS = ConfigKeys.newLongConfigKey("publishPeriodMs");

    public static final ConfigKey<Long> SIN_PERIOD_MS = ConfigKeys.newLongConfigKey("sinPeriodMs");

    public static final ConfigKey<Double> SIN_AMPLITUDE = ConfigKeys.newDoubleConfigKey("sinAmplitude");

    private final ScheduledExecutorService executor;
    
    public SinusoidalLoadGenerator() {
        this.executor = Executors.newSingleThreadScheduledExecutor();
    }
    
    public SinusoidalLoadGenerator(AttributeSensor<Double> target, long publishPeriodMs, long sinPeriodMs, double sinAmplitude) {
        setConfig(TARGET, target);
        setConfig(PUBLISH_PERIOD_MS, publishPeriodMs);
        setConfig(SIN_PERIOD_MS, sinPeriodMs);
        setConfig(SIN_AMPLITUDE, sinAmplitude);
        this.executor = Executors.newSingleThreadScheduledExecutor();
    }
    
    @Override
    public void setEntity(final EntityLocal entity) {
        super.setEntity(entity);
        
        executor.scheduleAtFixedRate(new Runnable() {
            @Override public void run() {
                try {
                    long time = System.currentTimeMillis();
                    double val = getRequiredConfig(SIN_AMPLITUDE) * (1 + Math.sin( (1.0*time) / getRequiredConfig(SIN_PERIOD_MS) * Math.PI * 2  - Math.PI/2 )) / 2;
                    entity.setAttribute(getRequiredConfig(TARGET), val);
                } catch (Throwable t) {
                    LOG.warn("Error generating sinusoidal-load metric", t);
                    throw Throwables.propagate(t);
                }
            }

        }, 0, getRequiredConfig(PUBLISH_PERIOD_MS), TimeUnit.MILLISECONDS);
    }
    
    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}

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
package org.apache.brooklyn.policy.ha;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.policy.core.AbstractPolicy;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class ConditionalSuspendPolicy extends AbstractPolicy {
    private static final Logger LOG = LoggerFactory.getLogger(ConditionalSuspendPolicy.class);

    @SetFromFlag("suppressSensor")
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static final ConfigKey<Sensor<?>> SUSPEND_SENSOR = (ConfigKey) ConfigKeys.newConfigKey(Sensor.class,
            "suppressSensor", "Sensor which will suppress the target policy", HASensors.CONNECTION_FAILED); 

    @SetFromFlag("resetSensor")
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static final ConfigKey<Sensor<?>> RESUME_SENSOR = (ConfigKey) ConfigKeys.newConfigKey(Sensor.class,
            "resetSensor", "Resume target policy when this sensor is observed", HASensors.CONNECTION_RECOVERED);

    @SetFromFlag("target")
    public static final ConfigKey<Object> SUSPEND_TARGET = ConfigKeys.newConfigKey(Object.class,
            "target", "The target policy to suspend. Either direct reference or the value of the suspendTarget config on a policy from the same entity.");

    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        Object target = config().get(SUSPEND_TARGET);
        Preconditions.checkNotNull(target, "Suspend target required");
        Preconditions.checkNotNull(getTargetPolicy(), "Can't find target policy set in " + SUSPEND_TARGET.getName() + ": " + target);
        subscribe();
        uniqueTag = JavaClassNames.simpleClassName(getClass())+":"+getConfig(SUSPEND_SENSOR).getName()+":"+getConfig(RESUME_SENSOR).getName();
    }

    private void subscribe() {
        subscribe(entity, getConfig(SUSPEND_SENSOR), new SensorEventListener<Object>() {
            @Override public void onEvent(final SensorEvent<Object> event) {
                if (isRunning()) {
                    Policy target = getTargetPolicy();
                    target.suspend();
                    LOG.debug("Suspended policy " + target + ", triggered by " + event.getSensor() + " = " + event.getValue());
                }
            }

        });
        subscribe(entity, getConfig(RESUME_SENSOR), new SensorEventListener<Object>() {
            @Override public void onEvent(final SensorEvent<Object> event) {
                if (isRunning()) {
                    Policy target = getTargetPolicy();
                    target.resume();
                    LOG.debug("Resumed policy " + target + ", triggered by " + event.getSensor() + " = " + event.getValue());
                }
            }
        });
    }

    private Policy getTargetPolicy() {
        Object target = config().get(SUSPEND_TARGET);
        if (target instanceof Policy) {
            return (Policy)target;
        } else if (target instanceof String) {
            for (Policy policy : entity.getPolicies()) {
                // No way to set config values for keys NOT declared in the policy,
                // so must use displayName as a generally available config value.
                if (target.equals(policy.getDisplayName()) || target.equals(policy.getClass().getName())) {
                    return policy;
                }
            }
        } else {
            throw new IllegalStateException("Unexpected type " + target.getClass() + " for target " + target);
        }
        return null;
    }
}

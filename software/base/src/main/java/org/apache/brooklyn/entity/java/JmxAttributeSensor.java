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
package org.apache.brooklyn.entity.java;

import java.util.concurrent.Callable;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.AddSensor;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.core.sensor.HttpRequestSensor;
import org.apache.brooklyn.core.sensor.ssh.SshCommandSensor;
import org.apache.brooklyn.feed.jmx.JmxAttributePollConfig;
import org.apache.brooklyn.feed.jmx.JmxFeed;
import org.apache.brooklyn.feed.jmx.JmxHelper;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;

/**
 * Configurable {@link org.apache.brooklyn.api.entity.EntityInitializer} which adds a JMX sensor feed to retrieve an
 * <code>attribute</code> from a JMX <code>objectName</code>.
 *
 * @see SshCommandSensor
 * @see HttpRequestSensor
 */
@Beta
public final class JmxAttributeSensor<T> extends AddSensor<T> {

    private static final Logger LOG = LoggerFactory.getLogger(JmxAttributeSensor.class);

    public static final ConfigKey<String> OBJECT_NAME = ConfigKeys.newStringConfigKey("objectName", "JMX object name for sensor lookup");
    public static final ConfigKey<String> ATTRIBUTE = ConfigKeys.newStringConfigKey("attribute", "JMX attribute to poll in object");
    public static final ConfigKey<Object> DEFAULT_VALUE = ConfigKeys.newConfigKey(Object.class, "defaultValue", "Default value for sensor; normally null");

    protected final String objectName;
    protected final String attribute;
    protected final Object defaultValue;

    public JmxAttributeSensor(final ConfigBag params) {
        super(params);
 
        objectName = Preconditions.checkNotNull(params.get(OBJECT_NAME), "objectName");
        attribute = Preconditions.checkNotNull(params.get(ATTRIBUTE), "attribute");
        defaultValue = params.get(DEFAULT_VALUE);

        try {
            ObjectName.getInstance(objectName);
        } catch (MalformedObjectNameException mone) {
            throw new IllegalArgumentException("Malformed JMX object name: " + objectName, mone);
        }
    }

    @Override
    public void apply(final EntityLocal entity) {
        super.apply(entity);

        if (entity instanceof UsesJmx) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Submitting task to add JMX sensor {} to {}", name, entity);
            }

            Task<Integer> jmxPortTask = DependentConfiguration.attributeWhenReady(entity, UsesJmx.JMX_PORT);
            Task<JmxFeed> jmxFeedTask = Tasks.<JmxFeed>builder()
                    .description("Add JMX feed")
                    .body(new Callable<JmxFeed>() {
                        @Override
                        public JmxFeed call() throws Exception {
                            JmxHelper helper = new JmxHelper(entity);
                            Duration period = entity.getConfig(SENSOR_PERIOD);

                            JmxFeed feed = JmxFeed.builder()
                                    .entity(entity)
                                    .period(period)
                                    .helper(helper)
                                    .pollAttribute(new JmxAttributePollConfig<T>(sensor)
                                            .objectName(objectName)
                                            .attributeName(attribute)
                                            .onFailureOrException(Functions.<T>constant((T) defaultValue)))
                                    .build();
                           return feed;
                        }
                    })
                    .build();
            DynamicTasks.submit(Tasks.sequential("Add JMX Sensor " + sensor.getName(), jmxPortTask, jmxFeedTask), entity);
        } else {
            throw new IllegalStateException(String.format("Entity %s does not support JMX", entity));
        }

        // TODO add entity shutdown hook to stop JmxFeed
    }

}

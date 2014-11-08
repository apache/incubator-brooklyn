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
package brooklyn.entity.software.ssh;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.effector.AddSensor;
import brooklyn.entity.proxying.EntityInitializer;
import brooklyn.entity.software.http.HttpRequestSensor;
import brooklyn.entity.software.java.JmxAttributeSensor;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.event.feed.ssh.SshValueFunctions;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

/** 
 * Configurable {@link EntityInitializer} which adds an SSH sensor feed running the <code>command</code> supplied
 * in order to populate the sensor with the indicated <code>name</code>. Note that the <code>targetType</code> is ignored,
 * and always set to {@link String}.
 *
 * @see HttpRequestSensor
 * @see JmxAttributeSensor
 */
// generics introduced here because we might support a configurable 'targetType` parameter in future, 
// with automatic casting (e.g. for ints); this way it remains compatible
@Beta
public final class SshCommandSensor<T extends String> extends AddSensor<String> {

    private static final Logger LOG = LoggerFactory.getLogger(SshCommandSensor.class);

    public static final ConfigKey<String> SENSOR_COMMAND = ConfigKeys.newStringConfigKey("command", "SSH command to execute for sensor");

    protected final String command;

    public SshCommandSensor(final ConfigBag params) {
        super(params.configure(SENSOR_TYPE, "String"));

        // TODO create a supplier for the command string to support attribute embedding
        command = Preconditions.checkNotNull(params.get(SENSOR_COMMAND), "command");
    }

    @Override
    public void apply(final EntityLocal entity) {
        super.apply(entity);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding SSH sensor {} to {}", name, entity);
        }

        Supplier<Map<String,String>> envSupplier = new Supplier<Map<String,String>>() {
            @Override
            public Map<String, String> get() {
                return MutableMap.copyOf(Strings.toStringMap(entity.getConfig(SoftwareProcess.SHELL_ENVIRONMENT)));
            }
        };

        Supplier<String> commandSupplier = new Supplier<String>() {
            @Override
            public String get() {
                String finalCommand = command;
                String runDir = entity.getAttribute(SoftwareProcess.RUN_DIR);
                if (runDir != null) {
                    finalCommand = "cd '"+runDir+"' && "+finalCommand;
                }
                return finalCommand;
            }
        };

        SshPollConfig<String> pollConfig = new SshPollConfig<String>(sensor)
                .period(period)
                .env(envSupplier)
                .command(commandSupplier)
                .checkSuccess(SshValueFunctions.exitStatusEquals(0))
                .onFailureOrException(Functions.constant((String) null))
                .onSuccess(SshValueFunctions.stdout());

        SshFeed.builder()
                .entity(entity)
                .onlyIfServiceUp()
                .poll(pollConfig)
                .build();
    }

}

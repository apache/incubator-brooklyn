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

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.effector.AddSensor;
import brooklyn.entity.proxying.EntityInitializer;
import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.event.feed.ssh.SshValueFunctions;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

/** 
 * Configurable {@link EntityInitializer} which adds an SSH sensor feed running the <code>command</code> supplied
 * in order to populate the sensor with the indicated <code>name</code>.
 */
// generics introduced here because we might support a configurable 'targetType` parameter in future, 
// with automatic casting (e.g. for ints); this way it remains compatible
public final class SshCommandSensor<T extends String> extends AddSensor<String,AttributeSensor<String>> {

    public static final ConfigKey<String> SENSOR_COMMAND = ConfigKeys.newStringConfigKey("command");
    
    String command;
    
    public SshCommandSensor(ConfigBag params) {
        super(newSensor(String.class, params));
        command = Preconditions.checkNotNull(params.get(SENSOR_COMMAND), SENSOR_COMMAND);
    }
    
    @Override
    public void apply(final EntityLocal entity) {
        super.apply(entity);

        Supplier<Map<String,String>> envSupplier = new Supplier<Map<String,String>>() {
            @Override
            public Map<String, String> get() {
                Map<String, String> result = MutableMap.of();
                result.putAll(Strings.toStringMap(entity.getConfig(SoftwareProcess.SHELL_ENVIRONMENT)));
                // TODO any custom env values?
                return result;
            }
        };
        
        Supplier<String> commandSupplier = new Supplier<String>() {
            @Override
            public String get() {
                String finalCommand = command;
                String runDir = entity.getAttribute(SoftwareProcess.RUN_DIR);
                if (runDir!=null) finalCommand = "cd '"+runDir+"'\n"+finalCommand;
                return finalCommand;
            }
        };
        
        Duration period = entity.getConfig(SENSOR_PERIOD);
        
        SshPollConfig<String> pollConfig = new SshPollConfig<String>(sensor)
            .env(envSupplier)
            .command(commandSupplier)
            .checkSuccess(SshValueFunctions.exitStatusEquals(0))
            .onFailureOrException(Functions.constant((String)null))
            .onSuccess(SshValueFunctions.stdout());
        
        if (period!=null) pollConfig.period(period);
        
        SshFeed.builder().entity(entity)
            .onlyIfServiceUp()
            .poll(pollConfig)
            .build();
    }
    
    public SshCommandSensor(Map<String,String> params) {
        this(ConfigBag.newInstance(params));
    }


}

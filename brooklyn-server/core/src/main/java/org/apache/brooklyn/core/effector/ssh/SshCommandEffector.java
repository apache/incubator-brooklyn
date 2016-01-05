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
package org.apache.brooklyn.core.effector.ssh;

import java.util.Map;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.effector.ParameterType;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.AddEffector;
import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.effector.Effectors.EffectorBuilder;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.sensor.ssh.SshCommandSensor;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Preconditions;

public final class SshCommandEffector extends AddEffector {
    
    public static final ConfigKey<String> EFFECTOR_COMMAND = ConfigKeys.newStringConfigKey("command");
    public static final ConfigKey<String> EFFECTOR_EXECUTION_DIR = SshCommandSensor.SENSOR_EXECUTION_DIR;
    
    public SshCommandEffector(ConfigBag params) {
        super(newEffectorBuilder(params).build());
    }
    
    public SshCommandEffector(Map<String,String> params) {
        this(ConfigBag.newInstance(params));
    }

    public static EffectorBuilder<String> newEffectorBuilder(ConfigBag params) {
        EffectorBuilder<String> eff = AddEffector.newEffectorBuilder(String.class, params);
        eff.impl(new Body(eff.buildAbstract(), params));
        return eff;
    }


    protected static class Body extends EffectorBody<String> {
        private final Effector<?> effector;
        private final String command;
        private final String executionDir;

        public Body(Effector<?> eff, ConfigBag params) {
            this.effector = eff;
            this.command = Preconditions.checkNotNull(params.get(EFFECTOR_COMMAND), "command must be supplied when defining this effector");
            this.executionDir = params.get(EFFECTOR_EXECUTION_DIR);
            // TODO could take a custom "env" aka effectorShellEnv
        }

        @Override
        public String call(ConfigBag params) {
            String command = this.command;
            
            command = SshCommandSensor.makeCommandExecutingInDirectory(command, executionDir, entity());
            
            MutableMap<String, String> env = MutableMap.of();
            // first set all declared parameters, including default values
            for (ParameterType<?> param: effector.getParameters()) {
                env.addIfNotNull(param.getName(), Strings.toString( params.get(Effectors.asConfigKey(param)) ));
            }
            
            // then set things from the entities defined shell environment, if applicable
            env.putAll(Strings.toStringMap(entity().getConfig(BrooklynConfigKeys.SHELL_ENVIRONMENT), ""));
            
            // if we wanted to resolve the surrounding environment in real time -- see above
//            Map<String,Object> paramsResolved = (Map<String, Object>) Tasks.resolveDeepValue(effectorShellEnv, Map.class, entity().getExecutionContext());
            
            // finally set the parameters we've been passed; this will repeat declared parameters but to no harm,
            // it may pick up additional values (could be a flag defining whether this is permitted or not)
            env.putAll(Strings.toStringMap(params.getAllConfig()));
            
            SshEffectorTasks.SshEffectorTaskFactory<String> t = SshEffectorTasks.ssh(command)
                .requiringZeroAndReturningStdout()
                .summary("effector "+effector.getName())
                .environmentVariables(env);
            return queue(t).get();
        }
        
    }
    
}

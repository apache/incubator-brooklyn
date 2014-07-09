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
package brooklyn.entity.chef;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.TaskFactory;
import brooklyn.util.task.DynamicTasks;

import com.google.common.annotations.Beta;
import com.google.common.reflect.TypeToken;

/** Driver class to facilitate use of Chef */
@Beta
@Deprecated /** @deprecated since 0.7.0 use ChefEntity or ChefLifecycleEffectorTasks */
public class ChefSoloDriver extends AbstractSoftwareProcessSshDriver implements ChefConfig {

    @SuppressWarnings("serial")
    public static final ConfigKey<TaskFactory<? extends TaskAdaptable<Boolean>>> IS_RUNNING_TASK = ConfigKeys.newConfigKey(
            new TypeToken<TaskFactory<? extends TaskAdaptable<Boolean>>>() {}, 
            "brooklyn.chef.task.driver.isRunningTask");
    
    @SuppressWarnings("serial")
    public static final ConfigKey<TaskFactory<?>> STOP_TASK = ConfigKeys.newConfigKey(
            new TypeToken<TaskFactory<?>>() {}, 
            "brooklyn.chef.task.driver.stopTask");
    
    public ChefSoloDriver(EntityLocal entity, SshMachineLocation location) {
        super(entity, location);
    }

    @Override
    public void install() {
        // TODO flag to force reinstallation
        DynamicTasks.queue(
                ChefSoloTasks.installChef(getInstallDir(), false), 
                ChefSoloTasks.installCookbooks(getInstallDir(), getRequiredConfig(CHEF_COOKBOOKS), false));
    }

    @Override
    public void customize() {
        DynamicTasks.queue(ChefSoloTasks.buildChefFile(getRunDir(), getInstallDir(), "launch", getRequiredConfig(CHEF_RUN_LIST),
                getEntity().getConfig(CHEF_LAUNCH_ATTRIBUTES)));
    }

    @Override
    public void launch() {
        DynamicTasks.queue(ChefSoloTasks.runChef(getRunDir(), "launch", getEntity().getConfig(CHEF_RUN_CONVERGE_TWICE)));
    }

    @Override
    public boolean isRunning() {
        return DynamicTasks.queue(getRequiredConfig(IS_RUNNING_TASK)).asTask().getUnchecked();
    }

    @Override
    public void stop() {
        DynamicTasks.queue(getRequiredConfig(STOP_TASK));
    }

    protected <T> T getRequiredConfig(ConfigKey<T> key) {
        return ChefConfigs.getRequiredConfig(getEntity(), key);
    }
    
}

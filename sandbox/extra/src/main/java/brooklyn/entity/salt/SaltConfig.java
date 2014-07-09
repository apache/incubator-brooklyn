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
package brooklyn.entity.salt;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.SetConfigKey;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.TaskFactory;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.reflect.TypeToken;

/**
 * {@link ConfigKey}s used to configure Salt entities.
 *
 * @see SaltConfigs
 * @see SaltLifecycleEffectorTasks
 */
@Beta
public interface SaltConfig {

    MapConfigKey<String> SALT_FORMULAS = new MapConfigKey<String>(String.class,
            "salt.formulaUrls", "Map of Salt formula URLs (normally GutHub repository archives from the salt-formulas user)");
    SetConfigKey<String> SALT_RUN_LIST = new SetConfigKey<String>(String.class,
            "salt.runList", "Set of Salt states to install from the formula URLs");
    MapConfigKey<Object> SALT_LAUNCH_ATTRIBUTES = new MapConfigKey<Object>(Object.class, "salt.launch.attributes", "TODO");

    @SetFromFlag("master")
    ConfigKey<SaltStackMaster> MASTER = ConfigKeys.newConfigKey(SaltStackMaster.class,
            "salt.master.entity", "The Salt master server");

    AttributeSensor<String> MINION_ID = new BasicAttributeSensor<String>(String.class,
            "salt.minionId", "The ID for a Salt minion");

    @SetFromFlag("masterless")
    ConfigKey<Boolean> MASTERLESS_MODE = ConfigKeys.newBooleanConfigKey(
            "salt.masterless", "Salt masterless, minion only configuration (default uses master and minion)",
            Boolean.FALSE);

    @SetFromFlag("masterConfigUrl")
    ConfigKey<String> MASTER_CONFIGURATION_URL = ConfigKeys.newStringConfigKey(
            "salt.master.templateUrl", "The Salt master configuration file template URL",
            "classpath://brooklyn/entity/salt/master");

    @SetFromFlag("minionConfigUrl")
    ConfigKey<String> MINION_CONFIGURATION_URL = ConfigKeys.newStringConfigKey(
            "salt.minion.templateUrl", "The Salt minion configuration file template URL",
            "classpath://brooklyn/entity/salt/minion");

    @SetFromFlag("masterlessConfigUrl")
    ConfigKey<String> MASTERLESS_CONFIGURATION_URL = ConfigKeys.newStringConfigKey(
            "salt.masterless.templateUrl", "The Salt minion masterless configuration file template URL",
            "classpath://brooklyn/entity/salt/masterless");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SetFromFlag("minionIdFunction")
    ConfigKey<Function<Entity, String>> MINION_ID_FUNCTION = new BasicConfigKey(Function.class,
            "salt.minionId.function", "Function to generate the ID of a Salt minion for an entity", Functions.toStringFunction());

    @SuppressWarnings("serial")
    ConfigKey<TaskFactory<? extends TaskAdaptable<Boolean>>> IS_RUNNING_TASK = ConfigKeys.newConfigKey(
            new TypeToken<TaskFactory<? extends TaskAdaptable<Boolean>>>() {}, 
            "salt.driver.isRunningTask");

    @SuppressWarnings("serial")
    ConfigKey<TaskFactory<?>> STOP_TASK = ConfigKeys.newConfigKey(
            new TypeToken<TaskFactory<?>>() {}, 
            "salt.driver.stopTask");

    /**
     * The {@link SaltStackMaster master} entity.
     */
    SaltStackMaster getMaster();

}

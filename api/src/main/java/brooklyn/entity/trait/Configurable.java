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
package brooklyn.entity.trait;

import org.apache.brooklyn.management.Task;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;

import com.google.common.annotations.Beta;

/**
 * Something that has mutable config, such as an entity or policy.
 * 
 * @author aled
 */
public interface Configurable {

    // FIXME Moved from core project to api project, as part of moving EntityLocal.
    // (though maybe it's fine here?)

    /**
     * @return the old value, or null if there was not one
     * @deprecated since 0.7.0; use {@link ConfigurationSupport#set(ConfigKey, Object)}, such as {@code config().set(key, val)} 
     */
    @Deprecated
    public <T> T setConfig(ConfigKey<T> key, T val);

    ConfigurationSupport config();
    
    @Beta
    public interface ConfigurationSupport {

        /**
         * Gets the given configuration value for this entity, in the following order of precedence:
         * <ol>
         *   <li> value (including null) explicitly set on the entity
         *   <li> value (including null) explicitly set on an ancestor (inherited)
         *   <li> a default value (including null) on the best equivalent static key of the same name declared on the entity
         *        (where best equivalence is defined as preferring a config key which extends another, 
         *        as computed in EntityDynamicType.getConfigKeys)
         *   <li> a default value (including null) on the key itself
         *   <li> null
         * </ol>
         */
        <T> T get(ConfigKey<T> key);
        
        /**
         * @see {@link #getConfig(ConfigKey)}
         */
        <T> T get(HasConfigKey<T> key);

        /**
         * Sets the config to the given value.
         */
        <T> T set(ConfigKey<T> key, T val);
        
        /**
         * @see {@link #setConfig(HasConfigKey, Object)}
         */
        <T> T set(HasConfigKey<T> key, T val);
        
        /**
         * Sets the config to the value returned by the task.
         * 
         * Returns immediately without blocking; subsequent calls to {@link #getConfig(ConfigKey)} 
         * will execute the task, and block until the task completes.
         * 
         * @see {@link #setConfig(ConfigKey, Object)}
         */
        <T> T set(ConfigKey<T> key, Task<T> val);
        
        /**
         * @see {@link #setConfig(ConfigKey, Task)}
         */
        <T> T set(HasConfigKey<T> key, Task<T> val);
    }
}

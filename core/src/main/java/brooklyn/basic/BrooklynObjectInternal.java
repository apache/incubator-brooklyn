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
package brooklyn.basic;

import java.util.Map;

import org.apache.brooklyn.api.basic.BrooklynObject;
import org.apache.brooklyn.api.entity.rebind.RebindSupport;
import org.apache.brooklyn.api.entity.rebind.Rebindable;
import org.apache.brooklyn.api.entity.trait.Configurable;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.guava.Maybe;

import com.google.common.annotations.Beta;

public interface BrooklynObjectInternal extends BrooklynObject, Rebindable {
    
    void setCatalogItemId(String id);
    
    @SuppressWarnings("rawtypes")  // subclasses typically apply stronger typing
    RebindSupport getRebindSupport();
    
    ConfigurationSupportInternal config();

    @Beta
    public interface ConfigurationSupportInternal extends Configurable.ConfigurationSupport {
        
        /**
         * Returns a read-only view of all the config key/value pairs on this entity, backed by a string-based map, 
         * including config names that did not match anything on this entity.
         * 
         * TODO This method gives no information about which config is inherited versus local;
         * this means {@link ConfigKey#getInheritance()} cannot be respected. This is an unsolvable problem
         * for "config names that did not match anything on this entity". Therefore consider using
         * alternative getters.
         */
        @Beta
        ConfigBag getBag();
        
        /**
         * Returns a read-only view of the local (i.e. not inherited) config key/value pairs on this entity, 
         * backed by a string-based map, including config names that did not match anything on this entity.
         */
        @Beta
        ConfigBag getLocalBag();
        
        /**
         * Returns the uncoerced value for this config key, if available, not taking any default.
         * If there is no local value and there is an explicit inherited value, will return the inherited.
         */
        @Beta
        Maybe<Object> getRaw(ConfigKey<?> key);

        /**
         * @see {@link #getConfigRaw(ConfigKey)}
         */
        @Beta
        Maybe<Object> getRaw(HasConfigKey<?> key);

        /**
         * Returns the uncoerced value for this config key, if available,
         * not following any inheritance chains and not taking any default.
         */
        @Beta
        Maybe<Object> getLocalRaw(ConfigKey<?> key);

        /**
         * @see {@link #getLocalConfigRaw(ConfigKey)}
         */
        @Beta
        Maybe<Object> getLocalRaw(HasConfigKey<?> key);

        @Beta
        void addToLocalBag(Map<String, ?> vals);

        @Beta
        void removeFromLocalBag(String key);

        @Beta
        void refreshInheritedConfig();
        
        @Beta
        void refreshInheritedConfigOfChildren();
    }
}

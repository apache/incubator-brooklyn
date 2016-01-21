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
package org.apache.brooklyn.core.objs;

import java.util.Map;

import org.apache.brooklyn.api.mgmt.rebind.RebindSupport;
import org.apache.brooklyn.api.mgmt.rebind.Rebindable;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.Configurable;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.guava.Maybe;

import com.google.common.annotations.Beta;

public interface BrooklynObjectInternal extends BrooklynObject, Rebindable {
    
    void setCatalogItemId(String id);
    
    // subclasses typically apply stronger typing
    RebindSupport<?> getRebindSupport();
    
    @Override
    ConfigurationSupportInternal config();

    @Override
    SubscriptionSupportInternal subscriptions();

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
         * Returns {@link Maybe#absent()} if the key is not explicitly set on this object or an ancestor.
         * <p>
         * See also {@link #getLocalRaw(ConfigKey).
         */
        @Beta
        Maybe<Object> getRaw(ConfigKey<?> key);

        /**
         * @see {@link #getRaw(ConfigKey)}
         */
        @Beta
        Maybe<Object> getRaw(HasConfigKey<?> key);

        /**
         * Returns the uncoerced value for this config key, if available,
         * not following any inheritance chains and not taking any default.
         * Returns {@link Maybe#absent()} if the key is not explicitly set on this object.
         * <p>
         * See also {@link #getRaw(ConfigKey).
         */
        @Beta
        Maybe<Object> getLocalRaw(ConfigKey<?> key);

        /**
         * @see {@link #getLocalRaw(ConfigKey)}
         */
        @Beta
        Maybe<Object> getLocalRaw(HasConfigKey<?> key);

        /**
         * Attempts to coerce the value for this config key, if available,
         * taking a default and {@link Maybe#absent absent} if the uncoerced
         * cannot be resolved within a short timeframe.
         * <p>
         * Note: if no value for the key is available, not even as a default,
         * this returns a {@link Maybe#isPresent()} containing <code>null</code>
         * (following the semantics of {@link #get(ConfigKey)} 
         * rather than {@link #getRaw(ConfigKey)}).
         */
        @Beta
        <T> Maybe<T> getNonBlocking(ConfigKey<T> key);

        /**
         * @see {@link #getNonBlocking(ConfigKey)}
         */
        @Beta
        <T> Maybe<T> getNonBlocking(HasConfigKey<T> key);

        @Beta
        void addToLocalBag(Map<String, ?> vals);

        @Beta
        void removeFromLocalBag(String key);

        @Beta
        void refreshInheritedConfig();

        @Beta
        void refreshInheritedConfigOfChildren();
    }
    
    @Beta
    public interface SubscriptionSupportInternal extends BrooklynObject.SubscriptionSupport {
        public void unsubscribeAll();
    }
    
    RelationSupportInternal<?> relations();
    
    public interface RelationSupportInternal<T extends BrooklynObject> extends BrooklynObject.RelationSupport<T> {
        @Beta
        RelationSupport<T> getLocalBackingStore();
    }
}

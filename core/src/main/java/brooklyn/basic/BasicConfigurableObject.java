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

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.Task;

import brooklyn.camp.brooklyn.api.HasBrooklynManagementContext;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.config.ConfigMap;
import brooklyn.entity.trait.Configurable;
import brooklyn.entity.trait.Identifiable;
import brooklyn.management.ManagementContextInjectable;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.text.Identifiers;

/**
 * A parent class for ancilliary objects that do not require the full heavy lifting of {@link AbstractBrooklynObject}
 * or similar, but wish to use {@link ConfigKey} and {@link Configurable} in their construction, via the
 * {@code $brooklyn:object} method of the CAMP DSL.
 * <p>
 * Type coercion of values will occur when the {@link ConfigMap} is accessed, but resolving of {@link Task tasks} and other
 * deferred operations are assumed to have occurred prior to calling {@link #setConfig(ConfigKey, Object)} i.e. at
 * object construction.
 */
public class BasicConfigurableObject implements Configurable, Identifiable, ManagementContextInjectable, HasBrooklynManagementContext {

    @SetFromFlag("id")
    private String id = Identifiers.makeRandomId(8);

    private volatile ManagementContext managementContext;
    private BasicConfigurationSupport config;
    
    public BasicConfigurableObject() {
        config = new BasicConfigurationSupport();
    }

    @Override
    public void injectManagementContext(ManagementContext managementContext) {
        this.managementContext = managementContext;
    }

    public ManagementContext getBrooklynManagementContext() {
        return managementContext;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ConfigurationSupport config() {
        return config;
    }

    @Override
    @Deprecated
    public <T> T setConfig(ConfigKey<T> key, T value) {
        return config().set(key, value);
    }

    public <T> T getConfig(ConfigKey<T> key) {
        return config().get(key);
    }

    private static class BasicConfigurationSupport implements ConfigurationSupport {
        private final ConfigBag config = ConfigBag.newInstance();

        @Override
        public <T> T get(ConfigKey<T> key) {
            return config.get(key);
        }

        @Override
        public <T> T get(HasConfigKey<T> key) {
            return get(key.getConfigKey());
        }

        @Override
        public <T> T set(ConfigKey<T> key, T val) {
            T old = config.get(key);
            config.configure(key, val);
            return old;
        }

        @Override
        public <T> T set(HasConfigKey<T> key, T val) {
            return set(key.getConfigKey(), val);
        }

        @Override
        public <T> T set(ConfigKey<T> key, Task<T> val) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T set(HasConfigKey<T> key, Task<T> val) {
            return set(key.getConfigKey(), val);
        }
    }
}

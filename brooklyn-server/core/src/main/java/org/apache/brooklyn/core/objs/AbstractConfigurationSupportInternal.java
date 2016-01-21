/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.brooklyn.core.objs;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.mgmt.ExecutionContext;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.core.task.ValueResolver;
import org.apache.brooklyn.util.guava.Maybe;

public abstract class AbstractConfigurationSupportInternal implements BrooklynObjectInternal.ConfigurationSupportInternal {

    @Override
    public <T> T get(HasConfigKey<T> key) {
        return get(key.getConfigKey());
    }

    @Override
    public Maybe<Object> getLocalRaw(HasConfigKey<?> key) {
        return getLocalRaw(key.getConfigKey());
    }

    @Override
    public Maybe<Object> getRaw(HasConfigKey<?> key) {
        return getRaw(key.getConfigKey());
    }

    @Override
    public <T> Maybe<T> getNonBlocking(HasConfigKey<T> key) {
        return getNonBlocking(key.getConfigKey());
    }

    @Override
    public <T> Maybe<T> getNonBlocking(ConfigKey<T> key) {
        // getRaw returns Maybe(val) if the key was explicitly set (where val can be null)
        // or Absent if the config key was unset.
        Object unresolved = getRaw(key).or(key.getDefaultValue());
        final Object marker = new Object();
        // Give tasks a short grace period to resolve.
        Object resolved = Tasks.resolving(unresolved)
                .as(Object.class)
                .defaultValue(marker)
                .timeout(ValueResolver.REAL_REAL_QUICK_WAIT)
                .context(getContext())
                .swallowExceptions()
                .get();
        return (resolved != marker)
               ? TypeCoercions.tryCoerce(resolved, key.getTypeToken())
               : Maybe.<T>absent();
    }

    @Override
    public <T> T set(HasConfigKey<T> key, Task<T> val) {
        return set(key.getConfigKey(), val);
    }

    @Override
    public <T> T set(HasConfigKey<T> key, T val) {
        return set(key.getConfigKey(), val);
    }

    /**
     * @return An execution context for use by {@link #getNonBlocking(ConfigKey)}
     */
    @Nullable
    protected abstract ExecutionContext getContext();
}

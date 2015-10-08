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

import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
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
    public <T> T set(HasConfigKey<T> key, Task<T> val) {
        return set(key.getConfigKey(), val);
    }

    @Override
    public <T> T set(HasConfigKey<T> key, T val) {
        return set(key.getConfigKey(), val);
    }

}

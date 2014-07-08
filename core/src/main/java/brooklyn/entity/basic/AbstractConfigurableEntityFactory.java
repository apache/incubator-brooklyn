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
package brooklyn.entity.basic;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;

public abstract class AbstractConfigurableEntityFactory<T extends Entity> implements ConfigurableEntityFactory<T>, Serializable {
    protected final Map config = new LinkedHashMap();

    public AbstractConfigurableEntityFactory(){
        this(new HashMap());
    }

    public AbstractConfigurableEntityFactory(Map flags) {
        this.config.putAll(flags);

    }
    public AbstractConfigurableEntityFactory<T> configure(Map flags) {
        config.putAll(flags);
        return this;
    }

    public AbstractConfigurableEntityFactory<T> configure(ConfigKey key, Object value) {
        config.put(key, value);
        return this;
    }

    public AbstractConfigurableEntityFactory<T> configure(ConfigKey.HasConfigKey key, Object value) {
        return setConfig(key.getConfigKey(), value);
    }

    public AbstractConfigurableEntityFactory<T> setConfig(ConfigKey key, Object value) {
        return configure(key, value);
    }

    public AbstractConfigurableEntityFactory<T> setConfig(ConfigKey.HasConfigKey key, Object value) {
        return configure(key.getConfigKey(), value);
    }

    public T newEntity(Entity parent){
        return newEntity(new HashMap(),parent);
    }

    public T newEntity(Map flags, Entity parent) {
        Map flags2 = new HashMap();
        flags2.putAll(config);
        flags2.putAll(flags);
        return newEntity2(flags2, parent);
    }

    public abstract T newEntity2(Map flags, Entity parent);
}


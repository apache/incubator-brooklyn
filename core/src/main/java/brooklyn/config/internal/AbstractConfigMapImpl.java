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
package brooklyn.config.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigMap;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
import org.apache.brooklyn.core.util.flags.TypeCoercions;
import org.apache.brooklyn.core.util.task.DeferredSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.ConfigMapViewWithStringKeys;
import brooklyn.event.basic.StructuredConfigKey;

public abstract class AbstractConfigMapImpl implements ConfigMap {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractConfigMapImpl.class);
    
    protected final ConfigMapViewWithStringKeys mapViewWithStringKeys = new ConfigMapViewWithStringKeys(this);
    
    /**
     * Map of configuration information that is defined at start-up time for the entity. These
     * configuration parameters are shared and made accessible to the "children" of this
     * entity.
     */
    protected Map<ConfigKey<?>,Object> ownConfig = Collections.synchronizedMap(new LinkedHashMap<ConfigKey<?>, Object>());

    public <T> T getConfig(ConfigKey<T> key) {
        return getConfig(key, null);
    }
    
    public <T> T getConfig(HasConfigKey<T> key) {
        return getConfig(key.getConfigKey(), null);
    }
    
    public <T> T getConfig(HasConfigKey<T> key, T defaultValue) {
        return getConfig(key.getConfigKey(), defaultValue);
    }
    
    @Override @Deprecated
    public Object getRawConfig(ConfigKey<?> key) {
        return getConfigRaw(key, true).orNull();
    }
    
    protected Object coerceConfigVal(ConfigKey<?> key, Object v) {
        Object val;
        if ((v instanceof Future) || (v instanceof DeferredSupplier)) {
            // no coercion for these (coerce on exit)
            val = v;
        } else if (key instanceof StructuredConfigKey) {
            // no coercion for these structures (they decide what to do)
            val = v;
        } else if ((v instanceof Map || v instanceof Iterable) && key.getType().isInstance(v)) {
            // don't do coercion on put for these, if the key type is compatible, 
            // because that will force resolution deeply
            val = v;
        } else {
            try {
                // try to coerce on input, to detect errors sooner
                val = TypeCoercions.coerce(v, key.getTypeToken());
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot coerce or set "+v+" to "+key, e);
                // if can't coerce, we could just log as below and *throw* the error when we retrieve the config
                // but for now, fail fast (above), because we haven't encountered strong use cases
                // where we want to do coercion on retrieval, except for the exceptions above
//                Exceptions.propagateIfFatal(e);
//                LOG.warn("Cannot coerce or set "+v+" to "+key+" (ignoring): "+e, e);
//                val = v;
            }
        }
        return val;
    }

    
    @Override
    public Map<String,Object> asMapWithStringKeys() {
        return mapViewWithStringKeys;
    }

    @Override
    public int size() {
        return ownConfig.size();
    }

    @Override
    public boolean isEmpty() {
        return ownConfig.isEmpty();
    }
}

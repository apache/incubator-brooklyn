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
package org.apache.brooklyn.core.mgmt.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.brooklyn.api.mgmt.ExecutionContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.core.task.DeferredSupplier;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;

/**
 * Delegates to another {@link BrooklynProperties} implementation, but intercepts all calls to get.
 * The results are transformed: if they are in the external-config format then they are 
 * automatically converted to {@link DeferredSupplier}.
 * 
 * The external-config format is that same as that for camp-yaml blueprints (i.e. 
 * {@code $brooklyn:external("myprovider", "mykey")}.
 */
public class DeferredBrooklynProperties implements BrooklynProperties {

    private static final Logger LOG = LoggerFactory.getLogger(DeferredBrooklynProperties.class);

    private static final String BROOKLYN_YAML_PREFIX = "$brooklyn:";
    
    private final BrooklynProperties delegate;
    private final ManagementContextInternal mgmt;

    public DeferredBrooklynProperties(BrooklynProperties delegate, ManagementContextInternal mgmt) {
        this.delegate = checkNotNull(delegate, "delegate");
        this.mgmt = checkNotNull(mgmt, "mgmt");
    }
    
    private Object transform(ConfigKey<?> key, Object value) {
        if (value instanceof CharSequence) {
            String raw = value.toString();
            if (raw.startsWith(BROOKLYN_YAML_PREFIX)) {
                CampYamlParser parser = mgmt.getConfig().getConfig(CampYamlParser.YAML_PARSER_KEY);
                if (parser == null) {
                    // TODO Should we fail or return the untransformed value?
                    // Problem is this gets called during initialisation, e.g. by BrooklynFeatureEnablement calling asMapWithStringKeys()
                    // throw new IllegalStateException("Cannot parse external-config for "+key+" because no camp-yaml parser available");
                    LOG.debug("Not transforming external-config {}, as no camp-yaml parser available", key);
                    return value;
                }
                return parser.parse(raw);
            }
        }
        return value;
    }
    
    private <T> T resolve(ConfigKey<T> key, Object value) {
        Object transformed = transform(key, value);

        Object result;
        if (transformed instanceof DeferredSupplier) {
            ExecutionContext exec = mgmt.getServerExecutionContext();
            try {
                result = Tasks.resolveValue(transformed, key.getType(), exec);
            } catch (ExecutionException | InterruptedException e) {
                throw Exceptions.propagate(e);
            }
        } else {
            result = transformed;
        }

        return TypeCoercions.coerce(result, key.getTypeToken());
    }
    
    @Override
    public <T> T getConfig(ConfigKey<T> key) {
        T raw = delegate.getConfig(key);
        return resolve(key, raw);
    }

    @Override
    public <T> T getConfig(HasConfigKey<T> key) {
        T raw = delegate.getConfig(key);
        return resolve(key.getConfigKey(), raw);
    }

    @Override
    public <T> T getConfig(HasConfigKey<T> key, T defaultValue) {
        T raw = delegate.getConfig(key, defaultValue);
        return resolve(key.getConfigKey(), raw);
    }

    @Override
    public <T> T getConfig(ConfigKey<T> key, T defaultValue) {
        T raw = delegate.getConfig(key, defaultValue);
        return resolve(key, raw);
    }

    @Deprecated
    @Override
    public Object getRawConfig(ConfigKey<?> key) {
        return transform(key, delegate.getRawConfig(key));
    }
    
    @Override
    public Maybe<Object> getConfigRaw(ConfigKey<?> key, boolean includeInherited) {
        Maybe<Object> result = delegate.getConfigRaw(key, includeInherited);
        return (result.isPresent()) ? Maybe.of(transform(key, result.get())) : Maybe.absent();
    }

    @Override
    public Map<ConfigKey<?>, Object> getAllConfig() {
        Map<ConfigKey<?>, Object> raw = delegate.getAllConfig();
        Map<ConfigKey<?>, Object> result = Maps.newLinkedHashMap();
        for (Map.Entry<ConfigKey<?>, Object> entry : raw.entrySet()) {
            result.put(entry.getKey(), transform(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    @Override
    public Map<String, Object> asMapWithStringKeys() {
        Map<ConfigKey<?>, Object> raw = delegate.getAllConfig();
        Map<String, Object> result = Maps.newLinkedHashMap();
        for (Map.Entry<ConfigKey<?>, Object> entry : raw.entrySet()) {
            result.put(entry.getKey().getName(), transform(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /**
     * Discouraged; returns the String so if it is external config, it will be the 
     * {@code $brooklyn:external(...)} format.
     */
    @Override
    @SuppressWarnings("rawtypes")
    @Deprecated
    public String get(Map flags, String key) {
        return delegate.get(flags, key);
    }

    /**
     * Discouraged; returns the String so if it is external config, it will be the 
     * {@code $brooklyn:external(...)} format.
     */
    @Override
    public String getFirst(String ...keys) {
        return delegate.getFirst(keys);
    }
    
    /**
     * Discouraged; returns the String so if it is external config, it will be the 
     * {@code $brooklyn:external(...)} format.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public String getFirst(Map flags, String ...keys) {
        return delegate.getFirst(flags, keys);
    }

    @Override
    public BrooklynProperties submap(Predicate<ConfigKey<?>> filter) {
        BrooklynProperties submap = delegate.submap(filter);
        return new DeferredBrooklynProperties(submap, mgmt);
    }

    @Override
    public BrooklynProperties addEnvironmentVars() {
        delegate.addEnvironmentVars();
        return this;
    }

    @Override
    public BrooklynProperties addSystemProperties() {
        delegate.addSystemProperties();
        return this;
    }

    @Override
    public BrooklynProperties addFrom(ConfigBag cfg) {
        delegate.addFrom(cfg);
        return this;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public BrooklynProperties addFrom(Map map) {
        delegate.addFrom(map);
        return this;
    }

    @Override
    public BrooklynProperties addFrom(InputStream i) {
        delegate.addFrom(i);
        return this;
    }
    
    @Override
    public BrooklynProperties addFrom(File f) {
        delegate.addFrom(f);
        return this;
    }
    
    @Override
    public BrooklynProperties addFrom(URL u) {
        delegate.addFrom(u);
        return this;
    }

    @Override
    public BrooklynProperties addFromUrl(String url) {
        delegate.addFromUrl(url);
        return this;
    }

    @Override
    public BrooklynProperties addFromUrlProperty(String urlProperty) {
        delegate.addFromUrlProperty(urlProperty);
        return this;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public BrooklynProperties addFromMap(Map properties) {
        delegate.addFromMap(properties);
        return this;
    }

    @Override
    public boolean putIfAbsent(String key, Object value) {
        return delegate.putIfAbsent(key, value);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public Object put(Object key, Object value) {
        return delegate.put(key, value);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void putAll(Map vals) {
        delegate.putAll(vals);
    }
    
    @Override
    public <T> Object put(HasConfigKey<T> key, T value) {
        return delegate.put(key, value);
    }

    @Override
    public <T> Object put(ConfigKey<T> key, T value) {
        return delegate.put(key, value);
    }
    
    @Override
    public <T> boolean putIfAbsent(ConfigKey<T> key, T value) {
        return delegate.putIfAbsent(key, value);
    }
    
    
    //////////////////////////////////////////////////////////////////////////////////
    // Methods below from java.util.LinkedHashMap, which BrooklynProperties extends //
    //////////////////////////////////////////////////////////////////////////////////
    
    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public Object get(Object key) {
        return delegate.get(key);
    }

    @Override
    public Object remove(Object key) {
        return delegate.remove(key);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Set keySet() {
        return delegate.keySet();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Collection values() {
        return delegate.values();
    }
    
    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Set<Map.Entry> entrySet() {
        return delegate.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
    
    // put(Object, Object) already overridden
    //@Override
    //public Object put(Object key, Object value) {

    // putAll(Map) already overridden
    //@Override
    //public void putAll(Map m) {
}

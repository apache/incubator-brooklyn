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
package brooklyn.event.basic;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.management.ExecutionContext;

import com.google.common.collect.Maps;

public abstract class AbstractStructuredConfigKey<T,RawT,V> extends BasicConfigKey<T> implements StructuredConfigKey {

    private static final long serialVersionUID = 7806267541029428561L;

    public final Class<V> subType;

    public AbstractStructuredConfigKey(Class<T> type, Class<V> subType, String name, String description, T defaultValue) {
        super(type, name, description, defaultValue);
        this.subType = subType;
    }

    protected ConfigKey<V> subKey(String subName) {
        return subKey(subName, "sub-element of " + getName() + ", named " + subName);
    }
    // it is not possible to supply default values
    protected ConfigKey<V> subKey(String subName, String description) {
        return new SubElementConfigKey<V>(this, subType, getName() + "." + subName, description, null);
    }   

    protected static String getKeyName(Object contender) {
        if (contender==null) return null;
        if (contender instanceof ConfigKey) return ((ConfigKey<?>)contender).getName();
        return contender.toString();
    }
    
    public boolean acceptsKeyMatch(Object contender) {
        return (getName().equalsIgnoreCase(getKeyName(contender)));
    }
    
    public boolean acceptsSubkey(Object contender) {
        return contender!=null && getKeyName(contender).startsWith(getName()+".");        
    }
    
    public String extractSubKeyName(Object o) {
        String name = getKeyName(o);
        assert name.startsWith(getName()+".");
        return name.substring(getName().length() + 1);
    }

    @Override
    public boolean acceptsSubkeyStronglyTyped(Object contender) {
        return (contender instanceof SubElementConfigKey) && 
            acceptsKeyMatch( ((SubElementConfigKey<?>) contender).parent );
    }
    
    @Override
    public boolean isSet(Map<?, ?> vals) {
        if (vals.containsKey(this))
            return true;
        for (Object contender : vals.keySet()) {
            if (acceptsKeyMatch(contender) || acceptsSubkey(contender)) {
                return true;
            }
        }
        return false;
    }

    protected RawT extractValue(Map<?,?> vals, ExecutionContext exec, boolean coerce, boolean unmodifiable) {
        RawT base = null; 
        Map<String,Object> subkeys = Maps.newLinkedHashMap();
        for (Map.Entry<?,?> entry : vals.entrySet()) {
            Object k = entry.getKey();
            
            if (acceptsKeyMatch(k)) {
                base = extractValueMatchingThisKey(entry.getValue(), exec, coerce);
            }
            
            if (acceptsSubkey(k)) {
                String subKeyName = extractSubKeyName(k);
                Object value;
                if (coerce) {
                    @SuppressWarnings("unchecked")
                    SubElementConfigKey<V> kk = k instanceof SubElementConfigKey<?> ? 
                        (SubElementConfigKey<V>) k : (SubElementConfigKey<V>) subKey(subKeyName);
                    value = kk.extractValue(vals, exec);
                } else {
                    value = vals.get(k);
                }
                subkeys.put(subKeyName, value);
            }
        }
        return merge(base, subkeys, unmodifiable);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T extractValue(Map<?,?> vals, ExecutionContext exec) {
        return (T) extractValue(vals, exec, true, true);
    }

    /** returns the entries in the map against this config key and any sub-config-keys, without resolving
     * (like {@link #extractValue(Map, ExecutionContext)} but without resolving/coercing;
     * useful because values in this "map" are actually stored against {@link SubElementConfigKey}s */
    public RawT rawValue(Map<?,?> vals) {
        return extractValue(vals, null, false, false);
    }

    /** returns value against *this* key, if it is of an acceptable type (ignoring subkeys which are added on top) */
    protected abstract RawT extractValueMatchingThisKey(Object potentialBase, ExecutionContext exec, boolean coerce);
    
    protected abstract RawT merge(RawT base, Map<String, Object> subkeys, boolean unmodifiable);
    
}

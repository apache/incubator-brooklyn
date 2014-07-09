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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.util.collections.Jsonya;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.Maps;

/** A config key which represents a map, where contents can be accessed directly via subkeys.
 * Items added directly to the map must be of type map, and can be updated by:
 * <ul>
 * <li>Putting individual subkeys ({@link SubElementConfigKey})
 * <li>Passing an an appropriate {@link MapModification} from {@link MapModifications}
 *      to clear, clear-and-set, or update
 * <li>Setting a value against a dot-extension of the key
 *     (e.g. setting <code>a.map.subkey=1</code> will cause getConfig(a.map[type=MapConfigKey])
 *     to return {subkey=1}; but note the above are preferred where possible)  
 * <li>Setting a map directly against the MapConfigKey (but note that the above are preferred where possible)
 * </ul>
 */
//TODO Create interface
public class MapConfigKey<V> extends AbstractStructuredConfigKey<Map<String,V>,Map<String,Object>,V> {
    
    private static final long serialVersionUID = -6126481503795562602L;
    private static final Logger log = LoggerFactory.getLogger(MapConfigKey.class);
    
    public MapConfigKey(Class<V> subType, String name) {
        this(subType, name, name, null);
    }

    public MapConfigKey(Class<V> subType, String name, String description) {
        this(subType, name, description, null);
    }

    // TODO it isn't clear whether defaultValue is an initialValue, or a value to use when map is empty
    // probably the latter, currently ... but maybe better to say that map configs are never null, 
    // and defaultValue is really an initial value?
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public MapConfigKey(Class<V> subType, String name, String description, Map<String, V> defaultValue) {
        super((Class)Map.class, subType, name, description, defaultValue);
    }

    public ConfigKey<V> subKey(String subName) {
        return super.subKey(subName);
    }
    public ConfigKey<V> subKey(String subName, String description) {
        return super.subKey(subName, description);
    }   

    @SuppressWarnings("unchecked")
    @Override
    protected Map<String, Object> extractValueMatchingThisKey(Object potentialBase, ExecutionContext exec, boolean coerce) {
        if (potentialBase instanceof Map<?,?>) {
            return Maps.<String,Object>newLinkedHashMap( (Map<String,Object>) potentialBase);
        } else if (coerce) {
            // TODO if it's a future could attempt type coercion
            // (e.g. if we have a MapConfigKey we use to set dependent configuration
        }
        return null;
    }
    
    @Override
    protected Map<String, Object> merge(Map<String, Object> base, Map<String, Object> subkeys, boolean unmodifiable) {
        Map<String, Object> result = MutableMap.copyOf(base).add(subkeys);
        if (unmodifiable) result = Collections.unmodifiableMap(result);
        return result;
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Object applyValueToMap(Object value, Map target) {
        if (value == null)
            return null;
        if (value instanceof StructuredModification)
            return ((StructuredModification)value).applyToKeyInMap(this, target);
        if (value instanceof Map.Entry)
            return applyEntryValueToMap((Map.Entry)value, target);
        if (!(value instanceof Map)) 
            throw new IllegalArgumentException("Cannot set non-map entries "+value+" on "+this);
        
        Map result = new MutableMap();
        for (Object entry: ((Map)value).entrySet()) {
            Map.Entry entryT = (Map.Entry)entry;
            result.put(entryT.getKey(), applyEntryValueToMap(entryT, target));
        }
        if (((Map)value).isEmpty() && !isSet(target))
            target.put(this, MutableMap.of());
        return result;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Object applyEntryValueToMap(Entry value, Map target) {
        Object k = value.getKey();
        if (acceptsSubkeyStronglyTyped(k)) {
            // do nothing
        } else if (k instanceof ConfigKey<?>) {
            k = subKey( ((ConfigKey<?>)k).getName() );
        } else if (k instanceof String) {
            k = subKey((String)k);
        } else {
            log.warn("Unexpected subkey "+k+" being inserted into "+this+"; ignoring");
            k = null;
        }
        if (k!=null)
            return target.put(k, value.getValue());
        else 
            return null;
    }

    public interface MapModification<V> extends StructuredModification<MapConfigKey<V>>, Map<String,V> {
    }
    
    public static class MapModifications extends StructuredModifications {
        /** when passed as a value to a MapConfigKey, causes each of these items to be put 
         * (this Mod is redundant as no other value is really sensible) */
        public static final <V> MapModification<V> put(final Map<String,V> itemsToPutInMapReplacing) { 
            return new MapModificationBase<V>(itemsToPutInMapReplacing, false);
        }
        /** when passed as a value to a MapConfigKey, causes the map to be cleared and these items added */
        public static final <V> MapModification<V> set(final Map<String,V> itemsToPutInMapAfterClearing) {
            return new MapModificationBase<V>(itemsToPutInMapAfterClearing, true);
        }
        /** when passed as a value to a MapConfigKey, causes the items to be added to the underlying map
         * using {@link Jsonya} add semantics (combining maps and lists) */
        public static final <V> MapModification<V> add(final Map<String,V> itemsToAdd) {
            return new MapModificationBase<V>(itemsToAdd, false /* ignored */) {
                private static final long serialVersionUID = 1L;
                @SuppressWarnings("rawtypes")
                @Override
                public Object applyToKeyInMap(MapConfigKey<V> key, Map target) {
                    return key.applyValueToMap(Jsonya.of(key.rawValue(target)).add(this).getRootMap(), target);
                }
            };
        }
    }

    public static class MapModificationBase<V> extends LinkedHashMap<String,V> implements MapModification<V> {
        private static final long serialVersionUID = -1670820613292286486L;
        private final boolean clearFirst;
        public MapModificationBase(Map<String,V> delegate, boolean clearFirst) {
            super(delegate);
            this.clearFirst = clearFirst;
        }
        @SuppressWarnings({ "rawtypes" })
        @Override
        public Object applyToKeyInMap(MapConfigKey<V> key, Map target) {
            if (clearFirst) {
                StructuredModification<StructuredConfigKey> clearing = StructuredModifications.clearing();
                clearing.applyToKeyInMap(key, target);
            }
            return key.applyValueToMap(new LinkedHashMap<String,V>(this), target);
        }
    }
}

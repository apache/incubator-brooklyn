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

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

import brooklyn.config.ConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.management.TaskAdaptable;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.text.Identifiers;

public abstract class AbstractCollectionConfigKey<T, RawT extends Collection<Object>, V> extends AbstractStructuredConfigKey<T, RawT, V> {

    private static final long serialVersionUID = 8225955960120637643L;
    private static final Logger log = LoggerFactory.getLogger(AbstractCollectionConfigKey.class);
    
    public AbstractCollectionConfigKey(Class<T> type, Class<V> subType, String name, String description, T defaultValue) {
        super(type, subType, name, description, defaultValue);
    }

    public ConfigKey<V> subKey() {
        String subName = Identifiers.makeRandomId(8);
        return new SubElementConfigKey<V>(this, subType, getName()+"."+subName, "element of "+getName()+", uid "+subName, null);
    }

    protected abstract RawT merge(boolean unmodifiable, Iterable<?> ...items);

    @Override
    protected RawT merge(RawT base, Map<String, Object> subkeys, boolean unmodifiable) {
        return merge(unmodifiable, base, subkeys.values());
    }

    @Override
    protected RawT extractValueMatchingThisKey(Object potentialBase, ExecutionContext exec, boolean coerce) {
        if (potentialBase instanceof Map<?,?>) {
            return merge(false, ((Map<?,?>) potentialBase).values() );
        } else if (potentialBase instanceof Collection<?>) {
            return merge(false, (Collection<?>) potentialBase );
        } else if (coerce) {
            // TODO if it's a future could attempt type coercion
            // (e.g. if we have a MapConfigKey we use to set dependent configuration
        }
        return null;
    }

    @SuppressWarnings({ "rawtypes" })
    @Override
    public Object applyValueToMap(Object value, Map target) {
        return applyValueToMap(value, target, false);
    }
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Object applyValueToMap(Object value, Map target, boolean isInCollection) {
        if (value instanceof StructuredModification) {
            return ((StructuredModification)value).applyToKeyInMap(this, target);
        } else if ((value instanceof Iterable) && (!isInCollection)) {
            // collections set _here_ (not in subkeys) get added
            boolean isSet = isSet(target);
            if (isSet) {
                String warning = "Discouraged undecorated setting of a collection to in-use StructuredConfigKey "+this+": use SetModification.{set,add}. " +
                    "Defaulting to 'add'. Look at debug logging for call stack.";
                log.warn(warning);
                if (log.isDebugEnabled())
                    log.debug("Trace for: "+warning, new Throwable("Trace for: "+warning));
            }
            Iterable<?> valueI = (Iterable<?>)value;
            for (Object v: valueI) { 
                // don't continue to recurse into these collections, however
                applyValueToMap(v, target, true);
            }
            if (Iterables.isEmpty(valueI) && !isSet) {
                target.put(this, MutableSet.of());
            }
            return null;
        } else if (value instanceof TaskAdaptable) {
            boolean isSet = isSet(target);
            if (isSet) {
                String warning = "Discouraged undecorated setting of a task to in-use StructuredConfigKey "+this+": use SetModification.{set,add}. " +
                    "Defaulting to 'add'. Look at debug logging for call stack.";
                log.warn(warning);
                if (log.isDebugEnabled())
                    log.debug("Trace for: "+warning, new Throwable("Trace for: "+warning));
            }
            // just add to set, using anonymous key
            target.put(subKey(), value);
            return null;
        } else {
            // just add to set, using anonymous key
            target.put(subKey(), value);
            return null;
        }
    }
    
}

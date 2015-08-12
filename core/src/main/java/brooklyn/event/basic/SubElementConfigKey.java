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

import org.apache.brooklyn.management.ExecutionContext;

import brooklyn.config.ConfigKey;
import brooklyn.util.exceptions.Exceptions;

@SuppressWarnings("rawtypes")
public class SubElementConfigKey<T> extends BasicConfigKey<T> {
    
    private static final long serialVersionUID = -1587240876351450665L;
    
    public final ConfigKey parent;

    public SubElementConfigKey(ConfigKey parent, Class<T> type, String name) {
        this(parent, type, name, name, null);
    }
    public SubElementConfigKey(ConfigKey parent, Class<T> type, String name, String description) {
        this(parent, type, name, description, null);
    }
    public SubElementConfigKey(ConfigKey parent, Class<T> type, String name, String description, T defaultValue) {
        super(type, name, description, defaultValue);
        this.parent = parent;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public T extractValue(Map vals, ExecutionContext exec) {
        if (vals.containsKey(this)) return super.extractValue(vals, exec);
        if (parent instanceof StructuredConfigKey) {
            // look for subkey in map at parent, in the event that the parent was set as an unstructured key
            Object parentVals = vals.get(parent);
            if (parentVals instanceof Map) {
                String subName = getName().substring(parent.getName().length()+1);
                if ( ((Map) parentVals).containsKey(subName) ) {
                    try {
                        return (T) resolveValue( ((Map) parentVals).get(subName), exec );
                    } catch (Exception e) { throw Exceptions.propagate(e); }
                }
            }
        }
        return null;
    }
    
    @Override
    public boolean isSet(Map<?,?> vals) {
        if (super.isSet(vals)) return true;
        if (parent instanceof StructuredConfigKey) {
            // look for subkey in map at parent, in the event that the parent was set as an unstructured key
            Object parentVals = vals.get(parent);
            if (parentVals instanceof Map) {
                String subName = getName().substring(parent.getName().length()+1);
                if ( ((Map) parentVals).containsKey(subName) ) return true;
            }
        }
        return false;
    }
}

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
package org.apache.brooklyn.core.effector;

import java.util.Collections;
import java.util.Map;

import org.apache.brooklyn.api.effector.ParameterType;

import com.google.common.base.Objects;

public class BasicParameterType<T> implements ParameterType<T> {
    private static final long serialVersionUID = -5521879180483663919L;
    
    private String name;
    private Class<T> type;
    private String description;
    private Boolean hasDefaultValue = null;
    private T defaultValue = null;

    public BasicParameterType() {
        this(Collections.emptyMap());
    }
    
    @SuppressWarnings("unchecked")
    public BasicParameterType(Map<?, ?> arguments) {
        if (arguments.containsKey("name")) name = (String) arguments.get("name");
        if (arguments.containsKey("type")) type = (Class<T>) arguments.get("type");
        if (arguments.containsKey("description")) description = (String) arguments.get("description");
        if (arguments.containsKey("defaultValue")) defaultValue = (T) arguments.get("defaultValue");
    }

    public BasicParameterType(String name, Class<T> type) {
        this(name, type, null, null, false);
    }
    
    public BasicParameterType(String name, Class<T> type, String description) {
        this(name, type, description, null, false);
    }
    
    public BasicParameterType(String name, Class<T> type, String description, T defaultValue) {
        this(name, type, description, defaultValue, true);
    }
    
    public BasicParameterType(String name, Class<T> type, String description, T defaultValue, boolean hasDefaultValue) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.defaultValue = defaultValue;
        if (defaultValue!=null && !defaultValue.getClass().equals(Object.class)) {
            // if default value is null (or is an Object, which is ambiguous on resolution to to rebind), 
            // don't bother to set this as it creates noise in the persistence files
            this.hasDefaultValue = hasDefaultValue;
        }
    }

    @Override
    public String getName() { return name; }

    @Override
    public Class<T> getParameterClass() { return type; }
    
    @Override
    public String getParameterClassName() { return type.getCanonicalName(); }

    @Override
    public String getDescription() { return description; }

    @Override
    public T getDefaultValue() {
        return hasDefaultValue() ? defaultValue : null;
    }

    public boolean hasDefaultValue() {
        // a new Object() was previously used to indicate no default value, but that doesn't work well across serialization boundaries!
        return hasDefaultValue!=null ? hasDefaultValue : defaultValue!=null && !defaultValue.getClass().equals(Object.class);
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).omitNullValues()
                .add("name", name).add("description", description).add("type", getParameterClassName())
                .add("defaultValue", defaultValue)
                .toString();
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(name, description, type, defaultValue);
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof ParameterType) &&
                Objects.equal(name, ((ParameterType<?>)obj).getName()) &&
                Objects.equal(description, ((ParameterType<?>)obj).getDescription()) &&
                Objects.equal(type, ((ParameterType<?>)obj).getParameterClass()) &&
                Objects.equal(defaultValue, ((ParameterType<?>)obj).getDefaultValue());
    }
}

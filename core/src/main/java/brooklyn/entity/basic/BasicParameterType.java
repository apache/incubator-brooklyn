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

import java.util.Collections;
import java.util.Map;

import brooklyn.entity.ParameterType;

import com.google.common.base.Objects;

/**
 * TODO javadoc
 */
public class BasicParameterType<T> implements ParameterType<T> {
    private static final long serialVersionUID = -5521879180483663919L;
    
    private String name;
    private Class<T> type;
    private String description;
    private T defaultValue = (T) NONE;

    public BasicParameterType() {
        this(Collections.emptyMap());
    }
    
    public BasicParameterType(Map<?, ?> arguments) {
        if (arguments.containsKey("name")) name = (String) arguments.get("name");
        if (arguments.containsKey("type")) type = (Class<T>) arguments.get("type");
        if (arguments.containsKey("description")) description = (String) arguments.get("description");
        if (arguments.containsKey("defaultValue")) defaultValue = (T) arguments.get("defaultValue");
    }

    public BasicParameterType(String name, Class<T> type) {
        this(name, type, null, (T) NONE);
    }
    
    public BasicParameterType(String name, Class<T> type, String description) {
        this(name, type, description, (T) NONE);
    }
    
    public BasicParameterType(String name, Class<T> type, String description, T defaultValue) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.defaultValue = defaultValue;
    }

    private static Object NONE = new Object();
    
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
        return defaultValue != NONE;
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

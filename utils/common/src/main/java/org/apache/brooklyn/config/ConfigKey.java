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
package org.apache.brooklyn.config;

import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;
import com.google.common.reflect.TypeToken;

/**
 * Represents the name of a piece of typed configuration data for an entity.
 * <p>
 * Two ConfigKeys should be considered equal if they have the same FQN.
 */
public interface ConfigKey<T> {
    /**
     * Returns the description of the configuration parameter, for display.
     */
    String getDescription();

    /**
     * Returns the name of the configuration parameter, in a dot-separated namespace (FQN).
     */
    String getName();

    /**
     * Returns the constituent parts of the configuration parameter name as a {@link Collection}.
     */
    Collection<String> getNameParts();

    /**
     * Returns the Guava TypeToken, including info on generics.
     */
    TypeToken<T> getTypeToken();
    
    /**
     * Returns the type of the configuration parameter data.
     * <p> 
     * This returns a "super" of T only in the case where T is generified, 
     * and in such cases it returns the Class instance for the unadorned T ---
     * i.e. for List&lt;String&gt; this returns Class&lt;List&gt; ---
     * this is of course because there is no actual Class&lt;List&lt;String&gt;&gt; instance.
     */
    Class<? super T> getType();

    /**
     * Returns the name of of the configuration parameter data type, as a {@link String}.
     */
    String getTypeName();

    /**
     * Returns the default value of the configuration parameter.
     */
    T getDefaultValue();

    /**
     * Returns true if a default configuration value has been set.
     */
    boolean hasDefaultValue();
    
    /**
     * @return True if the configuration can be changed at runtime.
     */
    boolean isReconfigurable();

    /**
     * @return The inheritance model, or <code>null</code> for the default in any context.
     */
    @Nullable ConfigInheritance getInheritance();

    /**
     * @return The predicate constraining the key's value.
     */
    @Beta
    @Nonnull
    Predicate<? super T> getConstraint();

    /**
     * @param value The value to test
     * @return True if the given value is acceptable per the {@link #getConstraint constraints} on this key.
     */
    @Beta
    boolean isValueValid(T value);

    /** Interface for elements which want to be treated as a config key without actually being one
     * (e.g. config attribute sensors).
     */
    public interface HasConfigKey<T> {
        public ConfigKey<T> getConfigKey();
    }
}

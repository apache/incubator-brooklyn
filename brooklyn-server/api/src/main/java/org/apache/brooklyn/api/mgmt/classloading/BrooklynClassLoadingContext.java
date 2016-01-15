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
package org.apache.brooklyn.api.mgmt.classloading;

import java.net.URL;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.util.guava.Maybe;

/** 
 * Provides functionality for loading classes based on the current context
 * (e.g. the bundles of a registered type from which an entity is created)
 */
public interface BrooklynClassLoadingContext {

    public ManagementContext getManagementContext();
    public Class<?> loadClass(String className);
    public <T> Class<? extends T> loadClass(String className, @Nullable Class<T> supertype);

    public Maybe<Class<?>> tryLoadClass(String className);
    public <T> Maybe<Class<? extends T>> tryLoadClass(String className, @Nullable Class<T> supertype);

    /** As {@link ClassLoader#getResource(String)} */
    public URL getResource(String name);

    /**
     * As {@link ClassLoader#getResources(String)} but returning an {@link Iterable} rather than
     * an {@link java.util.Enumeration}.
     */
    public Iterable<URL> getResources(String name);

}

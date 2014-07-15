package brooklyn.management.classloading;

import javax.annotation.Nullable;

import brooklyn.management.ManagementContext;
import brooklyn.util.guava.Maybe;

import com.google.common.base.Objects;

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
public abstract class AbstractBrooklynClassLoadingContext implements BrooklynClassLoadingContext {

    protected final ManagementContext mgmt;

    public AbstractBrooklynClassLoadingContext(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

    @Override
    public ManagementContext getManagementContext() {
        return mgmt;
    }
    
    @Override
    public Class<?> loadClass(String className) {
        return tryLoadClass(className).get();
    }

    @Override
    // this is the only one left for subclasses
    public abstract Maybe<Class<?>> tryLoadClass(String className);

    @Override
    public <T> Class<? extends T> loadClass(String className, @Nullable Class<T> supertype) {
        return tryLoadClass(className, supertype).get();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public <T> Maybe<Class<? extends T>> tryLoadClass(String className, @Nullable Class<T> supertype) {
        Maybe<Class<?>> result = tryLoadClass(className);
        if (result.isAbsent()) return (Maybe)result;
        Class<?> clazz = result.get();
        if (supertype==null || supertype.isAssignableFrom(clazz)) return (Maybe)result;
        throw new ClassCastException(className+" is not an instance of "+supertype);
    }

    @Override
    public abstract String toString();

    @Override
    public int hashCode() {
        return Objects.hashCode(mgmt);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BrooklynClassLoadingContext)) return false;
        if (!Objects.equal(mgmt, ((BrooklynClassLoadingContext)obj).getManagementContext())) return false;
        return true;
    }
    
}

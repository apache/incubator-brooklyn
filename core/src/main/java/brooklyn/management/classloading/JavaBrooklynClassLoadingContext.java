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
package brooklyn.management.classloading;

import java.net.URL;

import com.google.common.base.Objects;

import brooklyn.management.ManagementContext;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;

public class JavaBrooklynClassLoadingContext extends AbstractBrooklynClassLoadingContext {

    private final ClassLoader loader;

    public JavaBrooklynClassLoadingContext(ManagementContext mgmt, ClassLoader loader) {
        super(mgmt);
        this.loader = loader;
    }
    
    public static JavaBrooklynClassLoadingContext newDefault(ManagementContext mgmt) {
        ClassLoader cl = null;
        if (mgmt!=null) cl = mgmt.getCatalog().getRootClassLoader();
        if (cl==null) cl = JavaBrooklynClassLoadingContext.class.getClassLoader();
        return new JavaBrooklynClassLoadingContext(mgmt, cl);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Maybe<Class<?>> tryLoadClass(String className) {
        try {
            return (Maybe) Maybe.of(loader.loadClass(className));
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            return Maybe.absent(e);
        }
    }

    @Override
    public String toString() {
        return "java:"+loader;
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), loader);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        if (!(obj instanceof JavaBrooklynClassLoadingContext)) return false;
        if (!Objects.equal(loader, ((JavaBrooklynClassLoadingContext)obj).loader)) return false;
        return true;
    }

    @Override
    public URL getResource(String name) {
        return loader.getResource(name);
    }
    
}

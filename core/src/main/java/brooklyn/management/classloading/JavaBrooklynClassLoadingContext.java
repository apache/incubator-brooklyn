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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

import org.apache.brooklyn.management.ManagementContext;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;

import com.google.common.base.Objects;

public class JavaBrooklynClassLoadingContext extends AbstractBrooklynClassLoadingContext {

    // on deserialization this loader is replaced with the catalog's root loader;
    // may cause problems for non-osgi catalog items, but that's a reasonable trade-off,
    // should this be serialized (e.g. in SpecialFlagsTransformer) in such a case!
    private final transient ClassLoader loader;

    /**
     * @deprecated since 0.7.0 only for legacy catalog items which provide a non-osgi loader; see {@link #newDefault(ManagementContext)}
     */ @Deprecated
    public static JavaBrooklynClassLoadingContext create(ClassLoader loader) {
        return new JavaBrooklynClassLoadingContext(null, checkNotNull(loader, "loader"));
    }
    
    /**
     * At least one of mgmt or loader must not be null.
     * @deprecated since 0.7.0 only for legacy catalog items which provide a non-osgi loader; see {@link #newDefault(ManagementContext)}
     */ @Deprecated
    public static JavaBrooklynClassLoadingContext create(ManagementContext mgmt, ClassLoader loader) {
        checkState(mgmt != null || loader != null, "mgmt and loader must not both be null");
        return new JavaBrooklynClassLoadingContext(mgmt, loader);
    }
    
    public static JavaBrooklynClassLoadingContext create(ManagementContext mgmt) {
        return new JavaBrooklynClassLoadingContext(checkNotNull(mgmt, "mgmt"), null);
    }

    @Deprecated /** @deprecated since 0.7.0 use {@link #create(ManagementContext)} */
    public static JavaBrooklynClassLoadingContext newDefault(ManagementContext mgmt) {
        return new JavaBrooklynClassLoadingContext(checkNotNull(mgmt, "mgmt"), null);
    }

    @Deprecated /** @deprecated since 0.7.0 will become private; use one of the static methods to instantiate */
    public JavaBrooklynClassLoadingContext(ManagementContext mgmt, ClassLoader loader) {
        super(mgmt);
        this.loader = loader;
    }
    
    private ClassLoader getClassLoader() {
        if (loader != null) return loader;
        if (mgmt!=null) return mgmt.getCatalogClassLoader();
        return JavaBrooklynClassLoadingContext.class.getClassLoader();
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Maybe<Class<?>> tryLoadClass(String className) {
        try {
            return (Maybe) Maybe.of(getClassLoader().loadClass(className));
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            return Maybe.absent("Invalid class: "+className, e);
        }
    }

    @Override
    public String toString() {
        return "java:"+getClassLoader();
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), getClassLoader());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        if (!(obj instanceof JavaBrooklynClassLoadingContext)) return false;
        if (!Objects.equal(getClassLoader(), ((JavaBrooklynClassLoadingContext)obj).getClassLoader())) return false;
        return true;
    }

    @Override
    public URL getResource(String name) {
        return getClassLoader().getResource(name);
    }

    @Override
    public Iterable<URL> getResources(String name) {
        Enumeration<URL> resources;
        try {
            resources = getClassLoader().getResources(name);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
        return Collections.list(resources);
    }
}

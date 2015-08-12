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

import org.apache.brooklyn.management.classloading.BrooklynClassLoadingContext;

public class ClassLoaderFromBrooklynClassLoadingContext extends ClassLoader {

    /** Constructs a {@link ClassLoader} which delegates to the given {@link BrooklynClassLoadingContext} */
    public static ClassLoader of(BrooklynClassLoadingContext clc) {
        return new ClassLoaderFromBrooklynClassLoadingContext(clc);
    }
    
    protected final BrooklynClassLoadingContext clc;

    protected ClassLoaderFromBrooklynClassLoadingContext(BrooklynClassLoadingContext clc) {
        this.clc = clc;
    }
    
    @Override
    public Class<?> findClass(String className) throws ClassNotFoundException {
        Class<?> result = clc.loadClass(className);
        if (result!=null) return result;
        
        // last resort. see comment in XStream CompositeClassLoader
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            result = contextClassLoader.loadClass(className);
            if (result!=null) return result;
        }
        return null;
    }
    
    @Override
    protected URL findResource(String name) {
        URL result = clc.getResource(name);
        if (result!=null) return result;
        
        // last resort. see comment in XStream CompositeClassLoader
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            result = contextClassLoader.getResource(name);
            if (result!=null) return result;
        }
        return null;
    }
    
}

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
package org.apache.brooklyn.util.javalang;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.common.collect.Sets;

/** looks for classes and resources in the classloaders added here
 * <p>
 * similar to XStream's CompositeClassLoader, but also supporting resources,
 * exposing more info, a few conveniences, and a nice toString */
public class AggregateClassLoader extends ClassLoader {

    // thread safe -- and all access in this class is also synchronized, 
    // so that reset is guaranteed not to interfere with an add(0, cl) 
    private final CopyOnWriteArrayList<ClassLoader> classLoaders = new CopyOnWriteArrayList<ClassLoader>();

    private AggregateClassLoader() {
        //Don't pass load requests to the app classloader,
        //always relay to the classLoaders list.
        super(null);
    }
    
    /** creates default instance, with classloaders of Object and AggregateClassLoader */
    public static AggregateClassLoader newInstanceWithDefaultLoaders() {
        AggregateClassLoader cl = new AggregateClassLoader();
        cl.addFirst(AggregateClassLoader.class.getClassLoader()); // whichever classloader loaded this jar.
        cl.addFirst(Object.class.getClassLoader()); // bootstrap loader.
        return cl;
    }
    /** creates default instance, with no classloaders (assuming this instance will itself be nested,
     * or defaults will be added by caller) */
    public static AggregateClassLoader newInstanceWithNoLoaders() {
        return new AggregateClassLoader();
    }

    /** Add a loader to the first position in the search path. */
    public void addFirst(ClassLoader classLoader) {
        if (classLoader != null) {
            synchronized (classLoaders) {
                classLoaders.add(0, classLoader);
            }
        }
    }
    /** Add a loader to the last position in the search path. */
    public void addLast(ClassLoader classLoader) {
        if (classLoader != null) {
            synchronized (classLoaders) {
                classLoaders.add(classLoader);
            }
        }
    }
    /** Add a loader to the specific position in the search path. 
     * (It is callers responsibility to ensure that position is valid.) */
    public void add(int index, ClassLoader classLoader) {
        if (classLoader != null) {
            synchronized (classLoaders) {
                classLoaders.add(index, classLoader);
            }
        }
    }
    
    /** Resets the classloader shown here to be the given set */
    public void reset(Collection<? extends ClassLoader> newClassLoaders) {
        synchronized (classLoaders) {
            // synchronize:
            // * to prevent concurrent invocations
            // * so add(0, cl) doesn't interfere
            // * and for good measure we add before removing so that iterator always contains everything
            //   although since iterator access is synchronized that shouldn't be necessary
            int count = classLoaders.size();
            classLoaders.addAll(newClassLoaders);
            for (int i=0; i<count; i++) {
                classLoaders.remove(0);
            }
        }
    }

    /** True if nothing is in the list here */
    public boolean isEmpty() {
        return classLoaders.isEmpty();
    }
    
    /** Returns the _live_ (and modifiable) list of classloaders; dangerous and discouraged. 
     * @deprecated since 0.7.0 */
    @Deprecated
    public List<ClassLoader> getList() {
        return classLoaders;
    }

    public Iterator<ClassLoader> iterator() {
        synchronized (classLoaders) {
            // CopyOnWriteList iterator is immutable view of snapshot
            return classLoaders.iterator();
        }
    }
    
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        for (ClassLoader classLoader: classLoaders) {
            try {
                return classLoader.loadClass(name);
            } catch (ClassNotFoundException notFound) {
                /* ignore (nice if there were a better way than throwing... */
            }
        }
        // last resort. see comment in XStream CompositeClassLoader
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null)
            return contextClassLoader.loadClass(name);
        throw new ClassNotFoundException(name);
    }

    @Override
    public String toString() {
        return "AggregateClassLoader"+classLoaders;
    }

    @Override
    public URL getResource(String name) {
        URL result = null;
        Iterator<ClassLoader> cli = iterator();
        while (cli.hasNext()) {
            ClassLoader classLoader=cli.next();
            result = classLoader.getResource(name);
            if (result!=null) return result;
        }
        // last resort. see comment in XStream CompositeClassLoader
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) 
            return contextClassLoader.getResource(name);
        return null;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Set<URL> resources = Sets.newLinkedHashSet();
        Iterator<ClassLoader> cli = iterator();
        while (cli.hasNext()) {
            ClassLoader classLoader=cli.next();
            resources.addAll(Collections.list(classLoader.getResources(name)));
        }
        return Collections.enumeration(resources);
    }

    // TODO lesser used items, such as getPackage, findLibrary

}

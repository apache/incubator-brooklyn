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
package org.apache.brooklyn.core.mgmt.classloading;

import java.net.URL;
import java.util.List;
import java.util.Set;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public final class BrooklynClassLoadingContextSequential extends AbstractBrooklynClassLoadingContext {

    private static final Logger log = LoggerFactory.getLogger(BrooklynClassLoadingContextSequential.class);
    
    private final List<BrooklynClassLoadingContext> primaries = MutableList.<BrooklynClassLoadingContext>of();
    // secondaries used to put java classloader last
    private final Set<BrooklynClassLoadingContext> secondaries = MutableSet.<BrooklynClassLoadingContext>of();

    public BrooklynClassLoadingContextSequential(ManagementContext mgmt, BrooklynClassLoadingContext ...targets) {
        super(mgmt);
        for (BrooklynClassLoadingContext target: targets)
            add(target);
    }
    
    public void add(BrooklynClassLoadingContext target) {
        if (target instanceof BrooklynClassLoadingContextSequential) {
            for (BrooklynClassLoadingContext targetN: ((BrooklynClassLoadingContextSequential)target).primaries )
                add(targetN);
            for (BrooklynClassLoadingContext targetN: ((BrooklynClassLoadingContextSequential)target).secondaries )
                addSecondary(targetN);
        } else {
            this.primaries.add( target );
        }
    }

    public void addSecondary(BrooklynClassLoadingContext target) {
        if (!(target instanceof JavaBrooklynClassLoadingContext)) {
            // support for legacy catalog classloader only
            log.warn("Only Java classloaders should be secondary");
        }
        this.secondaries.add( target );
    }
    
    public Maybe<Class<?>> tryLoadClass(String className) {
        List<Throwable> errors = MutableList.of();
        for (BrooklynClassLoadingContext target: primaries) {
            Maybe<Class<?>> clazz = target.tryLoadClass(className);
            if (clazz.isPresent())
                return clazz;
            errors.add( ((Maybe.Absent<?>)clazz).getException() );
        }
        boolean noPrimaryErrors = errors.isEmpty();
        for (BrooklynClassLoadingContext target: secondaries) {
            Maybe<Class<?>> clazz = target.tryLoadClass(className);
            if (clazz.isPresent())
                return clazz;
            if (noPrimaryErrors)
                errors.add( ((Maybe.Absent<?>)clazz).getException() );
        }

        return Maybe.absent(Exceptions.create("Unable to load "+className+" from "+primaries, errors));
    }

    @Override
    public URL getResource(String resourceInThatDir) {
        for (BrooklynClassLoadingContext target: primaries) {
            URL result = target.getResource(resourceInThatDir);
            if (result!=null) return result;
        }
        for (BrooklynClassLoadingContext target: secondaries) {
            URL result = target.getResource(resourceInThatDir);
            if (result!=null) return result;
        }
        return null;
    }

    @Override
    public Iterable<URL> getResources(String name) {
        List<Iterable<URL>> resources = Lists.newArrayList();
        for (BrooklynClassLoadingContext target : primaries) {
            resources.add(target.getResources(name));
        }
        for (BrooklynClassLoadingContext target : secondaries) {
            resources.add(target.getResources(name));
        }
        return Iterables.concat(resources);
    }

    @Override
    public String toString() {
        return "classload:"+primaries+";"+secondaries;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), primaries, secondaries);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        if (!(obj instanceof BrooklynClassLoadingContextSequential)) return false;
        if (!Objects.equal(primaries, ((BrooklynClassLoadingContextSequential)obj).primaries)) return false;
        if (!Objects.equal(secondaries, ((BrooklynClassLoadingContextSequential)obj).secondaries)) return false;
        return true;
    }
    
}

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
package org.apache.brooklyn.core.typereg;

import groovy.xml.Entity;

import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.BrooklynObjectType;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry.RegisteredTypeKind;
import org.apache.brooklyn.api.typereg.RegisteredTypeLoadingContext;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

public class RegisteredTypeLoadingContexts {

    private static final Logger log = LoggerFactory.getLogger(RegisteredTypeLoadingContexts.BasicRegisteredTypeLoadingContext.class);
    
    /** Immutable (from caller's perspective) record of a constraint */
    public final static class BasicRegisteredTypeLoadingContext implements RegisteredTypeLoadingContext {
        @Nullable private RegisteredTypeKind kind;
        @Nullable private Class<?> expectedSuperType;
        @Nonnull private Set<String> encounteredTypes = ImmutableSet.of();
        @Nullable BrooklynClassLoadingContext loader;
        
        private BasicRegisteredTypeLoadingContext() {}
        
        public BasicRegisteredTypeLoadingContext(@Nullable RegisteredTypeLoadingContext source) {
            if (source==null) return;
            
            this.kind = source.getExpectedKind();
            this.expectedSuperType = source.getExpectedJavaSuperType();
            this.encounteredTypes = source.getAlreadyEncounteredTypes();
            this.loader = (BrooklynClassLoadingContext) source.getLoader();
        }

        @Override
        public RegisteredTypeKind getExpectedKind() {
            return kind;
        }
        
        @Override
        public Class<?> getExpectedJavaSuperType() {
            if (expectedSuperType==null) return Object.class;
            return expectedSuperType;
        }

        @Override
        public Set<String> getAlreadyEncounteredTypes() {
            if (encounteredTypes==null) return ImmutableSet.of();
            return ImmutableSet.<String>copyOf(encounteredTypes);
        }
        
        @Override
        public BrooklynClassLoadingContext getLoader() {
            return loader;
        }
        
        @Override
        public String toString() {
            return JavaClassNames.cleanSimpleClassName(this)+"["+kind+","+expectedSuperType+","+encounteredTypes+"]";
        }
    }

    /** returns a constraint which allows anything */
    public static RegisteredTypeLoadingContext any() {
        return new BasicRegisteredTypeLoadingContext();
    }

    public static RegisteredTypeLoadingContext alreadyEncountered(Set<String> encounteredTypeSymbolicNames) {
        BasicRegisteredTypeLoadingContext result = new BasicRegisteredTypeLoadingContext();
        result.encounteredTypes = encounteredTypeSymbolicNames == null ? ImmutableSet.<String>of() : ImmutableSet.copyOf(encounteredTypeSymbolicNames);
        return result;
    }
    public static RegisteredTypeLoadingContext alreadyEncountered(Set<String> encounteredTypeSymbolicNames, String anotherEncounteredType) {
        BasicRegisteredTypeLoadingContext result = new BasicRegisteredTypeLoadingContext();
        MutableSet<String> encounteredTypes = MutableSet.copyOf(encounteredTypeSymbolicNames);
        encounteredTypes.addIfNotNull(anotherEncounteredType);
        result.encounteredTypes = encounteredTypes.asUnmodifiable();
        return result;
    }

    public static RegisteredTypeLoadingContext loaderAlreadyEncountered(BrooklynClassLoadingContext loader, Set<String> encounteredTypeSymbolicNames) {
        return loaderAlreadyEncountered(loader, encounteredTypeSymbolicNames, null);
    }
    public static RegisteredTypeLoadingContext loaderAlreadyEncountered(BrooklynClassLoadingContext loader, Set<String> encounteredTypeSymbolicNames, String anotherEncounteredType) {
        return withLoader(alreadyEncountered(encounteredTypeSymbolicNames, anotherEncounteredType), loader);
    }

    private static RegisteredTypeLoadingContext of(RegisteredTypeKind kind, Class<?> javaSuperType) {
        BasicRegisteredTypeLoadingContext result = new BasicRegisteredTypeLoadingContext();
        result.kind = kind;
        result.expectedSuperType = javaSuperType;
        return result;
    }

    public static RegisteredTypeLoadingContext bean(Class<?> javaSuperType) {
        return of(RegisteredTypeKind.BEAN, javaSuperType);
    }

    public static RegisteredTypeLoadingContext spec(Class<? extends BrooklynObject> javaSuperType) {
        return of(RegisteredTypeKind.SPEC, javaSuperType);
    }
    
    public static <T> RegisteredTypeLoadingContext withBeanSuperType(@Nullable RegisteredTypeLoadingContext source, @Nullable Class<T> beanSuperType) {
        Class<T> superType = beanSuperType;
        BasicRegisteredTypeLoadingContext constraint = new BasicRegisteredTypeLoadingContext(source);
        if (source==null) source = constraint;
        if (superType==null) return source;
        if (source.getExpectedJavaSuperType()==null || source.getExpectedJavaSuperType().isAssignableFrom( superType )) {
            // the constraint was weaker than present; return the new constraint
            return constraint;
        }
        if (superType.isAssignableFrom( source.getExpectedJavaSuperType() )) {
            // the constraint was already for something more specific; ignore what we've inferred here
            return source;
        }
        log.warn("Ambiguous bean supertypes ("+beanSuperType+" for target "+source.getExpectedJavaSuperType()+"); "
            + "it is recommended that any registered type constraint for a spec be compatible with the spec type");
        return source;
    }

    /** Takes a Spec java type and adds an expected java type to the {@link RegisteredTypeLoadingContext} */
    public static <T extends AbstractBrooklynObjectSpec<?,?>> RegisteredTypeLoadingContext withSpecSuperType(@Nullable RegisteredTypeLoadingContext source, @Nullable Class<T> specSuperType) {
        Class<?> superType = lookupTargetTypeForSpec(specSuperType);
        BasicRegisteredTypeLoadingContext constraint = new BasicRegisteredTypeLoadingContext(source);
        if (source==null) source = constraint;
        if (superType==null) return source;
        if (source.getExpectedJavaSuperType()==null || source.getExpectedJavaSuperType().isAssignableFrom( superType )) {
            // the constraint was weaker than present; return the new constraint
            return constraint;
        }
        if (superType.isAssignableFrom( source.getExpectedJavaSuperType() )) {
            // the constraint was already for something more specific; ignore what we've inferred here
            return source;
        }
        // trickier situation; the constraint had a type not compatible with the spec type; log a warning and leave alone
        // (e.g. caller specified some java super type which is not a super or sub of the spec target type;
        // this may be because the caller specified a Spec as the type supertype, which is wrong;
        // or they may have specified an interface along a different hierarchy, which we discouraged
        // as it will make filtering/indexing more complex)
        log.warn("Ambiguous spec supertypes ("+specSuperType+" for target "+source.getExpectedJavaSuperType()+"); "
            + "it is recommended that any registered type constraint for a spec be compatible with the spec type");
        return source;
    }
        
    /** given a spec, returns the class of the item it targets, for instance returns {@link Entity} given {@link EntitySpec};
     * see also {@link #lookupSpecTypeForTarget(Class)} */
    static <T extends AbstractBrooklynObjectSpec<?,?>> Class<? extends BrooklynObject> lookupTargetTypeForSpec(Class<T> specSuperType) {
        if (specSuperType==null) return BrooklynObject.class;
        BrooklynObjectType best = null;

        for (BrooklynObjectType t: BrooklynObjectType.values()) {
            if (t.getSpecType()==null) continue;
            if (!t.getSpecType().isAssignableFrom(specSuperType)) continue;
            // on equality, exit immediately
            if (t.getSpecType().equals(specSuperType)) return t.getInterfaceType();
            // else pick which is best
            if (best==null) { best = t; continue; }
            // if t is more specific, it is better (handles case when e.g. a Policy is a subclass of Entity)
            if (best.getSpecType().isAssignableFrom(t.getSpecType())) { best = t; continue; }
        }
        if (best==null) {
            log.warn("Unexpected spec supertype ("+specSuperType+"); treating as any "+BrooklynObject.class, new Throwable("Trace for unexpected spec supertype"));
            return BrooklynObject.class;
        }
        // the spec is more specific, but we're not familiar with it here; return the best
        return best.getInterfaceType();
    }

    /** given a {@link BrooklynObject}, returns the spec class which would generate it, for instance returns {@link EntitySpec} given {@link Entity},
     * or null if not known */
    static <BO extends BrooklynObject> Class<? extends AbstractBrooklynObjectSpec<?,?>> lookupSpecTypeForTarget(Class<BO> targetSuperType) {
        if (targetSuperType==null) return null;
        BrooklynObjectType best = null;

        for (BrooklynObjectType t: BrooklynObjectType.values()) {
            if (t.getSpecType()==null) continue;
            if (!t.getInterfaceType().isAssignableFrom(targetSuperType)) continue;
            // on equality, exit immediately
            if (t.getInterfaceType().equals(targetSuperType)) return t.getSpecType();
            // else pick which is best
            if (best==null) { best = t; continue; }
            // if t is more specific, it is better (handles case when e.g. a Policy is a subclass of Entity)
            if (best.getSpecType().isAssignableFrom(t.getSpecType())) { best = t; continue; }
        }
        if (best==null) {
            log.warn("Unexpected target supertype ("+targetSuperType+"); unable to infer spec type");
            return null;
        }
        // the spec is more specific, but we're not familiar with it here; return the best
        return best.getSpecType();
    }

    public static RegisteredTypeLoadingContext loader(BrooklynClassLoadingContext loader) {
        BasicRegisteredTypeLoadingContext result = new BasicRegisteredTypeLoadingContext();
        result.loader = loader;
        return result;
    }
    
    public static RegisteredTypeLoadingContext withLoader(RegisteredTypeLoadingContext constraint, BrooklynClassLoadingContext loader) {
        BasicRegisteredTypeLoadingContext result = new BasicRegisteredTypeLoadingContext(constraint);
        result.loader = loader;
        return result;
    }

}

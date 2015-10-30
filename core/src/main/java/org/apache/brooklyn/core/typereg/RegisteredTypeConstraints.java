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

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.BrooklynObjectType;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry.RegisteredTypeKind;
import org.apache.brooklyn.api.typereg.RegisteredTypeConstraint;
import org.apache.brooklyn.util.collections.MutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

public class RegisteredTypeConstraints {

    private static final Logger log = LoggerFactory.getLogger(RegisteredTypeConstraints.BasicRegisteredTypeConstraint.class);
    
    public final static class BasicRegisteredTypeConstraint implements RegisteredTypeConstraint {
        private RegisteredTypeKind kind;
        private Class<?> javaSuperType;
        private Set<String> encounteredTypes;
        
        private BasicRegisteredTypeConstraint() {}
        
        public BasicRegisteredTypeConstraint(RegisteredTypeConstraint source) {
            if (source==null) return;
            
            this.kind = source.getKind();
            this.javaSuperType = source.getJavaSuperType();
            this.encounteredTypes = source.getEncounteredTypes();
        }

        @Override
        public RegisteredTypeKind getKind() {
            return kind;
        }
        
        @Override
        public Class<?> getJavaSuperType() {
            if (javaSuperType==null) return Object.class;
            return javaSuperType;
        }

        @Override
        public Set<String> getEncounteredTypes() {
            if (encounteredTypes==null) return ImmutableSet.of();
            return ImmutableSet.<String>copyOf(encounteredTypes);
        }
        
        @Override
        public String toString() {
            return super.toString()+"["+kind+","+javaSuperType+","+encounteredTypes+"]";
        }
    }

    /** returns a constraint which allows anything */
    public static RegisteredTypeConstraint any() {
        return new BasicRegisteredTypeConstraint();
    }

    public static RegisteredTypeConstraint alreadyVisited(Set<String> encounteredTypeSymbolicNames) {
        BasicRegisteredTypeConstraint result = new BasicRegisteredTypeConstraint();
        result.encounteredTypes = encounteredTypeSymbolicNames;
        return result;
    }
    public static RegisteredTypeConstraint alreadyVisited(Set<String> encounteredTypeSymbolicNames, String anotherEncounteredType) {
        BasicRegisteredTypeConstraint result = new BasicRegisteredTypeConstraint();
        result.encounteredTypes = MutableSet.copyOf(encounteredTypeSymbolicNames);
        if (anotherEncounteredType!=null) result.encounteredTypes.add(anotherEncounteredType);
        return result;
    }
    
    private static RegisteredTypeConstraint of(RegisteredTypeKind kind, Class<? extends BrooklynObject> javaSuperType) {
        BasicRegisteredTypeConstraint result = new BasicRegisteredTypeConstraint();
        result.kind = kind;
        result.javaSuperType = javaSuperType;
        return result;
    }

    public static RegisteredTypeConstraint spec(Class<? extends BrooklynObject> javaSuperType) {
        return of(RegisteredTypeKind.SPEC, javaSuperType);
    }

    public static <T extends AbstractBrooklynObjectSpec<?,?>> RegisteredTypeConstraint extendedWithSpecSuperType(RegisteredTypeConstraint source, Class<T> specSuperType) {
        Class<?> superType = lookupTargetTypeForSpec(specSuperType);
        BasicRegisteredTypeConstraint constraint = new BasicRegisteredTypeConstraint(source);
        if (source==null) source = constraint;
        if (source.getJavaSuperType()==null || source.getJavaSuperType().isAssignableFrom( superType )) {
            // the constraint was weaker than present; return the new constraint
            return constraint;
        }
        if (superType.isAssignableFrom( source.getJavaSuperType() )) {
            // the constraint was already for something more specific; ignore what we've inferred here
            return source;
        }
        // trickier situation; the constraint had a type not compatible with the spec type; log a warning and leave alone
        // (e.g. caller specified some java super type which is not a super or sub of the spec target type;
        // this may be because the caller specified a Spec as the type supertype, which is wrong;
        // or they may have specified an interface along a different hierarchy, which we discouraged
        // as it will make filtering/indexing more complex)
        log.warn("Ambiguous spec supertypes ("+specSuperType+" for target "+source.getJavaSuperType()+"); "
            + "it is recommended that any registered type constraint for a spec be compatible with the spec type");
        return source;
    }
    
    /** given a spec, returns the class of the item it targets, for instance {@link EntitySpec} for {@link Entity} */
    private static <T extends AbstractBrooklynObjectSpec<?,?>> Class<? extends BrooklynObject> lookupTargetTypeForSpec(Class<T> specSuperType) {
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

}

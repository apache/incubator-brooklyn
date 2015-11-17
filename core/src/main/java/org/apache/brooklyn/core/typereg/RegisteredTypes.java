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

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry.RegisteredTypeKind;
import org.apache.brooklyn.api.typereg.OsgiBundleWithUrl;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.api.typereg.RegisteredType.TypeImplementationPlan;
import org.apache.brooklyn.api.typereg.RegisteredTypeLoadingContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.typereg.JavaClassNameTypePlanTransformer.JavaClassNameTypeImplementationPlan;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.yaml.Yamls;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;

/**
 * Utility and preferred creation mechanisms for working with {@link RegisteredType} instances.
 * <p>
 * Use {@link #bean(String, String, TypeImplementationPlan, Class)} and {@link #spec(String, String, TypeImplementationPlan, Class)}
 * to create {@link RegisteredType} instances.
 * <p>
 * See {@link #isAssignableFrom(RegisteredType, Class)} or {@link #isAssignableFrom(RegisteredType, RegisteredType)} to 
 * inspect the type hierarchy.
 */
public class RegisteredTypes {

    @SuppressWarnings("serial")
    static ConfigKey<Class<?>> ACTUAL_JAVA_TYPE = ConfigKeys.newConfigKey(new TypeToken<Class<?>>() {}, "java.type.actual",
        "The actual Java type which will be instantiated (bean) or pointed at (spec)");

    /** @deprecated since it was introduced in 0.9.0; for backwards compatibility only, may be removed at any point */
    @Deprecated
    static final Function<CatalogItem<?,?>,RegisteredType> CI_TO_RT = new Function<CatalogItem<?,?>, RegisteredType>() {
        @Override
        public RegisteredType apply(CatalogItem<?, ?> item) {
            return of(item);
        }
    };
    
    /** @deprecated since it was introduced in 0.9.0; for backwards compatibility only, may be removed at any point */
    @Deprecated
    public static RegisteredType of(CatalogItem<?, ?> item) {
        if (item==null) return null;
        TypeImplementationPlan impl = null;
        if (item.getPlanYaml()!=null) {
            impl = new BasicTypeImplementationPlan(null, item.getPlanYaml());
        } else if (item.getJavaType()!=null) {
            impl = new JavaClassNameTypeImplementationPlan(item.getJavaType());
        } else {
            throw new IllegalStateException("Unsupported catalog item "+item+" when trying to create RegisteredType");
        }
        
        BasicRegisteredType type = (BasicRegisteredType) spec(item.getSymbolicName(), item.getVersion(), impl, item.getCatalogItemJavaType());
        type.bundles = item.getLibraries()==null ? ImmutableList.<OsgiBundleWithUrl>of() : ImmutableList.<OsgiBundleWithUrl>copyOf(item.getLibraries());
        type.displayName = item.getDisplayName();
        type.description = item.getDescription();
        type.iconUrl = item.getIconUrl();
        type.disabled = item.isDisabled();
        type.deprecated = item.isDeprecated();

        // TODO
        // probably not: javaType, specType, registeredTypeName ...
        // maybe: tags ?
        return type;
    }

    /** Preferred mechanism for defining a bean {@link RegisteredType}. */
    public static RegisteredType bean(String symbolicName, String version, TypeImplementationPlan plan, @Nullable Class<?> superType) {
        return addSuperType(new BasicRegisteredType(RegisteredTypeKind.BEAN, symbolicName, version, plan), superType);
    }
    
    /** Preferred mechanism for defining a spec {@link RegisteredType}. */
    // TODO we currently allow symbolicName and version to be null for the purposes of creation, internal only in BasicBrooklynTypeRegistry.createSpec
    // (ideally the API in TypePlanTransformer can be changed so even that is not needed)
    public static RegisteredType spec(String symbolicName, String version, TypeImplementationPlan plan, @Nullable Class<?> superType) {
        return addSuperType(new BasicRegisteredType(RegisteredTypeKind.SPEC, symbolicName, version, plan), superType);
    }

    /** returns the {@link Class} object corresponding to the given java type name,
     * using the cache on the type and the loader defined on the type
     * @param mgmt */
    @Beta
    // TODO should this be on the AbstractTypePlanTransformer ?
    public static Class<?> loadActualJavaType(String javaTypeName, ManagementContext mgmt, RegisteredType type, RegisteredTypeLoadingContext context) {
        Class<?> result = ((BasicRegisteredType)type).getCache().get(ACTUAL_JAVA_TYPE);
        if (result!=null) return result;
        
        result = CatalogUtils.newClassLoadingContext(mgmt, type, context==null ? null : context.getLoader()).loadClass( javaTypeName );
        
        ((BasicRegisteredType)type).getCache().put(ACTUAL_JAVA_TYPE, result);
        return result;
    }

    @Beta
    public static RegisteredType addSuperType(RegisteredType type, @Nullable Class<?> superType) {
        if (superType!=null) {
            ((BasicRegisteredType)type).superTypes.add(superType);
        }
        return type;
    }

    @Beta
    public static RegisteredType addSuperType(RegisteredType type, @Nullable RegisteredType superType) {
        if (superType!=null) {
            if (isAssignableFrom(superType, type)) {
                throw new IllegalStateException(superType+" declares "+type+" as a supertype; cannot set "+superType+" as a supertype of "+type);
            }
            ((BasicRegisteredType)type).superTypes.add(superType);
        }
        return type;
    }

    /** returns the implementation data for a spec if it is a string (e.g. plan yaml or java class name); else throws */
    @Beta
    public static String getImplementationDataStringForSpec(RegisteredType item) {
        if (item==null || item.getPlan()==null) return null;
        Object data = item.getPlan().getPlanData();
        if (!(data instanceof String)) throw new IllegalStateException("Expected plan data for "+item+" to be a string");
        return (String)data;
    }

    /** returns an implementation of the spec class corresponding to the given target type;
     * for use in {@link BrooklynTypePlanTransformer#create(RegisteredType, RegisteredTypeLoadingContext)} 
     * implementations when dealing with a spec; returns null if none found
     * @param mgmt */
    @Beta
    public static AbstractBrooklynObjectSpec<?,?> newSpecInstance(ManagementContext mgmt, Class<? extends BrooklynObject> targetType) throws Exception {
        Class<? extends AbstractBrooklynObjectSpec<?, ?>> specType = RegisteredTypeLoadingContexts.lookupSpecTypeForTarget(targetType);
        if (specType==null) return null;
        Method createMethod = specType.getMethod("create", Class.class);
        return (AbstractBrooklynObjectSpec<?, ?>) createMethod.invoke(null, targetType);
    }

    /** Returns a wrapped map, if the object is YAML which parses as a map; 
     * otherwise returns absent capable of throwing an error with more details */
    @SuppressWarnings("unchecked")
    public static Maybe<Map<?,?>> getAsYamlMap(Object planData) {
        if (!(planData instanceof String)) return Maybe.absent("not a string");
        Iterable<Object> result;
        try {
            result = Yamls.parseAll((String)planData);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            return Maybe.absent(e);
        }
        Iterator<Object> ri = result.iterator();
        if (!ri.hasNext()) return Maybe.absent("YAML has no elements in it");
        Object r1 = ri.next();
        if (ri.hasNext()) return Maybe.absent("YAML has multiple elements in it");
        if (r1 instanceof Map) return (Maybe<Map<?,?>>)(Maybe<?>) Maybe.of(r1);
        return Maybe.absent("YAML does not contain a map");
    }

    /** 
     * Queries recursively the supertypes of {@link RegisteredType} to see whether it 
     * inherits from the given {@link RegisteredType} */
    public static boolean isAssignableFrom(RegisteredType type, RegisteredType superType) {
        if (type.equals(superType)) return true;
        for (Object st: type.getSuperTypes()) {
            if (st instanceof RegisteredType) {
                if (isAssignableFrom((RegisteredType)st, superType)) return true;
            }
        }
        return false;
    }

    /** 
     * Queries recursively the supertypes of {@link RegisteredType} to see whether it 
     * inherits from the given {@link Class} */
    public static boolean isAssignableFrom(RegisteredType type, Class<?> superType) {
        return isAnyTypeAssignableFrom(type.getSuperTypes(), superType);
    }
    
    /** 
     * Queries recursively the given types (either {@link Class} or {@link RegisteredType}) 
     * to see whether any inherit from the given {@link Class} */
    public static boolean isAnyTypeAssignableFrom(Set<Object> candidateTypes, Class<?> superType) {
        return isAnyTypeOrSuperSatisfying(candidateTypes, Predicates.assignableFrom(superType));
    }

    /** 
     * Queries recursively the given types (either {@link Class} or {@link RegisteredType}) 
     * to see whether any java superclasses satisfy the given {@link Predicate} */
    public static boolean isAnyTypeOrSuperSatisfying(Set<Object> candidateTypes, Predicate<Class<?>> filter) {
        for (Object st: candidateTypes) {
            if (st instanceof Class) {
                if (filter.apply((Class<?>)st)) return true;
            }
        }
        for (Object st: candidateTypes) {
            if (st instanceof RegisteredType) {
                if (isAnyTypeOrSuperSatisfying(((RegisteredType)st).getSuperTypes(), filter)) return true;
            }
        }
        return false;
    }

}

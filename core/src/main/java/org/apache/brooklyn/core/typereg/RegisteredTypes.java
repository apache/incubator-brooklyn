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
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry.RegisteredTypeKind;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.api.typereg.RegisteredType.TypeImplementationPlan;
import org.apache.brooklyn.api.typereg.RegisteredTypeLoadingContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.objs.BrooklynObjectInternal;
import org.apache.brooklyn.core.typereg.JavaClassNameTypePlanTransformer.JavaClassNameTypeImplementationPlan;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.NaturalOrderComparator;
import org.apache.brooklyn.util.text.VersionComparator;
import org.apache.brooklyn.util.yaml.Yamls;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ComparisonChain;
import com.google.common.reflect.TypeToken;

/**
 * Utility and preferred creation mechanisms for working with {@link RegisteredType} instances.
 * <p>
 * Use {@link #bean(String, String, TypeImplementationPlan, Class)} and {@link #spec(String, String, TypeImplementationPlan, Class)}
 * to create {@link RegisteredType} instances.
 * <p>
 * See {@link #isSubtypeOf(RegisteredType, Class)} or {@link #isSubtypeOf(RegisteredType, RegisteredType)} to 
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
        type.displayName = item.getDisplayName();
        type.description = item.getDescription();
        type.iconUrl = item.getIconUrl();
        
        if (item.getLibraries()!=null) type.bundles.addAll(item.getLibraries());
        type.disabled = item.isDisabled();
        type.deprecated = item.isDeprecated();
        if (item.getLibraries()!=null) type.bundles.addAll(item.getLibraries());
        // aliases aren't on item
        if (item.tags()!=null) type.tags.addAll(item.tags().getTags());

        // these things from item we ignore: javaType, specType, registeredTypeName ...
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
            if (isSubtypeOf(superType, type)) {
                throw new IllegalStateException(superType+" declares "+type+" as a supertype; cannot set "+superType+" as a supertype of "+type);
            }
            ((BasicRegisteredType)type).superTypes.add(superType);
        }
        return type;
    }
    @Beta
    public static RegisteredType addSuperTypes(RegisteredType type, Iterable<Object> superTypesAsClassOrRegisteredType) {
        if (superTypesAsClassOrRegisteredType!=null) {
            for (Object superType: superTypesAsClassOrRegisteredType) {
                if (superType==null) {
                    // nothing
                } else if (superType instanceof Class) {
                    addSuperType(type, (Class<?>)superType);
                } else if (superType instanceof RegisteredType) {
                    addSuperType(type, (RegisteredType)superType);
                } else {
                    throw new IllegalStateException(superType+" supplied as a supertype of "+type+" but it is not a supported supertype");
                }
            }
        }
        return type;
    }

    @Beta
    public static RegisteredType addAlias(RegisteredType type, String alias) {
        if (alias!=null) {
            ((BasicRegisteredType)type).aliases.add( alias );
        }
        return type;
    }
    @Beta
    public static RegisteredType addAliases(RegisteredType type, Iterable<String> aliases) {
        if (aliases!=null) {
            for (String alias: aliases) addAlias(type, alias);
        }
        return type;
    }

    @Beta
    public static RegisteredType addTag(RegisteredType type, Object tag) {
        if (tag!=null) {
            ((BasicRegisteredType)type).tags.add( tag );
        }
        return type;
    }
    @Beta
    public static RegisteredType addTags(RegisteredType type, Iterable<?> tags) {
        if (tags!=null) {
            for (Object tag: tags) addTag(type, tag);
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
    public static boolean isSubtypeOf(RegisteredType type, RegisteredType superType) {
        if (type.equals(superType)) return true;
        for (Object st: type.getSuperTypes()) {
            if (st instanceof RegisteredType) {
                if (isSubtypeOf((RegisteredType)st, superType)) return true;
            }
        }
        return false;
    }

    /** 
     * Queries recursively the supertypes of {@link RegisteredType} to see whether it 
     * inherits from the given {@link Class} */
    public static boolean isSubtypeOf(RegisteredType type, Class<?> superType) {
        return isAnyTypeSubtypeOf(type.getSuperTypes(), superType);
    }
    
    /** 
     * Queries recursively the given types (either {@link Class} or {@link RegisteredType}) 
     * to see whether any inherit from the given {@link Class} */
    public static boolean isAnyTypeSubtypeOf(Set<Object> candidateTypes, Class<?> superType) {
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

    public static Maybe<RegisteredType> validate(RegisteredType item, final RegisteredTypeLoadingContext constraint) {
        if (item==null || constraint==null) return Maybe.of(item);
        if (constraint.getExpectedKind()!=null && !constraint.getExpectedKind().equals(item.getKind()))
            return Maybe.absent(item+" is not the expected kind "+constraint.getExpectedKind());
        if (constraint.getExpectedJavaSuperType()!=null) {
            if (!isSubtypeOf(item, constraint.getExpectedJavaSuperType())) {
                return Maybe.absent(item+" is not for the expected type "+constraint.getExpectedJavaSuperType());
            }
        }
        return Maybe.of(item);
    }

    /** 
     * Checks whether the given object appears to be an instance of the given registered type */
    private static boolean isSubtypeOf(Class<?> candidate, RegisteredType type) {
        for (Object st: type.getSuperTypes()) {
            if (st instanceof RegisteredType) {
                if (!isSubtypeOf(candidate, (RegisteredType)st)) return false;
            }
            if (st instanceof Class) {
                if (!((Class<?>)st).isAssignableFrom(candidate)) return false;
            }
        }
        return true;
    }

    public static RegisteredType getBestVersion(Iterable<RegisteredType> types) {
        if (types==null || !types.iterator().hasNext()) return null;
        return Collections.max(MutableList.copyOf(types), RegisteredTypeComparator.INSTANCE);
    }
    
    public static class RegisteredTypeComparator implements Comparator<RegisteredType> {
        public static Comparator<RegisteredType> INSTANCE = new RegisteredTypeComparator();
        private RegisteredTypeComparator() {}
        @Override
        public int compare(RegisteredType o1, RegisteredType o2) {
            return ComparisonChain.start()
                .compareTrueFirst(o1.isDisabled(), o2.isDisabled())
                .compareTrueFirst(o1.isDeprecated(), o2.isDeprecated())
                .compare(o1.getSymbolicName(), o2.getSymbolicName(), NaturalOrderComparator.INSTANCE)
                .compare(o1.getVersion(), o2.getVersion(), VersionComparator.INSTANCE)
                .result();
        }
    }

    public static <T> Maybe<T> validate(final T object, final RegisteredType type, final RegisteredTypeLoadingContext constraint) {
        RegisteredTypeKind kind = type!=null ? type.getKind() : constraint!=null ? constraint.getExpectedKind() : null;
        if (kind==null) {
            if (object instanceof AbstractBrooklynObjectSpec) kind=RegisteredTypeKind.SPEC;
            else kind=RegisteredTypeKind.BEAN;
        }
        return new RegisteredTypeKindVisitor<Maybe<T>>() {
            @Override
            protected Maybe<T> visitSpec() {
                return validateSpec(object, type, constraint);
            }

            @Override
            protected Maybe<T> visitBean() {
                return validateBean(object, type, constraint);
            }
        }.visit(kind);
    }

    private static <T> Maybe<T> validateBean(T object, RegisteredType type, final RegisteredTypeLoadingContext constraint) {
        if (object==null) return Maybe.absent("object is null");
        
        if (type!=null) {
            if (type.getKind()!=RegisteredTypeKind.BEAN)
                return Maybe.absent("Validating a bean when type is "+type.getKind()+" "+type);
            if (!isSubtypeOf(object.getClass(), type))
                return Maybe.absent(object+" does not have all the java supertypes of "+type);
        }

        if (constraint!=null) {
            if (constraint.getExpectedKind()!=RegisteredTypeKind.BEAN)
                return Maybe.absent("Validating a bean when constraint expected "+constraint.getExpectedKind());
            if (constraint.getExpectedJavaSuperType()!=null && !constraint.getExpectedJavaSuperType().isInstance(object))
                return Maybe.absent(object+" is not of the expected java supertype "+constraint.getExpectedJavaSuperType());
        }
        
        return Maybe.of(object);
    }

    private static <T> Maybe<T> validateSpec(T object, RegisteredType rType, final RegisteredTypeLoadingContext constraint) {
        if (object==null) return Maybe.absent("object is null");
        
        if (!(object instanceof AbstractBrooklynObjectSpec)) {
            Maybe.absent("Found "+object+" when expecting a spec");
        }
        Class<?> targetType = ((AbstractBrooklynObjectSpec<?,?>)object).getType();
        
        if (targetType==null) {
            Maybe.absent("Spec "+object+" does not have a target type");
        }
        
        if (rType!=null) {
            if (rType.getKind()!=RegisteredTypeKind.SPEC)
                Maybe.absent("Validating a spec when type is "+rType.getKind()+" "+rType);
            if (!isSubtypeOf(targetType, rType))
                Maybe.absent(object+" does not have all the java supertypes of "+rType);
        }

        if (constraint!=null) {
            if (constraint.getExpectedJavaSuperType()!=null) {
                if (!constraint.getExpectedJavaSuperType().isAssignableFrom(targetType)) {
                    Maybe.absent(object+" does not target the expected java supertype "+constraint.getExpectedJavaSuperType());
                }
                if (constraint.getExpectedJavaSuperType().isAssignableFrom(BrooklynObjectInternal.class)) {
                    // don't check spec type; any spec is acceptable
                } else {
                    @SuppressWarnings("unchecked")
                    Class<? extends AbstractBrooklynObjectSpec<?, ?>> specType = RegisteredTypeLoadingContexts.lookupSpecTypeForTarget( (Class<? extends BrooklynObject>) constraint.getExpectedJavaSuperType());
                    if (specType==null) {
                        // means a problem in our classification of spec types!
                        Maybe.absent(object+" is returned as spec for unexpected java supertype "+constraint.getExpectedJavaSuperType());
                    }
                    if (!specType.isAssignableFrom(object.getClass())) {
                        Maybe.absent(object+" is not a spec of the expected java supertype "+constraint.getExpectedJavaSuperType());
                    }
                }
            }
        }
        return Maybe.of(object);
    }

}

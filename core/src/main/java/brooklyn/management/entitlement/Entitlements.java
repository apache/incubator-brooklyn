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
package brooklyn.management.entitlement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.reflect.TypeToken;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.text.Strings;

/** @since 0.7.0 */
@Beta
public class Entitlements {

    private static final Logger log = LoggerFactory.getLogger(Entitlements.class);
    
    // ------------------- individual permissions
    
    public static EntitlementClass<Entity> SEE_ENTITY = new BasicEntitlementClassDefinition<Entity>("entity.see", Entity.class);
    
    public static EntitlementClass<EntityAndItem<String>> SEE_SENSOR = new BasicEntitlementClassDefinition<EntityAndItem<String>>("sensor.see", EntityAndItem. typeToken(String.class));
    
    public static EntitlementClass<EntityAndItem<String>> INVOKE_EFFECTOR = new BasicEntitlementClassDefinition<EntityAndItem<String>>("effector.invoke", EntityAndItem.typeToken(String.class));
    
    /** the permission to deploy an application, where parameter is some representation of the app to be deployed (spec instance or yaml plan) */
    public static EntitlementClass<Object> DEPLOY_APPLICATION = new BasicEntitlementClassDefinition<Object>("app.deploy", Object.class);

    /** catch-all for catalog, locations, scripting, usage, etc; 
     * NB1: all users can see HA status;
     * NB2: this may be refactored and deprecated in future */
    public static EntitlementClass<Void> SEE_ALL_SERVER_INFO = new BasicEntitlementClassDefinition<Void>("server.info.all.see", Void.class);
    
    /** permission to run untrusted code or embedded scripts at the server; 
     * secondary check required for any operation which could potentially grant root-level access */ 
    public static EntitlementClass<Void> ROOT = new BasicEntitlementClassDefinition<Void>("root", Void.class);

    public static enum EntitlementClassesEnum {
        ENTITLEMENT_SEE_ENTITY(SEE_ENTITY),
        ENTITLEMENT_SEE_SENSOR(SEE_SENSOR),
        ENTITLEMENT_INVOKE_EFFECTOR(INVOKE_EFFECTOR),
        ENTITLEMENT_DEPLOY_APPLICATION(DEPLOY_APPLICATION),
        ENTITLEMENT_SEE_ALL_SERVER_INFO(SEE_ALL_SERVER_INFO),
        ENTITLEMENT_ROOT(ROOT),
        ;
        
        private EntitlementClass<?> entitlementClass;

        private EntitlementClassesEnum(EntitlementClass<?> specificClass) {
            this.entitlementClass = specificClass;
        }
        public EntitlementClass<?> getEntitlementClass() {
            return entitlementClass;
        }
        
        public static EntitlementClassesEnum of(EntitlementClass<?> entitlementClass) {
            for (EntitlementClassesEnum x: values()) {
                if (entitlementClass.equals(x.getEntitlementClass())) return x;
            }
            return null;
        }
    }
    
    public static class EntityAndItem<T> {
        final Entity entity;
        final T item;
        public static <TT> TypeToken<EntityAndItem<TT>> typeToken(Class<TT> type) {
            return new TypeToken<Entitlements.EntityAndItem<TT>>() {
                private static final long serialVersionUID = -738154831809025407L;
            };
        }
        public EntityAndItem(Entity entity, T item) {
            this.entity = entity;
            this.item = item;
        }
        public Entity getEntity() {
            return entity;
        }
        public T getItem() {
            return item;
        }
        public static <T> EntityAndItem<T> of(Entity entity, T item) {
            return new EntityAndItem<T>(entity, item);
        }
    }

    /** 
     * These lifecycle operations are currently treated as effectors. This may change in the future.
     * @since 0.7.0 */
    @Beta
    public static class LifecycleEffectors {
        public static final String DELETE = "delete";
    }
    
    // ------------- permission sets -------------
    
    /** always ALLOW access to everything */
    public static EntitlementManager root() {
        return new EntitlementManager() {
            @Override
            public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> permission, T typeArgument) {
                return true;
            }
        };
    }

    /** always DENY access to anything which requires entitlements */
    public static EntitlementManager minimal() {
        return new EntitlementManager() {
            @Override
            public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> permission, T typeArgument) {
                return false;
            }
        };
    }

    public static class FineGrainedEntitlements {
    
        public static EntitlementManager anyOf(final EntitlementManager... checkers) {
            return new EntitlementManager() {
                @Override
                public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> permission, T typeArgument) {
                    for (EntitlementManager checker: checkers)
                        if (checker.isEntitled(context, permission, typeArgument))
                            return true;
                    return false;
                }
            };
        }
        
        public static EntitlementManager allOf(final EntitlementManager... checkers) {
            return new EntitlementManager() {
                @Override
                public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> permission, T typeArgument) {
                    for (EntitlementManager checker: checkers)
                        if (checker.isEntitled(context, permission, typeArgument))
                            return true;
                    return false;
                }
            };
        }

        public static <U> EntitlementManager allowing(EntitlementClass<U> permission, Predicate<U> test) {
            return new SinglePermissionEntitlementChecker<U>(permission, test);
        }

        public static <U> EntitlementManager allowing(EntitlementClass<U> permission) {
            return new SinglePermissionEntitlementChecker<U>(permission, Predicates.<U>alwaysTrue());
        }

        public static class SinglePermissionEntitlementChecker<U> implements EntitlementManager {
            final EntitlementClass<U> permission;
            final Predicate<U> test;
            
            protected SinglePermissionEntitlementChecker(EntitlementClass<U> permission, Predicate<U> test) {
                this.permission = permission;
                this.test = test;
            }
            
            @SuppressWarnings("unchecked")
            @Override
            public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> permission, T typeArgument) {
                if (!Objects.equal(this.permission, permission)) return false;
                return test.apply((U)typeArgument);
            }
            
        }
        public static EntitlementManager seeNonSecretSensors() {
            return allowing(SEE_SENSOR, new Predicate<EntityAndItem<String>>() {
                @Override
                public boolean apply(EntityAndItem<String> input) {
                    if (input == null) return false;
                    return !Entities.isSecret(input.getItem());
                }
            });
        }
        
    }
    
    /** allow read-only */
    public static EntitlementManager readOnly() {
        return FineGrainedEntitlements.anyOf(
            FineGrainedEntitlements.allowing(SEE_ENTITY),
            FineGrainedEntitlements.seeNonSecretSensors()
        );
    }

    // ------------- lookup conveniences -------------

    private static class PerThreadEntitlementContextHolder {
        public static final ThreadLocal<EntitlementContext> perThreadEntitlementsContextHolder = new ThreadLocal<EntitlementContext>();
    }

    public static EntitlementContext getEntitlementContext() {
        return PerThreadEntitlementContextHolder.perThreadEntitlementsContextHolder.get();
    }

    public static void setEntitlementContext(EntitlementContext context) {
        EntitlementContext oldContext = PerThreadEntitlementContextHolder.perThreadEntitlementsContextHolder.get();
        if (oldContext!=null && context!=null) {
            log.warn("Changing entitlement context from "+oldContext+" to "+context+"; context should have been reset or extended, not replaced");
            log.debug("Trace for entitlement context duplicate overwrite", new Throwable("Trace for entitlement context overwrite"));
        }
        PerThreadEntitlementContextHolder.perThreadEntitlementsContextHolder.set(context);
    }
    
    public static void clearEntitlementContext() {
        PerThreadEntitlementContextHolder.perThreadEntitlementsContextHolder.set(null);
    }
    
    public static <T> boolean isEntitled(EntitlementManager checker, EntitlementClass<T> permission, T typeArgument) {
        return checker.isEntitled(getEntitlementContext(), permission, typeArgument);
    }

    /** throws {@link NotEntitledException} if entitlement not available for current {@link #getEntitlementContext()} */
    public static <T> void checkEntitled(EntitlementManager checker, EntitlementClass<T> permission, T typeArgument) {
        if (!isEntitled(checker, permission, typeArgument)) {
            throw new NotEntitledException(getEntitlementContext(), permission, typeArgument);
        }
    }
    /** throws {@link NotEntitledException} if entitlement not available for current {@link #getEntitlementContext()} 
     * @since 0.7.0
     * @deprecated since 0.7.0, use {@link #checkEntitled(EntitlementManager, EntitlementClass, Object)};
     * kept briefly because there is some downstream usage*/
    public static <T> void requireEntitled(EntitlementManager checker, EntitlementClass<T> permission, T typeArgument) {
        checkEntitled(checker, permission, typeArgument);
    }
    
    // ----------------- initialization ----------------

    public static ConfigKey<String> GLOBAL_ENTITLEMENT_MANAGER = ConfigKeys.newStringConfigKey("brooklyn.entitlements.global", 
        "Class for entitlements in effect globally; "
        + "short names 'minimal', 'readonly', or 'root' are permitted here, with the default 'root' giving full access to all declared users; "
        + "or supply the name of an "+EntitlementManager.class+" class to instantiate, taking a 1-arg BrooklynProperties constructor or a 0-arg constructor",
        "root");
    
    public static EntitlementManager newManager(ResourceUtils loader, BrooklynProperties brooklynProperties) {
        EntitlementManager result = newGlobalManager(loader, brooklynProperties);
        // TODO read per user settings from brooklyn.properties, if set there ?
        return result;
    }
    private static EntitlementManager newGlobalManager(ResourceUtils loader, BrooklynProperties brooklynProperties) {
        String type = brooklynProperties.getConfig(GLOBAL_ENTITLEMENT_MANAGER);
        if ("root".equalsIgnoreCase(type)) return new PerUserEntitlementManagerWithDefault(root());
        if ("readonly".equalsIgnoreCase(type)) return new PerUserEntitlementManagerWithDefault(readOnly());
        if ("minimal".equalsIgnoreCase(type)) return new PerUserEntitlementManagerWithDefault(minimal());
        if (Strings.isNonBlank(type)) {
            try {
                Class<?> clazz = loader.getLoader().loadClass(type);
                Optional<?> result = Reflections.invokeConstructorWithArgs(clazz, brooklynProperties);
                if (result.isPresent()) return (EntitlementManager) result.get();
                return (EntitlementManager) clazz.newInstance();
            } catch (Exception e) { throw Exceptions.propagate(e); }
        }
        throw new IllegalStateException("Invalid entitlement manager specified: '"+type+"'");
    }
    

}

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
package brooklyn.entity.basic;

import java.util.Collection;

import javax.annotation.Nullable;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;
import brooklyn.util.collections.CollectionFunctionals;
import brooklyn.util.guava.SerializablePredicate;
import brooklyn.util.text.StringPredicates;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

@SuppressWarnings("serial")
public class EntityPredicates {

    public static Predicate<Entity> idEqualTo(final String val) {
        return idSatisfies(Predicates.equalTo(val));
    }
    
    public static Predicate<Entity> idSatisfies(final Predicate<? super String> condition) {
        return new IdSatisfies(condition);
    }
    
    protected static class IdSatisfies implements SerializablePredicate<Entity> {
        protected final Predicate<? super String> condition;
        protected IdSatisfies(Predicate<? super String> condition) {
            this.condition = condition;
        }
        @Override
        public boolean apply(@Nullable Entity input) {
            return (input != null) && condition.apply(input.getId());
        }
        @Override
        public String toString() {
            return "idSatisfies("+condition+")";
        }
    }

    /** @deprecated since 0.7.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<Entity> idEqualToOld(final T val) {
        return new SerializablePredicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return (input != null) && Objects.equal(input.getId(), val);
            }
        };
    }
    
    // ---------------------------
    
    public static Predicate<Entity> displayNameEqualTo(final String val) {
        return displayNameSatisfies(Predicates.equalTo(val));
    }
    
    public static Predicate<Entity> displayNameSatisfies(final Predicate<? super String> condition) {
        return new DisplayNameSatisfies(condition);
    }
    
    protected static class DisplayNameSatisfies implements SerializablePredicate<Entity> {
        protected final Predicate<? super String> condition;
        protected DisplayNameSatisfies(Predicate<? super String> condition) {
            this.condition = condition;
        }
        @Override
        public boolean apply(@Nullable Entity input) {
            return (input != null) && condition.apply(input.getDisplayName());
        }
        @Override
        public String toString() {
            return "displayNameSatisfies("+condition+")";
        }
    }

    /** @deprecated since 0.7.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<Entity> displayNameEqualToOld(final T val) {
        return new SerializablePredicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return (input != null) && Objects.equal(input.getDisplayName(), val);
            }
        };
    }
    
    /** @deprecated since 0.7.0 use {@link #displayNameSatisfies(Predicate)} to clarify this is *regex* matching
     * (passing {@link StringPredicates#matchesRegex(String)} as the predicate) */
    public static Predicate<Entity> displayNameMatches(final String regex) {
        return displayNameSatisfies(StringPredicates.matchesRegex(regex));
    }

    /** @deprecated since 0.7.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static class DisplayNameMatches implements SerializablePredicate<Entity> {
        private final String regex;
        DisplayNameMatches(String regex) {
            this.regex = regex;
        }
        @Override
        public boolean apply(@Nullable Entity input) {
            return (input != null && input.getDisplayName() != null) && input.getDisplayName().matches(regex);
        }
        @Override
        public String toString() {
            return "DisplayNameMatches("+regex+")";
        }
    };
    
    // ---------------------------

    public static Predicate<Entity> applicationIdEqualTo(final String val) {
        return applicationIdSatisfies(Predicates.equalTo(val));
    }

    public static Predicate<Entity> applicationIdSatisfies(final Predicate<? super String> condition) {
        return new ApplicationIdSatisfies(condition);
    }

    protected static class ApplicationIdSatisfies implements SerializablePredicate<Entity> {
        protected final Predicate<? super String> condition;
        protected ApplicationIdSatisfies(Predicate<? super String> condition) {
            this.condition = condition;
        }
        @Override
        public boolean apply(@Nullable Entity input) {
            return (input != null) && condition.apply(input.getApplicationId());
        }
        @Override
        public String toString() {
            return "applicationIdSatisfies("+condition+")";
        }
    }

    /** @deprecated since 0.7.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Predicate<Entity> applicationIdEqualToOld(final String val) {
        return new SerializablePredicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return (input != null) && val.equals(input.getApplicationId());
            }
        };
    }

    // ---------------------------
    
    public static <T> Predicate<Entity> attributeEqualTo(final AttributeSensor<T> attribute, final T val) {
        return attributeSatisfies(attribute, Predicates.equalTo(val));
    }
    
    public static <T> Predicate<Entity> attributeSatisfies(final AttributeSensor<T> attribute, final Predicate<T> condition) {
        return new AttributeSatisfies<T>(attribute, condition);
    }

    protected static class AttributeSatisfies<T> implements SerializablePredicate<Entity> {
        protected final AttributeSensor<T> attribute;
        protected final Predicate<T> condition;
        private AttributeSatisfies(AttributeSensor<T> attribute, Predicate<T> condition) {
            this.attribute = attribute;
            this.condition = condition;
        }
        @Override
        public boolean apply(@Nullable Entity input) {
            return (input != null) && condition.apply(input.getAttribute(attribute));
        }
        @Override
        public String toString() {
            return "attributeSatisfies("+attribute.getName()+","+condition+")";
        }
    }

    /** @deprecated since 0.7.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<Entity> attributeEqualToOld(final AttributeSensor<T> attribute, final T val) {
        return new SerializablePredicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return (input != null) && Objects.equal(input.getAttribute(attribute), val);
            }
        };
    }
    
    public static <T> Predicate<Entity> attributeNotEqualTo(final AttributeSensor<T> attribute, final T val) {
        return attributeSatisfies(attribute, Predicates.not(Predicates.equalTo(val)));
    }

    // ---------------------------

    public static <T> Predicate<Entity> configEqualTo(final ConfigKey<T> configKey, final T val) {
        return configSatisfies(configKey, Predicates.equalTo(val));
    }

    public static <T> Predicate<Entity> configSatisfies(final ConfigKey<T> configKey, final Predicate<T> condition) {
        return new ConfigKeySatisfies<T>(configKey, condition);
    }

    public static <T> Predicate<Entity> configEqualTo(final HasConfigKey<T> configKey, final T val) {
        return configEqualTo(configKey.getConfigKey(), val);
    }

    public static <T> Predicate<Entity> configSatisfies(final HasConfigKey<T> configKey, final Predicate<T> condition) {
        return new ConfigKeySatisfies<T>(configKey.getConfigKey(), condition);
    }

    protected static class ConfigKeySatisfies<T> implements SerializablePredicate<Entity> {
        protected final ConfigKey<T> configKey;
        protected final Predicate<T> condition;
        private ConfigKeySatisfies(ConfigKey<T> configKey, Predicate<T> condition) {
            this.configKey = configKey;
            this.condition = condition;
        }
        @Override
        public boolean apply(@Nullable Entity input) {
            return (input != null) && condition.apply(input.getConfig(configKey));
        }
        @Override
        public String toString() {
            return "configKeySatisfies("+configKey.getName()+","+condition+")";
        }
    }

    
    /** @deprecated since 0.7.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<Entity> configEqualToOld(final ConfigKey<T> configKey, final T val) {
        return new SerializablePredicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return (input != null) && Objects.equal(input.getConfig(configKey), val);
            }
        };
    }

    /** @deprecated since 0.7.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<Entity> configEqualToOld(final HasConfigKey<T> configKey, final T val) {
        return new SerializablePredicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return (input != null) && Objects.equal(input.getConfig(configKey), val);
            }
        };
    }

    // ---------------------------

    /**
     * Returns a predicate that determines if a given entity is a direct child of this {@code parent}.
     */
    public static <T> Predicate<Entity> isChildOf(final Entity parent) {
        return new IsChildOf(parent);
    }

    // if needed, could add parentSatisfies(...)
    
    protected static class IsChildOf implements SerializablePredicate<Entity> {
        protected final Entity parent;
        protected IsChildOf(Entity parent) {
            this.parent = parent;
        }
        @Override
        public boolean apply(@Nullable Entity input) {
            return (input != null) && Objects.equal(input.getParent(), parent);
        }
        @Override
        public String toString() {
            return "isChildOf("+parent+")";
        }
    }

    /** @deprecated since 0.7.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<Entity> isChildOfOld(final Entity parent) {
        return new SerializablePredicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return (input != null) && Objects.equal(input.getParent(), parent);
            }
        };
    }

    // ---------------------------
    
    public static <T> Predicate<Entity> isMemberOf(final Group group) {
        return new IsMemberOf(group);
    }

    protected static class IsMemberOf implements SerializablePredicate<Entity> {
        protected final Group group;
        protected IsMemberOf(Group group) {
            this.group = group;
        }
        @Override
        public boolean apply(@Nullable Entity input) {
            return (group != null) && (input != null) && group.hasMember(input);
        }
        @Override
        public String toString() {
            return "isMemberOf("+group+")";
        }
    }

    /** @deprecated since 0.7.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<Entity> isMemberOfOld(final Group group) {
        return new SerializablePredicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return (input != null) && group.hasMember(input);
            }
        };
    }

    // ---------------------------

    /**
     * Create a predicate that matches any entity who has an exact match for the given location
     * (i.e. {@code entity.getLocations().contains(location)}).
     */
    public static <T> Predicate<Entity> locationsIncludes(Location location) {
        return locationsSatisfy(CollectionFunctionals.contains(location));
        
    }
    
    public static <T> Predicate<Entity> locationsSatisfy(final Predicate<Collection<Location>> condition) {
        return new LocationsSatisfy(condition);
    }

    protected static class LocationsSatisfy implements SerializablePredicate<Entity> {
        protected final Predicate<Collection<Location>> condition;
        protected LocationsSatisfy(Predicate<Collection<Location>> condition) {
            this.condition = condition;
        }
        @Override
        public boolean apply(@Nullable Entity input) {
            return (input != null) && condition.apply(input.getLocations());
        }
        @Override
        public String toString() {
            return "locationsSatisfy("+condition+")";
        }
    }

    /** @deprecated since 0.7.0 use {@link #locationsIncludes(Location)} */
    @Deprecated 
    public static <T> Predicate<Entity> withLocation(final Location location) {
        return locationsIncludes(location);
    }
    
    /** @deprecated since 0.7.0 use {@link #locationsIncludes(Location)}, introduced to allow deserialization of anonymous inner class */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<Entity> withLocationOld(final Location location) {
        return new SerializablePredicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return (input != null) && input.getLocations().contains(location);
            }
        };
    }
    
    // ---------------------------

    public static <T> Predicate<Entity> isManaged() {
        return new IsManaged();
    }

    protected static class IsManaged implements SerializablePredicate<Entity> {
        @Override
        public boolean apply(@Nullable Entity input) {
            return (input != null) && Entities.isManaged(input);
        }
        @Override
        public String toString() {
            return "isManaged()";
        }
    }

    /** @deprecated since 0.7.0 use {@link #isManaged()} */ @Deprecated
    public static <T> Predicate<Entity> managed() {
        return isManaged();
    }

    /** @deprecated since 0.7.0 use {@link #isManaged()}, introduced to allow deserialization of anonymous inner class */
    @SuppressWarnings("unused") @Deprecated
    private static <T> Predicate<Entity> managedOld() {
        return new SerializablePredicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return (input != null) && Entities.isManaged(input);
            }
        };
    }

}

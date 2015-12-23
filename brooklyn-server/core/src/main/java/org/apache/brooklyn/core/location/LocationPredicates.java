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
package org.apache.brooklyn.core.location;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
import org.apache.brooklyn.util.guava.SerializablePredicate;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

@SuppressWarnings("serial")
public class LocationPredicates {

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<Location> idEqualToOld(final T val) {
        // TODO PERSISTENCE WORKAROUND
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return (input != null) && Objects.equal(input.getId(), val);
            }
        };
    }
    
    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<Location> displayNameEqualToOld(final T val) {
        // TODO PERSISTENCE WORKAROUND
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return (input != null) && Objects.equal(input.getDisplayName(), val);
            }
        };
    }
    
    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<Location> configEqualToOld(final ConfigKey<T> configKey, final T val) {
        // TODO PERSISTENCE WORKAROUND
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return (input != null) && Objects.equal(input.getConfig(configKey), val);
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<Location> configEqualToOld(final HasConfigKey<T> configKey, final T val) {
        // TODO PERSISTENCE WORKAROUND
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return (input != null) && Objects.equal(input.getConfig(configKey), val);
            }
        };
    }

    /**
     * Returns a predicate that determines if a given location is a direct child of this {@code parent}.
     */
    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<Location> isChildOfOld(final Location parent) {
        // TODO PERSISTENCE WORKAROUND
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return (input != null) && Objects.equal(input.getParent(), parent);
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<Location> isDescendantOfOld(final Location ancestor) {
        // TODO PERSISTENCE WORKAROUND
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                // assumes impossible to have cycles in location-hierarchy
                Location contenderAncestor = (input == null) ? input : input.getParent();
                while (contenderAncestor != null) {
                    if (Objects.equal(contenderAncestor, ancestor)) {
                        return true;
                    }
                    contenderAncestor = contenderAncestor.getParent();
                }
                return false;
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Predicate<Location> managedOld() {
        // TODO PERSISTENCE WORKAROUND
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return (input != null) && Locations.isManaged(input);
            }
        };
    }
    
    public static Predicate<Location> idEqualTo(final String val) {
        return idSatisfies(Predicates.equalTo(val));
    }
    
    public static Predicate<Location> idSatisfies(final Predicate<? super String> condition) {
        return new IdSatisfies(condition);
    }
    
    protected static class IdSatisfies implements SerializablePredicate<Location> {
        protected final Predicate<? super String> condition;
        protected IdSatisfies(Predicate<? super String> condition) {
            this.condition = condition;
        }
        @Override
        public boolean apply(@Nullable Location input) {
            return (input != null) && condition.apply(input.getId());
        }
        @Override
        public String toString() {
            return "idSatisfies("+condition+")";
        }
    }

    public static Predicate<Location> displayNameEqualTo(final String val) {
        return displayNameSatisfies(Predicates.equalTo(val));
    }
    
    public static Predicate<Location> displayNameSatisfies(final Predicate<? super String> condition) {
        return new DisplayNameSatisfies(condition);
    }
    
    protected static class DisplayNameSatisfies implements SerializablePredicate<Location> {
        protected final Predicate<? super String> condition;
        protected DisplayNameSatisfies(Predicate<? super String> condition) {
            this.condition = condition;
        }
        @Override
        public boolean apply(@Nullable Location input) {
            return (input != null) && condition.apply(input.getDisplayName());
        }
        @Override
        public String toString() {
            return "displayNameSatisfies("+condition+")";
        }
    }

    public static <T> Predicate<Location> configEqualTo(final ConfigKey<T> configKey, final T val) {
        return configSatisfies(configKey, Predicates.equalTo(val));
    }

    public static <T> Predicate<Location> configSatisfies(final ConfigKey<T> configKey, final Predicate<T> condition) {
        return new ConfigKeySatisfies<T>(configKey, condition);
    }

    public static <T> Predicate<Location> configEqualTo(final HasConfigKey<T> configKey, final T val) {
        return configEqualTo(configKey.getConfigKey(), val);
    }

    public static <T> Predicate<Location> configSatisfies(final HasConfigKey<T> configKey, final Predicate<T> condition) {
        return new ConfigKeySatisfies<T>(configKey.getConfigKey(), condition);
    }

    protected static class ConfigKeySatisfies<T> implements SerializablePredicate<Location> {
        protected final ConfigKey<T> configKey;
        protected final Predicate<T> condition;
        private ConfigKeySatisfies(ConfigKey<T> configKey, Predicate<T> condition) {
            this.configKey = configKey;
            this.condition = condition;
        }
        @Override
        public boolean apply(@Nullable Location input) {
            return (input != null) && condition.apply(input.getConfig(configKey));
        }
        @Override
        public String toString() {
            return "configKeySatisfies("+configKey.getName()+","+condition+")";
        }
    }
    
    /**
     * Returns a predicate that determines if a given location is a direct child of this {@code parent}.
     */
    public static Predicate<Location> isChildOf(final Location parent) {
        return new IsChildOf(parent);
    }

    // if needed, could add parentSatisfies(...)
    
    protected static class IsChildOf implements SerializablePredicate<Location> {
        protected final Location parent;
        protected IsChildOf(Location parent) {
            this.parent = parent;
        }
        @Override
        public boolean apply(@Nullable Location input) {
            return (input != null) && Objects.equal(input.getParent(), parent);
        }
        @Override
        public String toString() {
            return "isChildOf("+parent+")";
        }
    }

    /**
     * Returns a predicate that determines if a given location is a descendant of this {@code ancestor}.
     */
    public static <T> Predicate<Location> isDescendantOf(final Location ancestor) {
        return new IsDescendantOf(ancestor);
    }

    protected static class IsDescendantOf implements SerializablePredicate<Location> {
        protected final Location ancestor;
        protected IsDescendantOf(Location ancestor) {
            this.ancestor = ancestor;
        }
        @Override
        public boolean apply(@Nullable Location input) {
            // assumes impossible to have cycles in location-hierarchy
            Location contenderAncestor = (input == null) ? input : input.getParent();
            while (contenderAncestor != null) {
                if (Objects.equal(contenderAncestor, ancestor)) {
                    return true;
                }
                contenderAncestor = contenderAncestor.getParent();
            }
            return false;
        }
    }
    
    public static <T> Predicate<Location> managed() {
        return IsManaged.INSTANCE;
    }
    
    protected static class IsManaged implements Predicate<Location> {
        protected static final IsManaged INSTANCE = new IsManaged();
        @Override
        public boolean apply(@Nullable Location input) {
            return (input != null) && Locations.isManaged(input);
        }
    }
}

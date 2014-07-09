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
package brooklyn.location.basic;

import javax.annotation.Nullable;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.location.Location;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;

public class LocationPredicates {

    public static <T> Predicate<Location> idEqualTo(final T val) {
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return (input != null) && Objects.equal(input.getId(), val);
            }
        };
    }
    
    public static <T> Predicate<Location> displayNameEqualTo(final T val) {
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return (input != null) && Objects.equal(input.getDisplayName(), val);
            }
        };
    }
    
    public static <T> Predicate<Location> configEqualTo(final ConfigKey<T> configKey, final T val) {
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return (input != null) && Objects.equal(input.getConfig(configKey), val);
            }
        };
    }

    public static <T> Predicate<Location> configEqualTo(final HasConfigKey<T> configKey, final T val) {
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
    public static <T> Predicate<Location> isChildOf(final Location parent) {
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return (input != null) && Objects.equal(input.getParent(), parent);
            }
        };
    }

    /**
     * Returns a predicate that determines if a given location is a descendant of this {@code ancestor}.
     */
    public static <T> Predicate<Location> isDescendantOf(final Location ancestor) {
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

    public static <T> Predicate<Location> managed() {
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return (input != null) && Locations.isManaged(input);
            }
        };
    }
}

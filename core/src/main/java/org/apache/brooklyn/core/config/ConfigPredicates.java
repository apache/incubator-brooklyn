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
package org.apache.brooklyn.core.config;

import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.util.guava.SerializablePredicate;
import org.apache.brooklyn.util.text.StringPredicates;
import org.apache.brooklyn.util.text.WildcardGlobs;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

@SuppressWarnings("serial")
public class ConfigPredicates {

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Predicate<ConfigKey<?>> startingWithOld(final String prefix) {
        // TODO PERSISTENCE WORKAROUND
        return new Predicate<ConfigKey<?>>() {
            @Override
            public boolean apply(@Nullable ConfigKey<?> input) {
                return (input != null) && input.getName().startsWith(prefix);
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Predicate<ConfigKey<?>> matchingGlobOld(final String glob) {
        // TODO PERSISTENCE WORKAROUND
        return new Predicate<ConfigKey<?>>() {
            @Override
            public boolean apply(@Nullable ConfigKey<?> input) {
                return (input != null) && WildcardGlobs.isGlobMatched(glob, input.getName());
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Predicate<ConfigKey<?>> matchingRegexOld(final String regex) {
        // TODO PERSISTENCE WORKAROUND
        final Pattern p = Pattern.compile(regex);
        return new Predicate<ConfigKey<?>>() {
            @Override
            public boolean apply(@Nullable ConfigKey<?> input) {
                return (input != null) && p.matcher(input.getName()).matches();
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Predicate<ConfigKey<?>> nameMatchingOld(final Predicate<String> filter) {
        // TODO PERSISTENCE WORKAROUND
        return new Predicate<ConfigKey<?>>() {
            @Override
            public boolean apply(@Nullable ConfigKey<?> input) {
                return (input != null) && filter.apply(input.getName());
            }
        };
    }

    /** @deprecated since 0.9.0; use {@link #nameStartsWith(String)} */
    public static Predicate<ConfigKey<?>> startingWith(final String prefix) {
        return nameStartsWith(prefix);
    }

    /** @deprecated since 0.9.0; use {@link #nameMatchesGlob(String)} */
    public static Predicate<ConfigKey<?>> matchingGlob(final String glob) {
        return nameMatchesGlob(glob);
    }

    /** @deprecated since 0.9.0; use {@link #nameMatchesRegex(String)} */
    public static Predicate<ConfigKey<?>> matchingRegex(final String regex) {
        return nameMatchesRegex(regex);
    }

    /** @deprecated since 0.9.0; use {@link #nameSatisfies(Predicate)} */
    public static Predicate<ConfigKey<?>> nameMatching(final Predicate<String> filter) {
        return nameSatisfies(filter);
    }

    /**
     * @since 0.9.0
     */
    public static Predicate<ConfigKey<?>> nameStartsWith(final String prefix) {
        return nameSatisfies(StringPredicates.startsWith(prefix));
    }

    /**
     * @since 0.9.0
     */
    public static Predicate<ConfigKey<?>> nameMatchesGlob(final String glob) {
        return nameSatisfies(StringPredicates.matchesGlob(glob));
    }

    /**
     * @since 0.9.0
     */
    public static Predicate<ConfigKey<?>> nameMatchesRegex(final String regex) {
        return nameSatisfies(StringPredicates.matchesRegex(regex));
    }

    /**
     * @since 0.9.0
     */
    public static Predicate<ConfigKey<?>> nameEqualTo(final String val) {
        return nameSatisfies(Predicates.equalTo(val));
    }
    
    /**
     * @since 0.9.0
     */
    public static Predicate<ConfigKey<?>> nameSatisfies(final Predicate<? super String> condition) {
        return new NameSatisfies(condition);
    }
    
    /**
     * @since 0.9.0
     */
    protected static class NameSatisfies implements SerializablePredicate<ConfigKey<?>> {
        protected final Predicate<? super String> condition;
        protected NameSatisfies(Predicate<? super String> condition) {
            this.condition = condition;
        }
        @Override
        public boolean apply(@Nullable ConfigKey<?> input) {
            return (input != null) && condition.apply(input.getName());
        }
        @Override
        public String toString() {
            return "displayNameSatisfies("+condition+")";
        }
    }
}

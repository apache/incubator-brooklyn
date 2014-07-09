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
package brooklyn.config;

import java.util.regex.Pattern;

import javax.annotation.Nullable;

import brooklyn.util.text.WildcardGlobs;

import com.google.common.base.Predicate;

public class ConfigPredicates {

    public static Predicate<ConfigKey<?>> startingWith(final String prefix) {
        return new Predicate<ConfigKey<?>>() {
            @Override
            public boolean apply(@Nullable ConfigKey<?> input) {
                return (input != null) && input.getName().startsWith(prefix);
            }
        };
    }

    public static Predicate<ConfigKey<?>> matchingGlob(final String glob) {
        return new Predicate<ConfigKey<?>>() {
            @Override
            public boolean apply(@Nullable ConfigKey<?> input) {
                return (input != null) && WildcardGlobs.isGlobMatched(glob, input.getName());
            }
        };
    }

    public static Predicate<ConfigKey<?>> matchingRegex(final String regex) {
        final Pattern p = Pattern.compile(regex);
        return new Predicate<ConfigKey<?>>() {
            @Override
            public boolean apply(@Nullable ConfigKey<?> input) {
                return (input != null) && p.matcher(input.getName()).matches();
            }
        };
    }

    public static Predicate<ConfigKey<?>> nameMatching(final Predicate<String> filter) {
        return new Predicate<ConfigKey<?>>() {
            @Override
            public boolean apply(@Nullable ConfigKey<?> input) {
                return (input != null) && filter.apply(input.getName());
            }
        };
    }
    
}

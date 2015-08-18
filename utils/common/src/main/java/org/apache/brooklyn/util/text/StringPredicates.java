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
package org.apache.brooklyn.util.text;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.util.collections.MutableSet;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class StringPredicates {

    /** predicate form of {@link Strings#isBlank(CharSequence)} */
    public static <T extends CharSequence> Predicate<T> isBlank() {
        return new IsBlank<T>();
    }

    private static final class IsBlank<T extends CharSequence> implements Predicate<T> {
        @Override
        public boolean apply(@Nullable CharSequence input) {
            return Strings.isBlank(input);
        }

        @Override
        public String toString() {
            return "isBlank()";
        }
    }

    /** @deprecated since 0.7.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Predicate<CharSequence> isBlankOld() {
        return new Predicate<CharSequence>() {
            @Override
            public boolean apply(@Nullable CharSequence input) {
                return Strings.isBlank(input);
            }
            @Override
            public String toString() {
                return "isBlank";
            }
        };
    }


    /** Tests if object is non-null and not a blank string.
     * <p>
     * Predicate form of {@link Strings#isNonBlank(CharSequence)} also accepting objects non-null, for convenience */
    public static <T> Predicate<T> isNonBlank() {
        return new IsNonBlank<T>();
    }

    private static final class IsNonBlank<T> implements Predicate<T> {
        @Override
        public boolean apply(@Nullable Object input) {
            if (input==null) return false;
            if (!(input instanceof CharSequence)) return true;
            return Strings.isNonBlank((CharSequence)input);
        }

        @Override
        public String toString() {
            return "isNonBlank()";
        }
    }
    
    // -----------------
    
    public static <T extends CharSequence> Predicate<T> containsLiteralIgnoreCase(final String fragment) {
        return new ContainsLiteralIgnoreCase<T>(fragment);
    }

    private static final class ContainsLiteralIgnoreCase<T extends CharSequence> implements Predicate<T> {
        private final String fragment;

        private ContainsLiteralIgnoreCase(String fragment) {
            this.fragment = fragment;
        }

        @Override
        public boolean apply(@Nullable CharSequence input) {
            return Strings.containsLiteralIgnoreCase(input, fragment);
        }

        @Override
        public String toString() {
            return "containsLiteralCaseInsensitive("+fragment+")";
        }
    }

    public static <T extends CharSequence> Predicate<T> containsLiteral(final String fragment) {
        return new ContainsLiteral<T>(fragment);
    }
    
    private static final class ContainsLiteral<T extends CharSequence> implements Predicate<T> {
        private final String fragment;

        private ContainsLiteral(String fragment) {
            this.fragment = fragment;
        }

        @Override
        public boolean apply(@Nullable CharSequence input) {
            return Strings.containsLiteral(input, fragment);
        }

        @Override
        public String toString() {
            return "containsLiteral("+fragment+")";
        }
    }

    /** @deprecated since 0.7.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Predicate<CharSequence> containsLiteralCaseInsensitiveOld(final String fragment) {
        return new Predicate<CharSequence>() {
            @Override
            public boolean apply(@Nullable CharSequence input) {
                return Strings.containsLiteralIgnoreCase(input, fragment);
            }
            @Override
            public String toString() {
                return "containsLiteralCaseInsensitive("+fragment+")";
            }
        };
    }

    /** @deprecated since 0.7.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Predicate<CharSequence> containsLiteralOld(final String fragment) {
        return new Predicate<CharSequence>() {
            @Override
            public boolean apply(@Nullable CharSequence input) {
                return Strings.containsLiteral(input, fragment);
            }
            @Override
            public String toString() {
                return "containsLiteral("+fragment+")";
            }
        };
    }
    
    // -----------------
    
    public static <T extends CharSequence> Predicate<T> containsAllLiterals(final String... fragments) {
        List<Predicate<CharSequence>> fragmentPredicates = Lists.newArrayList();
        for (String fragment : fragments) {
            fragmentPredicates.add(containsLiteral(fragment));
        }
        return Predicates.and(fragmentPredicates);
    }

    /** @deprecated since 0.7.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Predicate<CharSequence> containsAllLiteralsOld(final String... fragments) {
        return Predicates.and(Iterables.transform(Arrays.asList(fragments), new Function<String,Predicate<CharSequence>>() {
            @Override
            public Predicate<CharSequence> apply(String input) {
                return containsLiteral(input);
            }
        }));
    }
    
    // -----------------

    public static Predicate<CharSequence> containsRegex(final String regex) {
        // "Pattern" ... what a bad name :)
        return Predicates.containsPattern(regex);
    }

    // -----------------
    
    public static <T extends CharSequence> Predicate<T> startsWith(final String prefix) {
        return new StartsWith<T>(prefix);
    }

    private static final class StartsWith<T extends CharSequence> implements Predicate<T> {
        private final String prefix;
        private StartsWith(String prefix) {
            this.prefix = prefix;
        }
        @Override
        public boolean apply(CharSequence input) {
            return (input != null) && input.toString().startsWith(prefix);
        }
        @Override
        public String toString() {
            return "startsWith("+prefix+")";
        }
    }

    /** @deprecated since 0.7.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Predicate<CharSequence> startsWithOld(final String prefix) {
        return new Predicate<CharSequence>() {
            @Override
            public boolean apply(CharSequence input) {
                return (input != null) && input.toString().startsWith(prefix);
            }
        };
    }

    // -----------------
    
    /** true if the object *is* a {@link CharSequence} starting with the given prefix */
    public static Predicate<Object> isStringStartingWith(final String prefix) {
        return Predicates.<Object>and(Predicates.instanceOf(CharSequence.class),
            Predicates.compose(startsWith(prefix), StringFunctions.toStringFunction()));
    }

    /** @deprecated since 0.7.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Predicate<Object> isStringStartingWithOld(final String prefix) {
        return new Predicate<Object>() {
            @Override
            public boolean apply(Object input) {
                return (input instanceof CharSequence) && input.toString().startsWith(prefix);
            }
        };
    }

    // ---------------
    
    public static <T> Predicate<T> equalToAny(Iterable<T> vals) {
        return new EqualToAny<T>(vals);
    }

    private static class EqualToAny<T> implements Predicate<T>, Serializable {
        private static final long serialVersionUID = 6209304291945204422L;
        private final Set<T> vals;
        
        public EqualToAny(Iterable<? extends T> vals) {
            this.vals = MutableSet.copyOf(vals); // so allows nulls
        }
        @Override
        public boolean apply(T input) {
            return vals.contains(input);
        }
        @Override
        public String toString() {
            return "equalToAny("+vals+")";
        }
    }

    // -----------
    
    public static <T extends CharSequence> Predicate<T> matchesRegex(final String regex) {
        return new MatchesRegex<T>(regex);
    }

    protected static class MatchesRegex<T extends CharSequence> implements Predicate<T> {
        protected final String regex;
        protected MatchesRegex(String regex) {
            this.regex = regex;
        }
        @Override
        public boolean apply(CharSequence input) {
            return (input != null) && input.toString().matches(regex);
        }
        @Override
        public String toString() {
            return "matchesRegex("+regex+")";
        }
    }
    
    public static <T extends CharSequence> Predicate<T> matchesGlob(final String glob) {
        return new MatchesGlob<T>(glob);
    }

    protected static class MatchesGlob<T extends CharSequence> implements Predicate<T> {
        protected final String glob;
        protected MatchesGlob(String glob) {
            this.glob = glob;
        }
        @Override
        public boolean apply(CharSequence input) {
            return (input != null) && WildcardGlobs.isGlobMatched(glob, input.toString());
        }
        @Override
        public String toString() {
            return "matchesGlob("+glob+")";
        }
    }

}

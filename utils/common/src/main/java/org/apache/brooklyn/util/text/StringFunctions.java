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

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nullable;

import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

public class StringFunctions {

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<String,String> appendOld(final String suffix) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<String, String>() {
            @Override
            @Nullable
            public String apply(@Nullable String input) {
                if (input==null) return null;
                return input + suffix;
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<String,String> prependOld(final String prefix) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<String, String>() {
            @Override
            @Nullable
            public String apply(@Nullable String input) {
                if (input==null) return null;
                return prefix + input;
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<Object, String> formatterOld(final String pattern) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<Object, String>() {
            public String apply(@Nullable Object input) {
                return String.format(pattern, input);
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<Object[], String> formatterForArrayOld(final String pattern) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<Object[], String>() {
            public String apply(@Nullable Object[] input) {
                return String.format(pattern, input);
            }
        };
    }
    
    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<Iterable<?>, String> joinerOld(final String separator) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<Iterable<?>, String>() {
            public String apply(@Nullable Iterable<?> input) {
                return Strings.join(input, separator);
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<Object[], String> joinerForArrayOld(final String separator) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<Object[], String>() {
            public String apply(@Nullable Object[] input) {
                if (input == null) return Strings.EMPTY;
                return Strings.join(input, separator);
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<String,Integer> lengthOld() {
        // TODO PERSISTENCE WORKAROUND
        return new Function<String,Integer>() {
            @Override
            public Integer apply(@Nullable String input) {
                if (input == null) return -1;
                return input.length();
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<String,String> surroundOld(final String prefix, final String suffix) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<String,String>() {
            @Override
            public String apply(@Nullable String input) {
                if (input == null) return null;
                return prefix+input+suffix;
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<String, String> trimOld() {
        // TODO PERSISTENCE WORKAROUND
        return new Function<String, String>() {
            @Override
            public String apply(@Nullable String input) {
                if (input == null) return null;
                if (Strings.isBlank(input)) return Strings.EMPTY;
                return CharMatcher.BREAKING_WHITESPACE.trimFrom(input);
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<String, String> toLowerCaseOld() {
        // TODO PERSISTENCE WORKAROUND
        return new Function<String, String>() {
            @Override
            public String apply(String input) {
                return input.toLowerCase();
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<String, String> toUpperCaseOld() {
        // TODO PERSISTENCE WORKAROUND
        return new Function<String, String>() {
            @Override
            public String apply(String input) {
                return input.toUpperCase();
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<String, String> convertCaseOld(final CaseFormat src, final CaseFormat target) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<String, String>() {
            @Override
            public String apply(String input) {
                return src.to(target, input);
            }
        };
    }

    public static Function<String,String> append(final String suffix) {
        return new AppendFunction(checkNotNull(suffix, "suffix"));
    }

    private static class AppendFunction implements Function<String, String> {
        private final String suffix;

        public AppendFunction(String suffix) {
            this.suffix = suffix;
        }
        @Override
        @Nullable
        public String apply(@Nullable String input) {
            if (input==null) return null;
            return input + suffix;
        }
    };

    public static Function<String,String> prepend(final String prefix) {
        return new PrependFunction(checkNotNull(prefix, "prefix"));
    }
    
    protected static class PrependFunction implements Function<String, String> {
        private final String prefix;

        public PrependFunction(String prefix) {
            this.prefix = prefix;
        }

        @Override
        @Nullable
        public String apply(@Nullable String input) {
            if (input==null) return null;
            return prefix + input;
        }
    }

    /** given e.g. "hello %s" returns a function which will insert a string into that pattern */
    public static Function<Object, String> formatter(final String pattern) {
        return new FormatterFunction(pattern);
    }

    protected static class FormatterFunction implements Function<Object, String> {
        private final String pattern;
        
        FormatterFunction(String pattern) {
            this.pattern = pattern;
        }
        public String apply(@Nullable Object input) {
            return String.format(pattern, input);
        }
    };

    /** given e.g. "hello %s %s" returns a function which will insert an array of two strings into that pattern */
    public static Function<Object[], String> formatterForArray(final String pattern) {
        return new FormatterForArrayFunction(checkNotNull(pattern, "pattern"));
    }
    
    protected static class FormatterForArrayFunction implements Function<Object[], String> {
        private final String pattern;
        
        public FormatterForArrayFunction(String pattern) {
            this.pattern = pattern;
        }
        public String apply(@Nullable Object[] input) {
            return String.format(pattern, input);
        }
    }
    
    /** 
     * Given e.g. "hello %s %s" returns a function which will insert an Iterable of two strings into that pattern
     * 
     * @since 0.9.0
     */
    public static Function<Iterable<?>, String> formatterForIterable(final String pattern) {
        return new FormatterForIterableFunction(pattern);
    }

    protected static class FormatterForIterableFunction implements Function<Iterable<?>, String> {
        final String pattern;

        public FormatterForIterableFunction(String pattern) {
            this.pattern = pattern;
        }

        public String apply(@Nullable Iterable<?> input) {
            Object[] arr = (input == null) ? null : Iterables.toArray(input, Object.class);
            return String.format(pattern, arr);
        }
    }

    /** joins the given objects in a collection as a toString with the given separator */
    public static Function<Iterable<?>, String> joiner(final String separator) {
        return new JoinerFunction(separator);
    }

    private static class JoinerFunction implements Function<Iterable<?>, String> {
        private final String separator;

        public JoinerFunction(String separator) {
            this.separator = separator;
        }
        public String apply(@Nullable Iterable<?> input) {
            return Strings.join(input, separator);
        }
    }
    
    /** joins the given objects as a toString with the given separator, but expecting an array of objects, not a collection */
    public static Function<Object[], String> joinerForArray(final String separator) {
        return new JoinerForArrayFunction(checkNotNull(separator, "separator"));
    }

    private static class JoinerForArrayFunction implements Function<Object[], String> {
        private final String separator;

        protected JoinerForArrayFunction(String separator) {
            this.separator = separator;
        }
        public String apply(@Nullable Object[] input) {
            if (input == null) return Strings.EMPTY;
            return Strings.join(input, separator);
        }
    }

    /** provided here as a convenience; prefer {@link Functions#toStringFunction()} */
    public static Function<Object,String> toStringFunction() {
        return Functions.toStringFunction();
    }

    /** returns function which gives length of input, with -1 for nulls */
    public static Function<String,Integer> length() {
        return new LengthFunction();
    }

    protected static class LengthFunction implements Function<String,Integer> {
        @Override
        public Integer apply(@Nullable String input) {
            if (input == null) return -1;
            return input.length();
        }
    }

    /** Surrounds an input string with the given prefix and suffix */
    public static Function<String,String> surround(final String prefix, final String suffix) {
        Preconditions.checkNotNull(prefix);
        Preconditions.checkNotNull(suffix);
        return new SurroundFunction(prefix, suffix);
    }
    
    protected static class SurroundFunction implements Function<String,String> {
        private final String prefix;
        private final String suffix;
        public SurroundFunction(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }
        @Override
        public String apply(@Nullable String input) {
            if (input == null) return null;
            return prefix+input+suffix;
        }
    }

    public static Function<String, String> trim() {
        return new TrimFunction();
    }
    
    protected static class TrimFunction implements Function<String, String> {
        @Override
        public String apply(@Nullable String input) {
            if (input == null) return null;
            if (Strings.isBlank(input)) return Strings.EMPTY;
            return CharMatcher.BREAKING_WHITESPACE.trimFrom(input);
        }
    }

    public static Function<String, String> toLowerCase() {
        return new LowerCaseFunction();
    }
    
    protected static class LowerCaseFunction implements Function<String, String> {
        @Override
        public String apply(String input) {
            return input.toLowerCase();
        }
    }

    public static Function<String, String> toUpperCase() {
        return new UpperCaseFunction();
    }
    
    protected static class UpperCaseFunction implements Function<String, String> {
        @Override
        public String apply(String input) {
            return input.toUpperCase();
        }
    }

    public static Function<String, String> convertCase(final CaseFormat src, final CaseFormat target) {
        return new ConvertCaseFunction(checkNotNull(src, "src"), checkNotNull(target, "target"));
    }
    
    protected static class ConvertCaseFunction implements Function<String, String> {
       private final CaseFormat src;
       private final CaseFormat target;

       public ConvertCaseFunction(CaseFormat src, CaseFormat target) {
          this.src = src;
          this.target = target;
       }

       @Override
       public String apply(String input) {
          return src.to(target, input);
       }
    }

    public static class RegexReplacer implements Function<String, String> {
        private final String pattern;
        private final String replacement;

        public RegexReplacer(String pattern, String replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }

        @Nullable
        @Override
        public String apply(@Nullable String s) {
            return Strings.replaceAllRegex(s, pattern, replacement);
        }
    }
}

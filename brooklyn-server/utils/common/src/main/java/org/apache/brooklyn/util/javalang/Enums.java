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
package org.apache.brooklyn.util.javalang;

import java.util.Arrays;
import java.util.Set;

import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.StringFunctions;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;

public class Enums {
    
    /** returns a function which given an enum, returns its <code>name()</code> function 
     * @deprecated since 0.7.0 use {@link #nameFunction()} to avoid inner class */
    @Deprecated
    public static Function<Enum<?>,String> enumValueNameFunction() {
        return new Function<Enum<?>,String>() {
            @Override
            public String apply(Enum<?> input) {
                return input.name();
            }
        };
    }

    private static final class EnumToNameFunction implements Function<Enum<?>, String> {
        @Override
        public String apply(Enum<?> input) {
            return input.name();
        }
    }

    /** returns a function which given an enum, returns its <code>name()</code> function */
    public static Function<Enum<?>,String> nameFunction() {
        return new EnumToNameFunction();
    }

    private static final class EnumFromStringFunction<T extends Enum<?>> implements Function<String,T> {
        private final Class<T> type;
        public EnumFromStringFunction(Class<T> type) { this.type = type; }
        @Override
        public T apply(String input) {
            return valueOfIgnoreCase(type, input).orNull();
        }
    }

    /** returns a function which given a string, produces an enum of the given type or null */
    public static <T extends Enum<?>> Function<String,T> fromStringFunction(Class<T> type) {
        return new EnumFromStringFunction<T>(type);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Enum<?>> T[] values(Class<T> type) {
        try {
            return (T[]) type.getMethod("values").invoke(null);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    /** as {@link #checkAllEnumeratedIgnoreCase(String, Enum[], String...)} using the same default strategy
     * that {@link #valueOfIgnoreCase(Class, String)} applies */
    public static void checkAllEnumeratedIgnoreCase(Class<? extends Enum<?>> type, String ...explicitValues) {
        checkAllEnumeratedIgnoreCase(JavaClassNames.simpleClassName(type), values(type), explicitValues);
    }
    /** checks that all accepted enum values are represented by the given set of explicit values */
    public static void checkAllEnumeratedIgnoreCase(String contextMessage, Enum<?>[] enumValues, String ...explicitValues) {
        MutableSet<String> explicitValuesSet = MutableSet.copyOf(Iterables.transform(Arrays.asList(explicitValues), StringFunctions.toLowerCase()));
        
        Set<Enum<?>> missingEnums = MutableSet.of();
        for (Enum<?> e: enumValues) {
            if (explicitValuesSet.remove(e.name().toLowerCase())) continue;
            if (explicitValuesSet.remove(e.toString().toLowerCase())) continue;
            
            if (explicitValuesSet.remove(CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, e.name()).toLowerCase())) continue;
            if (explicitValuesSet.remove(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, e.toString()).toLowerCase())) continue;
            
            if (explicitValuesSet.remove(CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, e.toString()).toLowerCase())) continue;
            if (explicitValuesSet.remove(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, e.name()).toLowerCase())) continue;
            
            missingEnums.add(e);
        }
        
        if (!missingEnums.isEmpty() || !explicitValuesSet.isEmpty()) {
            throw new IllegalStateException("Not all options for "+contextMessage+" are enumerated; "
                + "leftover enums = "+missingEnums+"; "
                + "leftover values = "+explicitValuesSet);
        }
    }
    
    /** as {@link #valueOfIgnoreCase(String, Enum[], String)} for all values of the given enum and using the enum type as the message */
    public static <T extends Enum<?>> Maybe<T> valueOfIgnoreCase(Class<T> type, String givenValue) {
        return valueOfIgnoreCase(JavaClassNames.simpleClassName(type), values(type), givenValue);
    }
    
    /** attempts to match the givenValue against the given enum values, first looking for exact matches (against name and toString),
     * then matching ignoring case, 
     * then matching with {@link CaseFormat#UPPER_UNDERSCORE} converted to {@link CaseFormat#LOWER_CAMEL},
     * then matching with {@link CaseFormat#LOWER_CAMEL} converted to {@link CaseFormat#UPPER_UNDERSCORE}
     * (including case insensitive matches for the final two)
     **/
    public static <T extends Enum<?>> Maybe<T> valueOfIgnoreCase(String contextMessage, T[] enumValues, String givenValue) {
        if (givenValue==null) 
            return Maybe.absent(new IllegalStateException("Value for "+contextMessage+" must not be null"));
        if (Strings.isBlank(givenValue)) 
            return Maybe.absent(new IllegalStateException("Value for "+contextMessage+" must not be blank"));
        
        for (T v: enumValues)
            if (v.name().equals(givenValue)) 
                return Maybe.of(v);
        for (T v: enumValues)
            if (v.toString().equals(givenValue)) 
                return Maybe.of(v);
        for (T v: enumValues)
            if (v.name().equalsIgnoreCase(givenValue)) return 
                Maybe.of(v);
        for (T v: enumValues)
            if (v.toString().equalsIgnoreCase(givenValue)) 
                return Maybe.of(v);
        for (T v: enumValues)
            if (CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, v.name()).equals(givenValue)) 
                return Maybe.of(v);
        for (T v: enumValues)
            if (CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, v.toString()).equals(givenValue)) 
                return Maybe.of(v);
        for (T v: enumValues)
            if (CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, v.name()).equalsIgnoreCase(givenValue)) 
                return Maybe.of(v);
        for (T v: enumValues)
            if (CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, v.toString()).equalsIgnoreCase(givenValue)) 
                return Maybe.of(v);
        for (T v: enumValues)
            if (CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, v.toString()).equalsIgnoreCase(givenValue)) 
                return Maybe.of(v);
        for (T v: enumValues)
            if (CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, v.name()).equals(givenValue)) 
                return Maybe.of(v);
        for (T v: enumValues)
            if (CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, v.toString()).equals(givenValue)) 
                return Maybe.of(v);
        for (T v: enumValues)
            if (CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, v.name()).equalsIgnoreCase(givenValue)) 
                return Maybe.of(v);
        
        return Maybe.absent(new IllegalStateException("Invalid value "+givenValue+" for "+contextMessage));
    }
    
}

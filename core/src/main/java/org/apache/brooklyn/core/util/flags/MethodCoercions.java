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
package org.apache.brooklyn.core.util.flags;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

import javax.annotation.Nullable;

import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A way of binding a loosely-specified method call into a strongly-typed Java method call.
 */
public class MethodCoercions {

    /**
     * Returns a predicate that matches a method with the given name, and a single parameter that
     * {@link org.apache.brooklyn.core.util.flags.TypeCoercions#tryCoerce(Object, com.google.common.reflect.TypeToken)} can process
     * from the given argument.
     *
     * @param methodName name of the method
     * @param argument argument that is intended to be given
     * @return a predicate that will match a compatible method
     */
    public static Predicate<Method> matchSingleParameterMethod(final String methodName, final Object argument) {
        checkNotNull(methodName, "methodName");
        checkNotNull(argument, "argument");

        return new Predicate<Method>() {
            @Override
            public boolean apply(@Nullable Method input) {
                if (input == null) return false;
                if (!input.getName().equals(methodName)) return false;
                Type[] parameterTypes = input.getGenericParameterTypes();
                return parameterTypes.length == 1
                        && TypeCoercions.tryCoerce(argument, TypeToken.of(parameterTypes[0])).isPresentAndNonNull();

            }
        };
    }

    /**
     * Tries to find a single-parameter method with a parameter compatible with (can be coerced to) the argument, and
     * invokes it.
     *
     * @param instance the object to invoke the method on
     * @param methodName the name of the method to invoke
     * @param argument the argument to the method's parameter.
     * @return the result of the method call, or {@link org.apache.brooklyn.util.guava.Maybe#absent()} if method could not be matched.
     */
    public static Maybe<?> tryFindAndInvokeSingleParameterMethod(final Object instance, final String methodName, final Object argument) {
        Class<?> clazz = instance.getClass();
        Iterable<Method> methods = Arrays.asList(clazz.getMethods());
        Optional<Method> matchingMethod = Iterables.tryFind(methods, matchSingleParameterMethod(methodName, argument));
        if (matchingMethod.isPresent()) {
            Method method = matchingMethod.get();
            try {
                Type paramType = method.getGenericParameterTypes()[0];
                Object coercedArgument = TypeCoercions.coerce(argument, TypeToken.of(paramType));
                return Maybe.of(method.invoke(instance, coercedArgument));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw Exceptions.propagate(e);
            }
        } else {
            return Maybe.absent();
        }
    }

    /**
     * Returns a predicate that matches a method with the given name, and parameters that
     * {@link org.apache.brooklyn.core.util.flags.TypeCoercions#tryCoerce(Object, com.google.common.reflect.TypeToken)} can process
     * from the given list of arguments.
     *
     * @param methodName name of the method
     * @param arguments arguments that is intended to be given
     * @return a predicate that will match a compatible method
     */
    public static Predicate<Method> matchMultiParameterMethod(final String methodName, final List<?> arguments) {
        checkNotNull(methodName, "methodName");
        checkNotNull(arguments, "arguments");

        return new Predicate<Method>() {
            @Override
            public boolean apply(@Nullable Method input) {
                if (input == null) return false;
                if (!input.getName().equals(methodName)) return false;
                int numOptionParams = arguments.size();
                Type[] parameterTypes = input.getGenericParameterTypes();
                if (parameterTypes.length != numOptionParams) return false;

                for (int paramCount = 0; paramCount < numOptionParams; paramCount++) {
                    if (!TypeCoercions.tryCoerce(((List) arguments).get(paramCount),
                            TypeToken.of(parameterTypes[paramCount])).isPresentAndNonNull()) return false;
                }
                return true;
            }
        };
    }

    /**
     * Tries to find a multiple-parameter method with each parameter compatible with (can be coerced to) the
     * corresponding argument, and invokes it.
     *
     * @param instance the object to invoke the method on
     * @param methodName the name of the method to invoke
     * @param argument a list of the arguments to the method's parameters.
     * @return the result of the method call, or {@link org.apache.brooklyn.util.guava.Maybe#absent()} if method could not be matched.
     */
    public static Maybe<?> tryFindAndInvokeMultiParameterMethod(final Object instance, final String methodName, final List<?> arguments) {
        Class<?> clazz = instance.getClass();
        Iterable<Method> methods = Arrays.asList(clazz.getMethods());
        Optional<Method> matchingMethod = Iterables.tryFind(methods, matchMultiParameterMethod(methodName, arguments));
        if (matchingMethod.isPresent()) {
            Method method = matchingMethod.get();
            try {
                int numOptionParams = ((List)arguments).size();
                Object[] coercedArguments = new Object[numOptionParams];
                for (int paramCount = 0; paramCount < numOptionParams; paramCount++) {
                    Object argument = arguments.get(paramCount);
                    Type paramType = method.getGenericParameterTypes()[paramCount];
                    coercedArguments[paramCount] = TypeCoercions.coerce(argument, TypeToken.of(paramType));
                }
                return Maybe.of(method.invoke(instance, coercedArguments));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw Exceptions.propagate(e);
            }
        } else {
            return Maybe.absent();
        }
    }

    /**
     * Tries to find a method with each parameter compatible with (can be coerced to) the corresponding argument, and invokes it.
     *
     * @param instance the object to invoke the method on
     * @param methodName the name of the method to invoke
     * @param argument a list of the arguments to the method's parameters, or a single argument for a single-parameter method.
     * @return the result of the method call, or {@link org.apache.brooklyn.util.guava.Maybe#absent()} if method could not be matched.
     */
    public static Maybe<?> tryFindAndInvokeBestMatchingMethod(final Object instance, final String methodName, final Object argument) {
        if (argument instanceof List) {
            List<?> arguments = (List<?>) argument;

            // ambiguous case: we can't tell if the user is using the multi-parameter syntax, or the single-parameter
            // syntax for a method which takes a List parameter. So we try one, then fall back to the other.

            Maybe<?> maybe = tryFindAndInvokeMultiParameterMethod(instance, methodName, arguments);
            if (maybe.isAbsent())
                maybe = tryFindAndInvokeSingleParameterMethod(instance, methodName, argument);

            return maybe;
        } else {
            return tryFindAndInvokeSingleParameterMethod(instance, methodName, argument);
        }
    }

}

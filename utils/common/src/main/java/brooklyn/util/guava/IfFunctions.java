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
package brooklyn.util.guava;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;

/** Utilities for building {@link Function} instances which return specific values
 * (or {@link Supplier} or {@link Function} instances) when certain predicates are satisfied,
 * tested in order and returning the first matching,
 * with support for an "else" default value if none are satisfied (null by default). */
public class IfFunctions {

    public static <I,O> IfFunctionBuilder<I,O> newInstance(Class<I> testType, Class<O> returnType) {
        return new IfFunctionBuilder<I,O>();
    }
    
    public static <I> IfFunctionBuilderApplyingFirst<I> ifPredicate(Predicate<? super I> test) {
        return new IfFunctionBuilderApplyingFirst<I>(test);
    }
    public static <I> IfFunctionBuilderApplyingFirst<I> ifEquals(I test) {
        return ifPredicate(Predicates.equalTo(test));
    }
    public static <I> IfFunctionBuilderApplyingFirst<I> ifNotEquals(I test) {
        return ifPredicate(Predicates.not(Predicates.equalTo(test)));
    }
    
    @Beta
    public static class IfFunction<I,O> implements Function<I,O> {
        protected final Map<Predicate<? super I>,Function<? super I,? extends O>> tests = new LinkedHashMap<Predicate<? super I>,Function<? super I,? extends O>>();
        protected Function<? super I,? extends O> defaultFunction = null;
        
        protected IfFunction(IfFunction<I,O> input) {
            this.tests.putAll(input.tests);
            this.defaultFunction = input.defaultFunction;
        }

        protected IfFunction() {
        }
        
        @Override
        public O apply(I input) {
            for (Map.Entry<Predicate<? super I>,Function<? super I,? extends O>> test: tests.entrySet()) {
                if (test.getKey().apply(input)) 
                    return test.getValue().apply(input);
            }
            return defaultFunction==null ? null : defaultFunction.apply(input);
        }
        
        @Override
        public String toString() {
            return "if["+tests+"]"+(defaultFunction!=null ? "-else["+defaultFunction+"]" : "");
        }
    }
    
    @Beta
    public static class IfFunctionBuilder<I,O> extends IfFunction<I,O> {
        protected IfFunctionBuilder() { super(); }
        protected IfFunctionBuilder(IfFunction<I,O> input) { super(input); }
        
        public IfFunction<I,O> build() {
            return new IfFunction<I,O>(this);
        }
        
        public IfFunctionBuilderApplying<I,O> ifPredicate(Predicate<I> test) {
            return new IfFunctionBuilderApplying<I,O>(this, (Predicate<I>)test);
        }
        public IfFunctionBuilderApplying<I,O> ifEquals(I test) {
            return ifPredicate(Predicates.equalTo(test));
        }
        public IfFunctionBuilderApplying<I,O> ifNotEquals(I test) {
            return ifPredicate(Predicates.not(Predicates.equalTo(test)));
        }

        public IfFunctionBuilder<I,O> defaultValue(O defaultValue) {
            return defaultApply(new Functionals.ConstantFunction<I,O>(defaultValue, defaultValue));
        }
        @SuppressWarnings("unchecked")
        public IfFunctionBuilder<I,O> defaultGet(Supplier<? extends O> defaultSupplier) {
            return defaultApply((Function<I,O>)Functions.forSupplier(defaultSupplier));
        }
        public IfFunctionBuilder<I,O> defaultApply(Function<? super I,? extends O> defaultFunction) {
            IfFunctionBuilder<I, O> result = new IfFunctionBuilder<I,O>(this);
            result.defaultFunction = defaultFunction;
            return result;
        }
    }

    @Beta
    public static class IfFunctionBuilderApplying<I,O> {
        private IfFunction<I, O> input;
        private Predicate<? super I> test;
        
        private IfFunctionBuilderApplying(IfFunction<I,O> input, Predicate<? super I> test) {
            this.input = input;
            this.test = test;
        }
        
        public IfFunctionBuilder<I,O> value(O value) {
            return apply(new Functionals.ConstantFunction<I,O>(value, value));
        }
        @SuppressWarnings("unchecked")
        public IfFunctionBuilder<I,O> get(Supplier<? extends O> supplier) {
            return apply((Function<I,O>)Functions.forSupplier(supplier));
        }
        public IfFunctionBuilder<I,O> apply(Function<? super I,? extends O> function) {
            IfFunctionBuilder<I, O> result = new IfFunctionBuilder<I,O>(input);
            result.tests.put(test, function);
            return result;
        }
    }

    @Beta
    public static class IfFunctionBuilderApplyingFirst<I> {
        private Predicate<? super I> test;
        
        private IfFunctionBuilderApplyingFirst(Predicate<? super I> test) {
            this.test = test;
        }
        
        public <O> IfFunctionBuilder<I,O> value(O value) {
            return apply(new Functionals.ConstantFunction<I,O>(value, value));
        }
        @SuppressWarnings("unchecked")
        public <O> IfFunctionBuilder<I,O> get(Supplier<? extends O> supplier) {
            return apply((Function<I,O>)Functions.forSupplier(supplier));
        }
        public <O> IfFunctionBuilder<I,O> apply(Function<? super I,? extends O> function) {
            IfFunctionBuilder<I, O> result = new IfFunctionBuilder<I,O>();
            result.tests.put(test, function);
            return result;
        }
    }
    
}

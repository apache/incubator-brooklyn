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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

public class WhenFunctions {

    public static <I,O> WhenFunctionBuilder<I,O> newInstance(Class<I> testType, Class<O> returnType) {
        return new WhenFunctionBuilder<I,O>();
    }
    
    public static <I,O> WhenFunctionBuilderWhenFirst<I> when(Predicate<I> test) {
        return new WhenFunctionBuilderWhenFirst<I>(test);
    }
    public static <I,O> WhenFunctionBuilderWhenFirst<I> when(I test) {
        return new WhenFunctionBuilderWhenFirst<I>(test);
    }
    public static <I,O> WhenFunctionBuilder<I,O> when(Predicate<I> test, Supplier<O> supplier) {
        return new WhenFunctionBuilder<I,O>().when(test, supplier);
    }
    public static <I,O> WhenFunctionBuilder<I,O> when(Predicate<I> test, O value) {
        return new WhenFunctionBuilder<I,O>().when(test, value);
    }
    
    public static class WhenFunction<I,O> implements Function<I,O> {
        protected final Map<Predicate<I>,Supplier<O>> tests = new LinkedHashMap<Predicate<I>,Supplier<O>>();
        protected Supplier<O> defaultValue = null;
        
        protected WhenFunction(WhenFunction<I,O> input) {
            this.tests.putAll(input.tests);
            this.defaultValue = input.defaultValue;
        }

        protected WhenFunction() {
        }
        
        @Override
        public O apply(I input) {
            for (Map.Entry<Predicate<I>,Supplier<O>> test: tests.entrySet()) {
                if (test.getKey().apply(input)) 
                    return test.getValue().get();
            }
            return defaultValue==null ? null : defaultValue.get();
        }
        
        @Override
        public String toString() {
            return "if["+tests+"]"+(defaultValue!=null ? "-else["+defaultValue+"]" : "");
        }
    }
    
    public static class WhenFunctionBuilder<I,O> extends WhenFunction<I,O> {
        protected WhenFunctionBuilder() { super(); }
        protected WhenFunctionBuilder(WhenFunction<I,O> input) { super(input); }
        
        public WhenFunction<I,O> build() {
            return new WhenFunction<I,O>(this);
        }
        
        public WhenFunctionBuilder<I,O> when(Predicate<I> test, Supplier<O> supplier) {
            return when(test).value(supplier);
        }

        public WhenFunctionBuilder<I,O> when(Predicate<I> test, O value) {
            return when(test).value(value);
        }

        public WhenFunctionBuilderWhen<I,O> when(Predicate<I> test) {
            return whenUnchecked(test);
        }
        public WhenFunctionBuilderWhen<I,O> when(I test) {
            return whenUnchecked(test);
        }
        @SuppressWarnings("unchecked")
        protected WhenFunctionBuilderWhen<I,O> whenUnchecked(Object test) {
            if (!(test instanceof Predicate)) {
                test = Predicates.equalTo(test);
            }
            return new WhenFunctionBuilderWhen<I,O>(this, (Predicate<I>)test);
        }

        public WhenFunctionBuilder<I,O> defaultValue(O defaultValue) {
            return defaultValueUnchecked(defaultValue);
        }
        public WhenFunctionBuilder<I,O> defaultValue(Supplier<O> defaultValue) {
            return defaultValueUnchecked(defaultValue);
        }
        @SuppressWarnings("unchecked")
        protected WhenFunctionBuilder<I,O> defaultValueUnchecked(Object defaultValue) {
            if (!(defaultValue instanceof Supplier)) {
                defaultValue = Suppliers.ofInstance(defaultValue);
            }
            WhenFunctionBuilder<I, O> result = new WhenFunctionBuilder<I,O>(this);
            result.defaultValue = (Supplier<O>)defaultValue;
            return result;
        }
    }

    public static class WhenFunctionBuilderWhen<I,O> {
        private WhenFunction<I, O> input;
        private Predicate<I> test;
        
        private WhenFunctionBuilderWhen(WhenFunction<I,O> input, Predicate<I> test) {
            this.input = input;
            this.test = test;
        }
        
        public WhenFunctionBuilder<I,O> value(O value) {
            return valueUnchecked(value);
        }
        public WhenFunctionBuilder<I,O> value(Supplier<O> value) {
            return valueUnchecked(value);
        }
        @SuppressWarnings("unchecked")
        protected WhenFunctionBuilder<I,O> valueUnchecked(Object value) {
            if (!(value instanceof Supplier)) {
                value = Suppliers.ofInstance(value);
            }
            WhenFunctionBuilder<I, O> result = new WhenFunctionBuilder<I,O>(input);
            result.tests.put(test, (Supplier<O>) value);
            return result;
        }
    }

    public static class WhenFunctionBuilderWhenFirst<I> {
        private Predicate<I> test;
        
        private WhenFunctionBuilderWhenFirst(Predicate<I> test) {
            whenUnchecked(test);
        }
        
        public WhenFunctionBuilderWhenFirst(I test) {
            whenUnchecked(test);
        }

        @SuppressWarnings("unchecked")
        protected void whenUnchecked(Object test) {
            if (!(test instanceof Predicate)) {
                this.test = Predicates.equalTo((I)test);
            } else {
                this.test = (Predicate<I>) test;
            }
        }
        
        public <O> WhenFunctionBuilder<I,O> value(O value) {
            return valueUnchecked(value);
        }
        public <O> WhenFunctionBuilder<I,O> value(Supplier<O> value) {
            return valueUnchecked(value);
        }
        @SuppressWarnings("unchecked")
        protected <O> WhenFunctionBuilder<I,O> valueUnchecked(Object value) {
            if (!(value instanceof Supplier)) {
                value = Suppliers.ofInstance(value);
            }
            WhenFunctionBuilder<I, O> result = new WhenFunctionBuilder<I,O>();
            result.tests.put(test, (Supplier<O>) value);
            return result;
        }
    }
    
}

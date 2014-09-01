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

import java.util.concurrent.Callable;

import brooklyn.util.guava.IfFunctions.IfFunctionBuilderApplyingFirst;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;

public class Functionals {

    /** applies f1 to the input, then the result of that is passed to f2 (note opposite semantics to {@link Functions#compose(Function, Function)} */ 
    public static <A,B,C> Function<A,C> chain(final Function<A,? extends B> f1, final Function<B,C> f2) {
        return Functions.compose(f2, f1);
    }
    
    /** applies f1 to the input, then f2 to that result, then f3 to that result */
    public static <A,B,C,D> Function<A,D> chain(final Function<A,? extends B> f1, final Function<B,? extends C> f2, final Function<C,D> f3) {
        return chain(f1, chain(f2, f3));
    }
    
    /** applies f1 to the input, then f2 to that result, then f3 to that result, then f4 to that result */
    public static <A,B,C,D,E> Function<A,E> chain(final Function<A,? extends B> f1, final Function<B,? extends C> f2, final Function<C,? extends D> f3, final Function<D,E> f4) {
        return chain(f1, chain(f2, chain(f3, f4)));
    }

    /** @see IfFunctions */
    public static <I> IfFunctionBuilderApplyingFirst<I> ifEquals(I test) {
        return IfFunctions.ifEquals(test);
    }

    /** @see IfFunctions */
    public static <I> IfFunctionBuilderApplyingFirst<I> ifNotEquals(I test) {
        return IfFunctions.ifNotEquals(test);
    }
    
    /** @see IfFunctions */
    public static <I> IfFunctionBuilderApplyingFirst<I> ifPredicate(Predicate<I> test) {
        return IfFunctions.ifPredicate(test);
    }

    /** like guava equivalent but parametrises the input generic type, and allows tostring to be customised */
    public static final class ConstantFunction<I, O> implements Function<I, O> {
        private final O constant;
        private Object toStringDescription;

        public ConstantFunction(O constant) {
            this(constant, null);
        }
        public ConstantFunction(O constant, Object toStringDescription) {
            this.constant = constant;
            this.toStringDescription = toStringDescription;
        }

        @Override
        public O apply(I input) {
            return constant;
        }
        
        @Override
        public String toString() {
            return toStringDescription==null ? "constant("+constant+")" : toStringDescription.toString();
        }
    }

    /** like guava {@link Functions#forSupplier(Supplier)} but parametrises the input generic type */
    public static <I,O> Function<I,O> function(final Supplier<O> supplier) {
        class SupplierAsFunction implements Function<I,O> {
            @Override public O apply(I input) {
                return supplier.get();
            }
        }
        return new SupplierAsFunction();
    }

    public static <I> Function<I,Void> function(final Runnable runnable) {
        class RunnableAsFunction implements Function<I,Void> {
            @Override public Void apply(I input) {
                runnable.run();
                return null;
            }
        }
        return new RunnableAsFunction();
    }

    public static Runnable runnable(final Supplier<?> supplier) {
        class SupplierAsRunnable implements Runnable {
            @Override
            public void run() {
                supplier.get();
            }
        }
        return new SupplierAsRunnable();
    }

    public static <T> Callable<T> callable(final Supplier<T> supplier) {
        class SupplierAsCallable implements Callable<T> {
            @Override
            public T call() {
                return supplier.get();
            }
        }
        return new SupplierAsCallable();
    }

}

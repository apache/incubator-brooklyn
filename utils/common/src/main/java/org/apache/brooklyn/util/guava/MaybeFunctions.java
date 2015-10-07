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
package org.apache.brooklyn.util.guava;

import com.google.common.base.Function;

public class MaybeFunctions {

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Function<T, Maybe<T>> wrapOld() {
        // TODO PERSISTENCE WORKAROUND
        return new Function<T, Maybe<T>>() {
            @Override
            public Maybe<T> apply(T input) {
                return Maybe.fromNullable(input);
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Function<Maybe<T>, T> getOld() {
        // TODO PERSISTENCE WORKAROUND
        return new Function<Maybe<T>, T>() {
            @Override
            public T apply(Maybe<T> input) {
                return input.get();
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Function<Maybe<T>, T> orOld(final T value) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<Maybe<T>, T>() {
            @Override
            public T apply(Maybe<T> input) {
                return input.or(value);
            }
        };
    }

    public static <T> Function<T, Maybe<T>> wrap() {
        return new WrapMaybe<T>();
    }

    protected static class WrapMaybe<T> implements Function<T, Maybe<T>> {
        @Override
        public Maybe<T> apply(T input) {
            return Maybe.fromNullable(input);
        }
    };

    public static <T> Function<Maybe<T>, T> get() {
        return new GetMaybe<T>();
    }
    
    protected static class GetMaybe<T> implements Function<Maybe<T>, T> {
        @Override
        public T apply(Maybe<T> input) {
            return input.get();
        }
    }

    public static <T> Function<Maybe<T>, T> or(final T value) {
        return new OrMaybe<T>(value);
    }
    
    protected static class OrMaybe<T> implements Function<Maybe<T>, T> {
        private final T value;

        public OrMaybe(T value) {
            this.value = value;
        }
        @Override
        public T apply(Maybe<T> input) {
            return input.or(value);
        }
    }
}

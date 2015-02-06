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
package brooklyn.util.math;

import javax.annotation.Nullable;

import com.google.common.base.Predicate;

public class MathPredicates {

    /**
     * Creates a predicate comparing a given number with {@code val}. 
     * A number of {@code null} passed to the predicate will always return false.
     */
    public static <T extends Number> Predicate<T> greaterThan(final double val) {
        return new Predicate<T>() {
            public boolean apply(@Nullable T input) {
                return (input == null) ? false : input.doubleValue() > val;
            }
        };
    }

    /**
     * Creates a predicate comparing a given number with {@code val}. 
     * A number of {@code null} passed to the predicate will always return false.
     */
    public static <T extends Number> Predicate<T> greaterThanOrEqual(final double val) {
        return new Predicate<T>() {
            public boolean apply(@Nullable T input) {
                return (input == null) ? false : input.doubleValue() >= val;
            }
        };
    }

    /**
     * Creates a predicate comparing a given number with {@code val}. 
     * A number of {@code null} passed to the predicate will always return false.
     */
    public static <T extends Number> Predicate<T> lessThan(final double val) {
        return new Predicate<T>() {
            public boolean apply(@Nullable T input) {
                return (input == null) ? false : input.doubleValue() < val;
            }
        };
    }

    /**
     * Creates a predicate comparing a given number with {@code val}. 
     * A number of {@code null} passed to the predicate will always return false.
     */
    public static <T extends Number> Predicate<T> lessThanOrEqual(final double val) {
        return new Predicate<T>() {
            public boolean apply(@Nullable T input) {
                return (input == null) ? false : input.doubleValue() <= val;
            }
        };
    }
}

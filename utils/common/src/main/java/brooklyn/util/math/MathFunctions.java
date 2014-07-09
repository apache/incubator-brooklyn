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

import brooklyn.util.text.Strings;

import com.google.common.base.Function;

public class MathFunctions {

    public static Function<Number, Integer> plus(final int addend) {
        return new Function<Number, Integer>() {
            public Integer apply(@Nullable Number input) {
                if (input==null) return null;
                return input.intValue() + addend;
            }
        };
    }

    public static Function<Number, Long> plus(final long addend) {
        return new Function<Number, Long>() {
            public Long apply(@Nullable Number input) {
                if (input==null) return null;
                return input.longValue() + addend;
            }
        };
    }

    public static Function<Number, Double> plus(final double addend) {
        return new Function<Number, Double>() {
            public Double apply(@Nullable Number input) {
                if (input==null) return null;
                return input.doubleValue() + addend;
            }
        };
    }

    public static Function<Number, Integer> times(final int multiplicand) {
        return new Function<Number, Integer>() {
            public Integer apply(@Nullable Number input) {
                if (input==null) return null;
                return input.intValue() * multiplicand;
            }
        };
    }

    public static Function<Number, Long> times(final long multiplicand) {
        return new Function<Number, Long>() {
            public Long apply(@Nullable Number input) {
                if (input==null) return null;
                return input.longValue() * multiplicand;
            }
        };
    }

    public static Function<Number, Double> times(final double multiplicand) {
        return new Function<Number, Double>() {
            public Double apply(@Nullable Number input) {
                if (input==null) return null;
                return input.doubleValue() * multiplicand;
            }
        };
    }

    public static Function<Number, Double> divide(final double divisor) {
        return new Function<Number, Double>() {
            public Double apply(@Nullable Number input) {
                if (input==null) return null;
                return input.doubleValue() / divisor;
            }
        };
    }

    public static <T> Function<T, Double> divide(final Function<T, ? extends Number> input, final double divisor) {
        return new Function<T, Double>() {
            public Double apply(@Nullable T input2) {
                if (input==null) return null;
                Number n = input.apply(input2);
                if (n==null) return null;
                return n.doubleValue() / divisor;
            }
        };
    }

    /** returns a string of up to maxLen length (longer in extreme cases) also capped at significantDigits significantDigits */
    public static Function<Number, String> readableString(final int significantDigits, final int maxLen) {
        return new Function<Number, String>() {
            public String apply(@Nullable Number input) {
                if (input==null) return null;
                return Strings.makeRealString(input.doubleValue(), maxLen, significantDigits, 0);
            }
        };
    }

    /** returns a string where the input number is expressed as percent, with given number of significant digits */
    public static Function<Number, String> percent(final int significantDigits) {
        return new Function<Number, String>() {
            public String apply(@Nullable Number input) {
                if (input==null) return null;
                return readableString(significantDigits, significantDigits+3).apply(input.doubleValue() * 100d)+"%";
            }
        };
    }

}

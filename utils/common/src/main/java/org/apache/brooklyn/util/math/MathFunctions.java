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
package org.apache.brooklyn.util.math;

import javax.annotation.Nullable;

import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Function;

public class MathFunctions {

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<Number, Integer> plusOld(final int addend) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<Number, Integer>() {
            public Integer apply(@Nullable Number input) {
                if (input==null) return null;
                return input.intValue() + addend;
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<Number, Long> plusOld(final long addend) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<Number, Long>() {
            public Long apply(@Nullable Number input) {
                if (input==null) return null;
                return input.longValue() + addend;
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<Number, Double> plusOld(final double addend) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<Number, Double>() {
            public Double apply(@Nullable Number input) {
                if (input==null) return null;
                return input.doubleValue() + addend;
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<Number, Integer> timesOld(final int multiplicand) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<Number, Integer>() {
            public Integer apply(@Nullable Number input) {
                if (input==null) return null;
                return input.intValue() * multiplicand;
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<Number, Long> timesOld(final long multiplicand) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<Number, Long>() {
            public Long apply(@Nullable Number input) {
                if (input==null) return null;
                return input.longValue() * multiplicand;
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<Number, Double> timesOld(final double multiplicand) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<Number, Double>() {
            public Double apply(@Nullable Number input) {
                if (input==null) return null;
                return input.doubleValue() * multiplicand;
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<Number, Double> divideOld(final double divisor) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<Number, Double>() {
            public Double apply(@Nullable Number input) {
                if (input==null) return null;
                return input.doubleValue() / divisor;
            }
        };
    }

    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static <T> Function<T, Double> divideOld(final Function<T, ? extends Number> input, final double divisor) {
        // TODO PERSISTENCE WORKAROUND
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
    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<Number, String> readableStringOld(final int significantDigits, final int maxLen) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<Number, String>() {
            public String apply(@Nullable Number input) {
                if (input==null) return null;
                return Strings.makeRealString(input.doubleValue(), maxLen, significantDigits, 0);
            }
        };
    }

    /** returns a string where the input number is expressed as percent, with given number of significant digits */
    /** @deprecated since 0.9.0 kept only to allow conversion of anonymous inner classes */
    @SuppressWarnings("unused") @Deprecated 
    private static Function<Number, String> percentOld(final int significantDigits) {
        // TODO PERSISTENCE WORKAROUND
        return new Function<Number, String>() {
            public String apply(@Nullable Number input) {
                if (input==null) return null;
                return readableString(significantDigits, significantDigits+3).apply(input.doubleValue() * 100d)+"%";
            }
        };
    }

    public static Function<Number, Integer> plus(int addend) {
        return new PlusInt(addend);
    }

    protected static class PlusInt implements Function<Number, Integer> {
        private final int addend;

        public PlusInt(int addend) {
            this.addend = addend;
        }
        public Integer apply(@Nullable Number input) {
            if (input==null) return null;
            return input.intValue() + addend;
        }
    }

    public static Function<Number, Long> plus(long addend) {
        return new PlusLong(addend);
    }

    protected static class PlusLong implements Function<Number, Long> {
        private final long addend;

        public PlusLong(long addend) {
            this.addend = addend;
        }
        public Long apply(@Nullable Number input) {
            if (input==null) return null;
            return input.longValue() + addend;
        }
    }

    public static Function<Number, Double> plus(final double addend) {
        return new PlusDouble(addend);
    }

    protected static class PlusDouble implements Function<Number, Double> {
        private final double addend;

        public PlusDouble(double addend) {
            this.addend = addend;
        }
        public Double apply(@Nullable Number input) {
            if (input==null) return null;
            return input.doubleValue() + addend;
        }
    }

    public static Function<Number, Integer> times(final int multiplicand) {
        return new TimesInt(multiplicand);
    }

    protected static class TimesInt implements Function<Number, Integer> {
        private final int multiplicand;

        public TimesInt(int multiplicand) {
            this.multiplicand = multiplicand;
        }
        public Integer apply(@Nullable Number input) {
            if (input==null) return null;
            return input.intValue() * multiplicand;
        }
    }

    public static Function<Number, Long> times(long multiplicand) {
        return new TimesLong(multiplicand);
    }

    protected static class TimesLong implements Function<Number, Long> {
        private final long multiplicand;

        public TimesLong(long multiplicand) {
            this.multiplicand = multiplicand;
        }
        public Long apply(@Nullable Number input) {
            if (input==null) return null;
            return input.longValue() * multiplicand;
        }
    }

    public static Function<Number, Double> times(final double multiplicand) {
        return new TimesDouble(multiplicand);
    }

    protected static class TimesDouble implements Function<Number, Double> {
        private final double multiplicand;

        public TimesDouble(double multiplicand) {
            this.multiplicand = multiplicand;
        }
        public Double apply(@Nullable Number input) {
            if (input==null) return null;
            return input.doubleValue() * multiplicand;
        }
    }

    public static Function<Number, Double> divide(double divisor) {
        return new DivideDouble(divisor);
    }

    protected static class DivideDouble implements Function<Number, Double> {
        private final double divisor;

        public DivideDouble(double divisor) {
            this.divisor = divisor;
        }
        public Double apply(@Nullable Number input) {
            if (input==null) return null;
            return input.doubleValue() / divisor;
        }
    }

    /**
     * @deprecated since 0.9.0; use {@link Functionals#chain(Function, Function)} and {@link MathFunctions#divide(double)}
     */
    public static <T> Function<T, Double> divide(final Function<T, ? extends Number> preprocessor, final double divisor) {
        return Functionals.chain(preprocessor, divide(divisor));
    }

    /** returns a string of up to maxLen length (longer in extreme cases) also capped at significantDigits significantDigits */
    public static Function<Number, String> readableString(int significantDigits, int maxLen) {
        return new ReadableString(significantDigits, maxLen);
    }

    protected static class ReadableString implements Function<Number, String> {
        private final int significantDigits;
        private final int maxLen;
        public ReadableString(int significantDigits, int maxLen) {
            this.significantDigits = significantDigits;
            this.maxLen = maxLen;
        }
        public String apply(@Nullable Number input) {
            if (input==null) return null;
            return Strings.makeRealString(input.doubleValue(), maxLen, significantDigits, 0);
        }
    };

    /** returns a string where the input number is expressed as percent, with given number of significant digits */
    public static Function<Number, String> percent(int significantDigits) {
        return new Percent(significantDigits);
    }

    private static class Percent implements Function<Number, String> {
        final int significantDigits;
        public Percent(int significantDigits) {
            this.significantDigits = significantDigits;
        }

        public String apply(@Nullable Number input) {
            if (input==null) return null;
            return readableString(significantDigits, significantDigits+3).apply(input.doubleValue() * 100d)+"%";
        }
    }
}

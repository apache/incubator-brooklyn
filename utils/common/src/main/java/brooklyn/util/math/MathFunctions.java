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

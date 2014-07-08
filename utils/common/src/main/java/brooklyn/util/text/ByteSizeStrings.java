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
package brooklyn.util.text;

import java.util.Formattable;
import java.util.FormattableFlags;
import java.util.Formatter;

import javax.annotation.Nullable;

import com.google.common.base.Function;

/**
 * A formatter to pretty-print numeric values representing sizes in byes.
 * <p>
 * The {@link ByteSizeStrings#builder()} presents a fluent interface to create
 * various configurations of formatting. The defaults produce metric units in
 * multiples of 1000 bytes at a precision of three significant figures. This is
 * the way disk space is normally measured, for example {@literal 128.1GB}.
 * <p>
 * Alternatively the {@link ByteSizeStrings#iso()} convenience method produces
 * ISO standard units in multiples of 1024 bytes, with the same precision as the
 * metric output. This is how RAM is normally measured, for example {@literal 12.4MiB}
 * or {@literal 1.04GiB}.
 * <p>
 * Finally, the {@link ByteSizeStrings#java()} convenience method will produce
 * strings suitable for use with a Java command line, as part of the {@code -Xms}
 * or {@code -Xmx} options. These output integer values only, so values up to
 * 10GB will be reported in MB to preserve accuracy. For size values over 1000GB,
 * the output will still be formatted as GB but rounded to a mutiple of 1000.
 * <p>
 * The class is immutable and thread safe once built and a single instance of
 * the three pre-defined configurations is created and returned buy the methods
 * described above.
 *
 * @see Strings#makeSizeString(long)
 * @see Strings#makeISOSizeString(long)
 * @see Strings#makeJavaSizeString(long)
 */
public class ByteSizeStrings implements Function<Long, String> {

    /**
     * Configures and builds a {@link ByteSizeStrings} formatter.
     */
    public static class Builder {

        private String suffixBytes = "B";
        private String suffixKilo = "kB";
        private String suffixMega = "MB";
        private String suffixGiga = "GB";
        private String suffixTera = "TB";
        private boolean addSpace = true;
        private int bytesPerMetricUnit = 1000;
        private int maxLen = 4;
        private int precision = 3;
        private int lowerLimit = 1;

        /**
         * The suffix to use when printing bytes.
         */
        public Builder suffixBytes(String suffixBytes) { this.suffixBytes = suffixBytes; return this; }

        /**
         * The suffix to use when printing Kilobytes.
         */
        public Builder suffixKilo(String suffixKilo) { this.suffixKilo = suffixKilo; return this; }

        /**
         * The suffix to use when printing Megabytes.
         */
        public Builder suffixMega(String suffixMega) { this.suffixMega = suffixMega; return this; }

        /**
         * The suffix to use when printing Gigabytes.
         */
        public Builder suffixGiga(String suffixGiga) { this.suffixGiga = suffixGiga; return this; }

        /**
         * The suffix to use when printing Terabytes.
         */
        public Builder suffixTera(String suffixTera) { this.suffixTera = suffixTera; return this; }

        /**
         * Whether to add a space between the value and the unit suffix.
         * <p>
         * Defaults is {@literal true} for '5 MiB' output.
         */
        public Builder addSpace(boolean addSpace) { this.addSpace = addSpace; return this; }
        public Builder addSpace() { this.addSpace = true; return this; }
        public Builder noSpace() { this.addSpace = false; return this; }

        /**
         * The number of bytes per metric usnit, usually either 1000 or 1024.
         * <p>
         * Used to determine when to use the next suffix string.
         */
        public Builder bytesPerMetricUnit(int bytesPerMetricUnit) { this.bytesPerMetricUnit = bytesPerMetricUnit; return this; }

        /**
         * The maximum length of the printed number.
         *
         * @see Strings#makeRealString(double, int, int, int, double, boolean)
         */
        public Builder maxLen(int maxLen) { this.maxLen = maxLen; return this; }

        /**
         * The number of digits accuracy desired in the printed number.
         *
         * @see Strings#makeRealString(double, int, int, int, double, boolean)
         */
        public Builder precision(int precision) { this.precision = precision; return this; }

        /**
         * Prints using a lower suffix until the size is greater than this limit multiplied
         * by bytes per metric unit, when the next highest suffix will be used.§
         * <p>
         * If this has the value 5 then sizes up to 5000 will be printed as bytes, and over 5000
         * as Kilobytes.
         */
        public Builder lowerLimit(int lowerLimit) { this.lowerLimit = lowerLimit; return this; }

        /**
         * Returns an immutable {@link ByteSizeStrings} formatter using the builder configuration.
         */
        public ByteSizeStrings build() {
            String space = addSpace ? " " : "";
            return new ByteSizeStrings(space + suffixBytes, space + suffixKilo, space + suffixMega, space + suffixGiga,
                    space + suffixTera, bytesPerMetricUnit, maxLen, precision, lowerLimit);
        }

    }

    /**
     * Returns a builder for a {@link ByteSizeStrings} formatter.
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Format byte sizes suitable for Java {@code -Xms} arguments.
     */
    public static final ByteSizeStrings java() { return JAVA; }

    private static final ByteSizeStrings JAVA = ByteSizeStrings.builder()
                .suffixBytes("")
                .suffixKilo("k")
                .suffixMega("m")
                .suffixGiga("g")
                .suffixTera("000g") // Java has no Tera suffix
                .noSpace()
                .bytesPerMetricUnit(1024)
                .maxLen(6)
                .precision(0)
                .lowerLimit(10)
                .build();

    /**
     * Formats byte sizes using ISO standard suffixes and binary multiples of 1024
     */
    public static ByteSizeStrings iso() { return ISO; }

    private static ByteSizeStrings ISO = ByteSizeStrings.builder()
                .suffixBytes("B")
                .suffixKilo("KiB")
                .suffixMega("MiB")
                .suffixGiga("GiB")
                .suffixTera("TiB")
                .bytesPerMetricUnit(1024)
                .build();

    /**
     * Default byte size formatter using metric multiples of 1000.
     */
    public static ByteSizeStrings metric() { return METRIC; }

    private static ByteSizeStrings METRIC = ByteSizeStrings.builder().build();

    private String suffixBytes;
    private String suffixKilo;
    private String suffixMega;
    private String suffixGiga;
    private String suffixTera;
    private int bytesPerMetricUnit;
    private int maxLen;
    private int precision;
    private int lowerLimit;

    /**
     * For use by the {@link Builder} only.
     */
    private ByteSizeStrings(String suffixBytes, String suffixKilo, String suffixMega, String suffixGiga,
            String suffixTera, int bytesPerMetricUnit, int maxLen, int precision, int lowerLimit) {
        this.suffixBytes = suffixBytes;
        this.suffixKilo = suffixKilo;
        this.suffixMega = suffixMega;
        this.suffixGiga = suffixGiga;
        this.suffixTera = suffixTera;
        this.bytesPerMetricUnit = bytesPerMetricUnit;
        this.maxLen = maxLen;
        this.precision = precision;
        this.lowerLimit = lowerLimit;
    }

    /** @deprecated Use {@link ByteSizeStrings#builder()} */
    @Deprecated
    public ByteSizeStrings() { }

    /** @deprecated Use {@link ByteSizeStrings.Builder#suffixBytes(String)} */
    @Deprecated
    public void setSuffixBytes(String suffixBytes) {
        this.suffixBytes = suffixBytes;
    }

    /** @deprecated Use {@link ByteSizeStrings.Builder#suffixKilo(String)} */
    @Deprecated
    public void setSuffixKilo(String suffixKilo) {
        this.suffixKilo = suffixKilo;
    }

    /** @deprecated Use {@link ByteSizeStrings.Builder#suffixMega(String)} */
    @Deprecated
    public void setSuffixMega(String suffixMega) {
        this.suffixMega = suffixMega;
    }

    /** @deprecated Use {@link ByteSizeStrings.Builder#suffixGiga(String)} */
    @Deprecated
    public void setSuffixGiga(String suffixGiga) {
        this.suffixGiga = suffixGiga;
    }

    /** @deprecated Use {@link ByteSizeStrings.Builder#suffixTera(String)} */
    @Deprecated
    public void setSuffixTera(String suffixTera) {
        this.suffixTera = suffixTera;
    }

    /** @deprecated Use {@link ByteSizeStrings.Builder#bytesPerMetricUnit(int)} */
    @Deprecated
    public void setBytesPerMetricUnit(int bytesPerMetricUnit) {
        this.bytesPerMetricUnit = bytesPerMetricUnit;
    }

    /** @deprecated Use {@link ByteSizeStrings.Builder#maxLen(int)} */
    @Deprecated
    public void setMaxLen(int maxLen) {
        this.maxLen = maxLen;
    }

    /** @deprecated Use {@link ByteSizeStrings.Builder#precision(int)} */
    @Deprecated
    public void setPrecision(int precision) {
        this.precision = precision;
    }

    /** @deprecated Use {@link ByteSizeStrings.Builder#lowerLimit(int)} */
    @Deprecated
    public void setLowerLimit(int lowerLimit) {
        this.lowerLimit = lowerLimit;
    }

    /**
     * Format the {@literal size} bytes as a String.
     */
    public String makeSizeString(long size) {
        return makeSizeString(size, precision);
    }

    /**
     * Format the {@literal size} bytes as a String with the given precision.
     */
    public String makeSizeString(long size, int precision) {
        long t = size;
        if (t==0) return "0"+suffixBytes;
        if (t<0) return "-"+makeSizeString(-t);
        long b = t%bytesPerMetricUnit;
        t = t/bytesPerMetricUnit;
        long kb = t%bytesPerMetricUnit;
        t = t/bytesPerMetricUnit;
        long mb = t%bytesPerMetricUnit;
        t = t/bytesPerMetricUnit;
        long gb = t%bytesPerMetricUnit;
        t = t/bytesPerMetricUnit;
        long tb = t;

        if (tb>lowerLimit)
            return Strings.makeRealString(tb + (1.0*gb/bytesPerMetricUnit), -1, precision, 0) + suffixTera;
        if (gb>lowerLimit)
            return Strings.makeRealString((tb*bytesPerMetricUnit) + gb + (1.0*mb/bytesPerMetricUnit), maxLen, precision, 0) + suffixGiga;
        if (mb>lowerLimit)
            return Strings.makeRealString((gb*bytesPerMetricUnit) + mb + (1.0*kb/bytesPerMetricUnit), maxLen, precision, 0) + suffixMega;
        if (kb>lowerLimit)
            return Strings.makeRealString((mb*bytesPerMetricUnit) + kb + (1.0*b/bytesPerMetricUnit), maxLen, precision, 0) + suffixKilo;
        return (kb*bytesPerMetricUnit) + b + suffixBytes;
    }

    /**
     * Returns a {@link Formattable} object that can be used with {@link String#format(String, Object...)}.
     * <p>
     * When used as the argument for a {@literal %s} format string element, the {@literal bytes} value
     * will be formatted using the current {@link ByteSizeStrings} values, or if the alternative
     * flag is set (using the {@literal %#s} format string) it will use the {@link ByteSizeStrings#metric()}
     * formatter. Finally, the precision of the formatted value can be adjusted using format string
     * argumenbts like {@literal %.6s}.
     *
     * @see http://docs.oracle.com/javase/7/docs/api/java/util/Formatter.html#syntax
     */
    public Formattable formatted(final long bytes) {
        return new Formattable() {
            @Override
            public void formatTo(Formatter formatter, int flags, int width, int precision) {
                boolean alternate = (flags & FormattableFlags.ALTERNATE) == FormattableFlags.ALTERNATE;
                ByteSizeStrings strings = alternate ? ByteSizeStrings.metric() : ByteSizeStrings.this;
                if (precision != -1) {
                    formatter.format("%s", strings.makeSizeString(bytes, precision));
                } else {
                    formatter.format("%s", strings.makeSizeString(bytes));
                }
            }
        };
    }

    /**
     * A {@link Function} implementation that formats its input using the current {@link ByteSizeStrings} values.
     */
    @Override
    @Nullable
    public String apply(@Nullable Long input) {
        if (input == null) return null;
        return makeSizeString(input);
    }

}

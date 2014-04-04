/*
 * Copyright 2009-2014 by Cloudsoft Corporation Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.util.text;

import static org.testng.Assert.assertEquals;

import java.util.Arrays;

import org.testng.annotations.Test;

import com.google.common.collect.Iterables;

/**
 * Test the different {@link ByteSizeStrings} formatting options using a list of byte sizes.
 */
@Test
public class ByteSizeStringsTest {

    public void testSizeString() {
        assertEquals(Strings.makeSizeString(-1), "-1 B");
        assertEquals(Strings.makeSizeString(0), "0 B");
        assertEquals(Strings.makeSizeString(999), "999 B");
        assertEquals(Strings.makeSizeString(1024), "1024 B");
        assertEquals(Strings.makeSizeString(1234), "1234 B");
        assertEquals(Strings.makeSizeString(2345), "2.34 kB");
        assertEquals(Strings.makeSizeString(4096), "4.10 kB");
        assertEquals(Strings.makeSizeString(4567), "4.57 kB");
        assertEquals(Strings.makeSizeString(65535), "65.5 kB");
        assertEquals(Strings.makeSizeString(23456789L), "23.5 MB");
        assertEquals(Strings.makeSizeString(23456789012L), "23.5 GB");
        assertEquals(Strings.makeSizeString(23456789012345L), "23.5 TB");
        assertEquals(Strings.makeSizeString(Long.MAX_VALUE), "9223372 TB");
    }

    public void testJavaSizeString() {
        assertEquals(ByteSizeStrings.java().makeSizeString(-1), "-1");
        assertEquals(ByteSizeStrings.java().makeSizeString(0), "0");
        assertEquals(ByteSizeStrings.java().makeSizeString(999), "999");
        assertEquals(ByteSizeStrings.java().makeSizeString(1024), "1024");
        assertEquals(ByteSizeStrings.java().makeSizeString(1234), "1234");
        assertEquals(ByteSizeStrings.java().makeSizeString(2345), "2345");
        assertEquals(ByteSizeStrings.java().makeSizeString(4096), "4096");
        assertEquals(ByteSizeStrings.java().makeSizeString(4567), "4567");
        assertEquals(ByteSizeStrings.java().makeSizeString(6789), "6789");
        assertEquals(ByteSizeStrings.java().makeSizeString(65535), "64k");
        assertEquals(ByteSizeStrings.java().makeSizeString(23456789L), "22m");
        assertEquals(ByteSizeStrings.java().makeSizeString(23456789012L), "22g");
        assertEquals(ByteSizeStrings.java().makeSizeString(23456789012345L), "21000g");
        assertEquals(ByteSizeStrings.java().makeSizeString(Long.MAX_VALUE), "8388608000g");
    }

    public void testISOSizeString() {
        assertEquals(ByteSizeStrings.iso().makeSizeString(-1), "-1 B");
        assertEquals(ByteSizeStrings.iso().makeSizeString(0), "0 B");
        assertEquals(ByteSizeStrings.iso().makeSizeString(999), "999 B");
        assertEquals(ByteSizeStrings.iso().makeSizeString(1024), "1024 B");
        assertEquals(ByteSizeStrings.iso().makeSizeString(1234), "1234 B");
        assertEquals(ByteSizeStrings.iso().makeSizeString(2345), "2.29 KiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(4096), "4 KiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(4567), "4.46 KiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(6789), "6.63 KiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(65535), "64.0 KiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(23456789L), "22.4 MiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(23456789012L), "21.8 GiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(23456789012345L), "21.3 TiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(Long.MAX_VALUE), "8388608 TiB");
    }

    public void testBuilder() {
        ByteSizeStrings strings = ByteSizeStrings.builder()
                .bytesPerMetricUnit(1024)
                .precision(4)
                .lowerLimit(5)
                .maxLen(4)
                .suffixBytes("b")
                .suffixKilo("kb")
                .suffixMega("Mb")
                .suffixGiga("Gb")
                .suffixTera("Tb")
                .addSpace()
                .build();

        assertEquals(strings.makeSizeString(-1), "-1 b");
        assertEquals(strings.makeSizeString(0), "0 b");
        assertEquals(strings.makeSizeString(999), "999 b");
        assertEquals(strings.makeSizeString(1024), "1024 b");
        assertEquals(strings.makeSizeString(1234), "1234 b");
        assertEquals(strings.makeSizeString(2345), "2345 b");
        assertEquals(strings.makeSizeString(4096), "4096 b");
        assertEquals(strings.makeSizeString(4567), "4567 b");
        assertEquals(strings.makeSizeString(6789), "6.630 kb");
        assertEquals(strings.makeSizeString(65535), "64.00 kb");
        assertEquals(strings.makeSizeString(23456789L), "22.37 Mb");
        assertEquals(strings.makeSizeString(23456789012L), "21.85 Gb");
        assertEquals(strings.makeSizeString(23456789012345L), "21.33 Tb");
        assertEquals(strings.makeSizeString(Long.MAX_VALUE), "8388608 Tb");
    }

    public void testFormatter() {
        ByteSizeStrings iso = ByteSizeStrings.iso();
        assertEquals(String.format("%s", iso.formatted(23456789L)), "22.4 MiB");
        assertEquals(String.format("%.6s", iso.formatted(23456789L)), "22.3701 MiB");
        assertEquals(String.format("%#s", iso.formatted(23456789L)), "23.5 MB");
    }

    public void testFunction() {
        ByteSizeStrings iso = ByteSizeStrings.iso();
        Iterable<String> bytes = Iterables.transform(Arrays.asList(23456789L, 23456789012L, 23456789012345L), iso);
        assertEquals(Iterables.get(bytes, 0), "22.4 MiB");
        assertEquals(Iterables.get(bytes, 1), "21.8 GiB");
        assertEquals(Iterables.get(bytes, 2), "21.3 TiB");
    }
}

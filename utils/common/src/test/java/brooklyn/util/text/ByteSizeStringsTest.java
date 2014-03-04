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
        assertEquals(Strings.makeSizeString(-1), "-1B");
        assertEquals(Strings.makeSizeString(0), "0B");
        assertEquals(Strings.makeSizeString(999), "999B");
        assertEquals(Strings.makeSizeString(1024), "1024B");
        assertEquals(Strings.makeSizeString(1234), "1234B");
        assertEquals(Strings.makeSizeString(2345), "2.34kB");
        assertEquals(Strings.makeSizeString(4096), "4.10kB");
        assertEquals(Strings.makeSizeString(4567), "4.57kB");
        assertEquals(Strings.makeSizeString(65535), "65.5kB");
        assertEquals(Strings.makeSizeString(23456789L), "23.5MB");
        assertEquals(Strings.makeSizeString(23456789012L), "23.5GB");
        assertEquals(Strings.makeSizeString(23456789012345L), "23.5TB");
        assertEquals(Strings.makeSizeString(Long.MAX_VALUE), "9223372TB");
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
        assertEquals(ByteSizeStrings.iso().makeSizeString(-1), "-1B");
        assertEquals(ByteSizeStrings.iso().makeSizeString(0), "0B");
        assertEquals(ByteSizeStrings.iso().makeSizeString(999), "999B");
        assertEquals(ByteSizeStrings.iso().makeSizeString(1024), "1024B");
        assertEquals(ByteSizeStrings.iso().makeSizeString(1234), "1234B");
        assertEquals(ByteSizeStrings.iso().makeSizeString(2345), "2.29KiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(4096), "4KiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(4567), "4.46KiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(6789), "6.63KiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(65535), "64.0KiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(23456789L), "22.4MiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(23456789012L), "21.8GiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(23456789012345L), "21.3TiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(Long.MAX_VALUE), "8388608TiB");
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
                .build();

        assertEquals(strings.makeSizeString(-1), "-1b");
        assertEquals(strings.makeSizeString(0), "0b");
        assertEquals(strings.makeSizeString(999), "999b");
        assertEquals(strings.makeSizeString(1024), "1024b");
        assertEquals(strings.makeSizeString(1234), "1234b");
        assertEquals(strings.makeSizeString(2345), "2345b");
        assertEquals(strings.makeSizeString(4096), "4096b");
        assertEquals(strings.makeSizeString(4567), "4567b");
        assertEquals(strings.makeSizeString(6789), "6.630kb");
        assertEquals(strings.makeSizeString(65535), "64.00kb");
        assertEquals(strings.makeSizeString(23456789L), "22.37Mb");
        assertEquals(strings.makeSizeString(23456789012L), "21.85Gb");
        assertEquals(strings.makeSizeString(23456789012345L), "21.33Tb");
        assertEquals(strings.makeSizeString(Long.MAX_VALUE), "8388608Tb");
    }

    public void testFormatter() {
        ByteSizeStrings iso = ByteSizeStrings.iso();
        assertEquals(String.format("%s", iso.formatted(23456789L)), "22.4MiB");
        assertEquals(String.format("%.6s", iso.formatted(23456789L)), "22.3701MiB");
        assertEquals(String.format("%#s", iso.formatted(23456789L)), "23.5MB");
    }

    public void testFunction() {
        ByteSizeStrings iso = ByteSizeStrings.iso();
        Iterable<String> bytes = Iterables.transform(Arrays.asList(23456789L, 23456789012L, 23456789012345L), iso);
        assertEquals(Iterables.get(bytes, 0), "22.4MiB");
        assertEquals(Iterables.get(bytes, 1), "21.8GiB");
        assertEquals(Iterables.get(bytes, 2), "21.3TiB");
    }
}

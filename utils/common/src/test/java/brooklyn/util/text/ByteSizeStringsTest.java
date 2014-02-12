/*
 * Copyright (c) 2009-2013 Cloudsoft Corporation Ltd.
 */
package brooklyn.util.text;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

@Test
public class ByteSizeStringsTest {

    public void testSizeString() {
        assertEquals(Strings.makeSizeString(0), "0B");
        assertEquals(Strings.makeSizeString(999), "999B");
        assertEquals(Strings.makeSizeString(1234), "1234B");
        assertEquals(Strings.makeSizeString(2345), "2345B");
        assertEquals(Strings.makeSizeString(4567), "4.57kB");
        assertEquals(Strings.makeSizeString(65535), "65.5kB");
        assertEquals(Strings.makeSizeString(23456789), "23.5MB");
        assertEquals(Strings.makeSizeString(23456789012L), "23.5GB");
        assertEquals(Strings.makeSizeString(23456789012345L), "2.35E4GB");
    }

    public void testJavaSizeString() {
        assertEquals(ByteSizeStrings.java().makeSizeString(0), "0");
        assertEquals(ByteSizeStrings.java().makeSizeString(999), "999");
        assertEquals(ByteSizeStrings.java().makeSizeString(1234), "1234");
        assertEquals(ByteSizeStrings.java().makeSizeString(2345), "2345");
        assertEquals(ByteSizeStrings.java().makeSizeString(4567), "4k");
        assertEquals(ByteSizeStrings.java().makeSizeString(65535), "64k");
        assertEquals(ByteSizeStrings.java().makeSizeString(23456789), "22m");
        assertEquals(ByteSizeStrings.java().makeSizeString(23456789012L), "22g");
        assertEquals(ByteSizeStrings.java().makeSizeString(23456789012345L), "21846g");
    }

    public void testISOSizeString() {
        assertEquals(ByteSizeStrings.iso().makeSizeString(0), "0B");
        assertEquals(ByteSizeStrings.iso().makeSizeString(999), "999B");
        assertEquals(ByteSizeStrings.iso().makeSizeString(1024), "1024B");
        assertEquals(ByteSizeStrings.iso().makeSizeString(2048), "2048B");
        assertEquals(ByteSizeStrings.iso().makeSizeString(4096), "4KiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(65535), "64.0KiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(23456789), "22.4MiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(23456789012L), "21.8GiB");
        assertEquals(ByteSizeStrings.iso().makeSizeString(23456789012345L), "2.18E4GiB");
    }
}

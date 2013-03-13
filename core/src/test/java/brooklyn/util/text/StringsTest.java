/*
 * Copyright (c) 2009-2013 Cloudsoft Corporation Ltd.
 */
package brooklyn.util.text;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import brooklyn.util.MutableMap;

@Test
public class StringsTest {

    public void isBlankOrEmpty() {
        assertTrue(Strings.isEmpty(null));
        assertTrue(Strings.isEmpty(""));
        assertFalse(Strings.isEmpty("   \t   "));
        assertFalse(Strings.isEmpty("abc"));
        assertFalse(Strings.isEmpty("   abc   "));

        assertFalse(Strings.isNonEmpty(null));
        assertFalse(Strings.isNonEmpty(""));
        assertTrue(Strings.isNonEmpty("   \t   "));
        assertTrue(Strings.isNonEmpty("abc"));
        assertTrue(Strings.isNonEmpty("   abc   "));

        assertTrue(Strings.isBlank(null));
        assertTrue(Strings.isBlank(""));
        assertTrue(Strings.isBlank("   \t   "));
        assertFalse(Strings.isBlank("abc"));
        assertFalse(Strings.isBlank("   abc   "));

        assertFalse(Strings.isNonBlank(null));
        assertFalse(Strings.isNonBlank(""));
        assertFalse(Strings.isNonBlank("   \t   "));
        assertTrue(Strings.isNonBlank("abc"));
        assertTrue(Strings.isNonBlank("   abc   "));
    }

    public void testMakeValidFilename() {
        assertEquals("abcdef", Strings.makeValidFilename("abcdef"));
        assertEquals("abc_def", Strings.makeValidFilename("abc$$$def"));
        assertEquals("abc_def", Strings.makeValidFilename("$$$abc$$$def$$$"));
        assertEquals("a_b_c", Strings.makeValidFilename("a b c"));
        assertEquals("a.b.c", Strings.makeValidFilename("a.b.c"));
    }
    @Test(expectedExceptions = { NullPointerException.class })
    public void testMakeValidFilenameNull() {
        Strings.makeValidFilename(null);
    }
    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testMakeValidFilenameEmpty() {
        Strings.makeValidFilename("");
    }
    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testMakeValidFilenameBlank() {
        Strings.makeValidFilename("    \t    ");
    }

    public void makeValidJavaName() {
        assertEquals("__null", Strings.makeValidJavaName(null));
        assertEquals("__empty", Strings.makeValidJavaName(""));
        assertEquals("abcdef", Strings.makeValidJavaName("abcdef"));
        assertEquals("abcdef", Strings.makeValidJavaName("a'b'c'd'e'f"));
        assertEquals("_12345", Strings.makeValidJavaName("12345"));
    }

    public void makeValidUniqueJavaName() {
        assertEquals("__null", Strings.makeValidUniqueJavaName(null));
        assertEquals("__empty", Strings.makeValidUniqueJavaName(""));
        assertEquals("abcdef", Strings.makeValidUniqueJavaName("abcdef"));
        assertEquals("_12345", Strings.makeValidUniqueJavaName("12345"));
    }

    public void testRemoveFromEnd() {
        assertEquals("", Strings.removeFromEnd("", "bar"));
        assertEquals(null, Strings.removeFromEnd(null, "bar"));
        
        assertEquals("foo", Strings.removeFromEnd("foobar", "bar"));
        assertEquals("foo", Strings.removeFromEnd("foo", "bar"));
        assertEquals("foo", Strings.removeFromEnd("foobar", "foo", "bar"));
        // test they are applied in order
        assertEquals("foob", Strings.removeFromEnd("foobar", "ar", "bar", "b"));
    }

    public void testRemoveAllFromEnd() {
        assertEquals("", Strings.removeAllFromEnd("", "bar"));
        assertEquals(null, Strings.removeAllFromEnd(null, "bar"));
        
        assertEquals("", Strings.removeAllFromEnd("foobar", "foo", "bar"));
        assertEquals("f", Strings.removeAllFromEnd("foobar", "ar", "car", "b", "o"));
        // test they are applied in order
        assertEquals("foo", Strings.removeAllFromEnd("foobar", "ar", "car", "b", "ob"));
        assertEquals("foobar", Strings.removeAllFromEnd("foobar", "zz", "x"));
    }

    public void testRemoveFromStart() {
        assertEquals("", Strings.removeFromStart("", "foo"));
        assertEquals(null, Strings.removeFromStart(null, "foo"));
        
        assertEquals("bar", Strings.removeFromStart("foobar", "foo"));
        assertEquals("foo", Strings.removeFromStart("foo", "bar"));
        assertEquals("bar", Strings.removeFromStart("foobar", "foo", "bar"));
        assertEquals("obar", Strings.removeFromStart("foobar", "ob", "fo", "foo", "o"));
    }

    public void testRemoveAllFromStart() {
        assertEquals("", Strings.removeAllFromStart("", "foo"));
        assertEquals(null, Strings.removeAllFromStart(null, "foo"));
        
        assertEquals("bar", Strings.removeAllFromStart("foobar", "foo"));
        assertEquals("foo", Strings.removeAllFromStart("foo", "bar"));
        assertEquals("", Strings.removeAllFromStart("foobar", "foo", "bar"));
        
        assertEquals("ar", Strings.removeAllFromStart("foobar", "fo", "ob", "o"));
        assertEquals("ar", Strings.removeAllFromStart("foobar", "ob", "fo", "o"));
        // test they are applied in order, "ob" doesn't match because "o" eats the o
        assertEquals("bar", Strings.removeAllFromStart("foobar", "o", "fo", "ob"));
    }

    public void testRemoveFromStart2() {
        assertEquals(Strings.removeFromStart("xyz", "x"), "yz");
        assertEquals(Strings.removeFromStart("xyz", "."), "xyz");
        assertEquals(Strings.removeFromStart("http://foo.com", "http://"), "foo.com");
    }
    
    public void testRemoveFromEnd2() {
        assertEquals(Strings.removeFromEnd("xyz", "z"), "xy");
        assertEquals(Strings.removeFromEnd("xyz", "."), "xyz");
        assertEquals(Strings.removeFromEnd("http://foo.com/", "/"), "http://foo.com");
    }

    public void testReplaceAll() {
        assertEquals(Strings.replaceAll("xyz", "x", ""), "yz");
        assertEquals(Strings.replaceAll("xyz", ".", ""), "xyz");
        assertEquals(Strings.replaceAll("http://foo.com/", "/", ""), "http:foo.com");
        assertEquals(Strings.replaceAll("http://foo.com/", "http:", "https:"), "https://foo.com/");
    }

    public void testReplaceAllNonRegex() {
        assertEquals(Strings.replaceAllNonRegex("xyz", "x", ""), "yz");
        assertEquals(Strings.replaceAllNonRegex("xyz", ".", ""), "xyz");
        assertEquals(Strings.replaceAllNonRegex("http://foo.com/", "/", ""), "http:foo.com");
        assertEquals(Strings.replaceAllNonRegex("http://foo.com/", "http:", "https:"), "https://foo.com/");
    }

    public void testReplaceAllRegex() {
        assertEquals(Strings.replaceAllRegex("xyz", "x", ""), "yz");
        assertEquals(Strings.replaceAllRegex("xyz", ".", ""), "");
        assertEquals(Strings.replaceAllRegex("http://foo.com/", "/", ""), "http:foo.com");
        assertEquals(Strings.replaceAllRegex("http://foo.com/", "http:", "https:"), "https://foo.com/");
    }

    public void testReplaceMap() {
        assertEquals(Strings.replaceAll("xyz", MutableMap.builder().put("x","a").put("y","").build()), "az");
    }

    public void testContainsLiteral() {
        assertTrue(Strings.containsLiteral("hello", "ell"));
        assertTrue(Strings.containsLiteral("hello", "h"));
        assertFalse(Strings.containsLiteral("hello", "H"));
        assertFalse(Strings.containsLiteral("hello", "O"));
        assertFalse(Strings.containsLiteral("hello", "x"));
        assertFalse(Strings.containsLiteral("hello", "ELL"));
        assertTrue(Strings.containsLiteral("hello", "hello"));
        assertTrue(Strings.containsLiteral("hELlo", "ELl"));
        assertFalse(Strings.containsLiteral("hello", "!"));
    }
    
    public void testContainsLiteralIgnoreCase() {
        assertTrue(Strings.containsLiteralIgnoreCase("hello", "ell"));
        assertTrue(Strings.containsLiteralIgnoreCase("hello", "H"));
        assertTrue(Strings.containsLiteralIgnoreCase("hello", "O"));
        assertFalse(Strings.containsLiteralIgnoreCase("hello", "X"));
        assertTrue(Strings.containsLiteralIgnoreCase("hello", "ELL"));
        assertTrue(Strings.containsLiteralIgnoreCase("hello", "hello"));
        assertTrue(Strings.containsLiteralIgnoreCase("hELlo", "Hello"));
        assertFalse(Strings.containsLiteralIgnoreCase("hello", "!"));
    }

    public void testSizeString() {
        assertEquals(Strings.makeSizeString(23456789), "23.5mb");
    }
}

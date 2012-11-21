/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package brooklyn.util.text;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import brooklyn.util.MutableMap;

@Test
public class StringsTest {

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

    public void testContainsLiteralCaseInsensitive() {
        assertTrue(Strings.containsLiteralCaseInsensitive("hello", "ell"));
        assertTrue(Strings.containsLiteralCaseInsensitive("hello", "H"));
        assertTrue(Strings.containsLiteralCaseInsensitive("hello", "O"));
        assertFalse(Strings.containsLiteralCaseInsensitive("hello", "X"));
        assertTrue(Strings.containsLiteralCaseInsensitive("hello", "ELL"));
        assertTrue(Strings.containsLiteralCaseInsensitive("hello", "hello"));
        assertTrue(Strings.containsLiteralCaseInsensitive("hELlo", "Hello"));
        assertFalse(Strings.containsLiteralCaseInsensitive("hello", "!"));
    }
}

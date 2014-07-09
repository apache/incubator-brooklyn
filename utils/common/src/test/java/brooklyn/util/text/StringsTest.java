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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.test.FixedLocaleTest;
import brooklyn.util.collections.MutableMap;

@Test
public class StringsTest extends FixedLocaleTest {

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

    @Test
    public void testDeferredFormat() {
        ToStringCounter c = new ToStringCounter();
        FormattedString x = Strings.format("hello %s", c);
        Assert.assertEquals(c.count, 0);
        Assert.assertEquals(x.toString(), "hello world");
        Assert.assertEquals(c.count, 1);
    }

    @Test
    public void testToStringSupplier() {
        ToStringCounter c = new ToStringCounter(true);
        Assert.assertEquals(Strings.toStringSupplier(c).get(), "world1");
        FormattedString x = Strings.format("hello %s", c);
        Assert.assertEquals(x.toString(), "hello world2");
        Assert.assertEquals(x.toString(), "hello world3");
    }

    private static class ToStringCounter {
        private int count = 0;
        private boolean appendCount = false;
        private ToStringCounter() {}
        private ToStringCounter(boolean append) { this.appendCount = append; }
        @Override
        public String toString() {
            count++;
            return "world"+(appendCount?""+count:"");
        }
    }

    @Test
    public void testFormatter() {
        Assert.assertEquals(StringFunctions.formatter("hello %s").apply("world"), "hello world");
        Assert.assertEquals(StringFunctions.formatterForArray("%s %s").apply(new String[] { "hello", "world" }), "hello world");
    }

    @Test
    public void testJoiner() {
        Assert.assertEquals(StringFunctions.joiner(" ").apply(Arrays.asList("hello", "world")), "hello world");
        Assert.assertEquals(StringFunctions.joinerForArray(" ").apply(new String[] { "hello", "world" }), "hello world");
    }

    @Test
    public void testSurround() {
        Assert.assertEquals(StringFunctions.surround("hello ", " world").apply("new"), "hello new world");
    }

    @Test
    public void testFirstWord() {
        Assert.assertEquals(Strings.getFirstWord("hello world"), "hello");
        Assert.assertEquals(Strings.getFirstWord("   hello world"), "hello");
        Assert.assertEquals(Strings.getFirstWord("   hello   "), "hello");
        Assert.assertEquals(Strings.getFirstWord("hello"), "hello");
        Assert.assertEquals(Strings.getFirstWord("  "), null);
        Assert.assertEquals(Strings.getFirstWord(""), null);
        Assert.assertEquals(Strings.getFirstWord(null), null);
    }

    @Test
    public void testLastWord() {
        Assert.assertEquals(Strings.getLastWord("hello world"), "world");
        Assert.assertEquals(Strings.getLastWord("   hello world  "), "world");
        Assert.assertEquals(Strings.getLastWord("   hello   "), "hello");
        Assert.assertEquals(Strings.getLastWord("hello"), "hello");
        Assert.assertEquals(Strings.getLastWord("  "), null);
        Assert.assertEquals(Strings.getLastWord(""), null);
        Assert.assertEquals(Strings.getLastWord(null), null);
    }

    @Test
    public void testFirstWordAfter() {
        Assert.assertEquals(Strings.getFirstWordAfter("hello world", "hello"), "world");
        Assert.assertEquals(Strings.getFirstWordAfter("   hello world", "hello"), "world");
        Assert.assertEquals(Strings.getFirstWordAfter("   hello world: is not enough", "world:"), "is");
        Assert.assertEquals(Strings.getFirstWordAfter("   hello world: is not enough", "world"), ":");
        Assert.assertEquals(Strings.getFirstWordAfter("   hello   ", "hello"), null);
        Assert.assertEquals(Strings.getFirstWordAfter("hello", "hello"), null);
        Assert.assertEquals(Strings.getFirstWordAfter("  ", "x"), null);
        Assert.assertEquals(Strings.getFirstWordAfter("", "x"), null);
        Assert.assertEquals(Strings.getFirstWordAfter(null, "x"), null);
    }

    @Test
    public void testFragmentBetween() {
        Assert.assertEquals("ooba", Strings.getFragmentBetween("foobar", "f", "r"));
        Assert.assertEquals("oobar", Strings.getFragmentBetween("foobar", "f", "z"));
        Assert.assertEquals("oobar", Strings.getFragmentBetween("foobar", "f", null));
        Assert.assertEquals("oba", Strings.getFragmentBetween("foobar", "o", "r"));
        Assert.assertEquals("\nba", Strings.getFragmentBetween("foo\nbar", "foo", "r"));
        Assert.assertEquals("fooba", Strings.getFragmentBetween("foobar", null, "r"));
        Assert.assertEquals(null, Strings.getFragmentBetween("foobar", "z", "r"));
    }

    @Test
    public void testWordCount() {
        Assert.assertEquals(Strings.getWordCount("hello", true), 1);
        Assert.assertEquals(Strings.getWordCount("hello world", true), 2);
        Assert.assertEquals(Strings.getWordCount("hello\nworld", true), 2);
        Assert.assertEquals(Strings.getWordCount("hello world \nit is me!\n", true), 5);
        Assert.assertEquals(Strings.getWordCount("", true), 0);
        Assert.assertEquals(Strings.getWordCount(null, true), 0);
        Assert.assertEquals(Strings.getWordCount("\"hello world\" ", true), 1);
        Assert.assertEquals(Strings.getWordCount("\"hello world\" ", false), 2);
        Assert.assertEquals(Strings.getWordCount("hello world \nit's me!\n", true), 3);
        Assert.assertEquals(Strings.getWordCount("hello world \nit's me!\n", false), 4);
    }

    @Test
    public void testMakeRealString() {
        // less precision = less length
        Assert.assertEquals(Strings.makeRealString(1.23456d, 4, 2, 0), "1.2");
        // precision trumps length, and rounds
        Assert.assertEquals(Strings.makeRealString(1.23456d, 4, 5, 0), "1.2346");
        // uses E notation when needed
        Assert.assertEquals(Strings.makeRealString(123456, 2, 2, 0), "1.2E5");
        // and works with negatives
        Assert.assertEquals(Strings.makeRealString(-123456, 2, 2, 0), "-1.2E5");
        // and very small negatives
        Assert.assertEquals(Strings.makeRealString(-0.000000000123456, 2, 2, 0), "-1.2E-10");
        // and 0
        Assert.assertEquals(Strings.makeRealString(0.0d, 4, 2, 0), "0");
        // skips E notation and gives extra precision when it's free
        Assert.assertEquals(Strings.makeRealString(123456, 8, 2, 0), "123456");
    }
    
}

/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package brooklyn.util.text;

import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * The ConfigParserTest
 *
 * @author aled
 **/
public class KeyValueParserTest {

    // Gives an example of how to do this with guava. But note that does not
    // give the same behaviour for quoted values.
    @Test
    public void testGuavaEquivalent() {
        assertOrderedMapsEqual(Splitter.on(",").withKeyValueSeparator("=").split("a=x,b=y"), ImmutableMap.of("a", "x", "b", "y"));
    }
    
    @Test
    public void testTrimsWhiteSpaceOutsideOfQuotes() throws Exception {
        assertOrderedMapsEqual(KeyValueParser.parseMap("a=x"), ImmutableMap.of("a", "x"));
        assertOrderedMapsEqual(KeyValueParser.parseMap("a=x "), ImmutableMap.of("a", "x"));
        assertOrderedMapsEqual(KeyValueParser.parseMap(" a=x"), ImmutableMap.of("a", "x"));
        assertOrderedMapsEqual(KeyValueParser.parseMap("a =x"), ImmutableMap.of("a", "x"));
        assertOrderedMapsEqual(KeyValueParser.parseMap("a= x"), ImmutableMap.of("a", "x"));
        assertOrderedMapsEqual(KeyValueParser.parseMap("a = x"), ImmutableMap.of("a", "x"));

        assertOrderedMapsEqual(KeyValueParser.parseMap("a=\"x\""), ImmutableMap.of("a", "x"));
        assertOrderedMapsEqual(KeyValueParser.parseMap("a=\"x\" "), ImmutableMap.of("a", "x"));
        assertOrderedMapsEqual(KeyValueParser.parseMap(" a=\"x\""), ImmutableMap.of("a", "x"));
        assertOrderedMapsEqual(KeyValueParser.parseMap("a =\"x\""), ImmutableMap.of("a", "x"));
        assertOrderedMapsEqual(KeyValueParser.parseMap("a= \"x\""), ImmutableMap.of("a", "x"));
        assertOrderedMapsEqual(KeyValueParser.parseMap("a = \"x\""), ImmutableMap.of("a", "x"));
        
        assertOrderedMapsEqual(KeyValueParser.parseMap("a=x,b=y"), ImmutableMap.of("a", "x", "b", "y"));
        assertOrderedMapsEqual(KeyValueParser.parseMap("a=x, b=y"), ImmutableMap.of("a", "x", "b", "y"));
        assertOrderedMapsEqual(KeyValueParser.parseMap("a=x,\tb=y"), ImmutableMap.of("a", "x", "b", "y"));
        assertOrderedMapsEqual(KeyValueParser.parseMap("a=x ,b=y"), ImmutableMap.of("a", "x", "b", "y"));
    }

    @Test
    public void testPreservesWhiteSpaceInsideQuotes() throws Exception {
        assertOrderedMapsEqual(KeyValueParser.parseMap("a=\" x \""), ImmutableMap.of("a", " x "));
    }

    @Test
    public void testConfigParseMap() throws Exception {
        Map<String, String> result = KeyValueParser.parseMap("a=x, b=\"x x\", c, \"d d\"");
        Map<String, String> expected = Maps.newLinkedHashMap();
        expected.put("a", "x");
        expected.put("b", "x x");
        expected.put("c", null);
        expected.put("d d", null);
        
        assertOrderedMapsEqual(result, expected);
        assertOrderedMapsEqual(KeyValueParser.parseMap(KeyValueParser.toLine(expected)), expected);
    }
    
    @Test
    public void testConfigParseMapWithBigWhiteSpace() throws Exception {
        Map<String, String> result = KeyValueParser.parseMap(" a=x,  b=y ");
        Map<String, String> expected = Maps.newLinkedHashMap();
        expected.put("a", "x");
        expected.put("b", "y");
        
        assertOrderedMapsEqual(result, expected);
        assertOrderedMapsEqual(KeyValueParser.parseMap(KeyValueParser.toLine(expected)), expected);
    }
    
    @Test
    public void testConfigParseMapWithEmptyValue() throws Exception {
        Map<String, String> result = KeyValueParser.parseMap("a=\"\"");
        Map<String, String> expected = Maps.newLinkedHashMap();
        expected.put("a", "");
        
        assertOrderedMapsEqual(result, expected);
        assertOrderedMapsEqual(KeyValueParser.parseMap(KeyValueParser.toLine(expected)), expected);
    }
    
    @Test
    public void testConfigParseMapWithNoValue() throws Exception {
        Map<String, String> result = KeyValueParser.parseMap("a=, b");
        Map<String, String> expected = Maps.newLinkedHashMap();
        expected.put("a", "");
        expected.put("b", null);
        
        assertOrderedMapsEqual(result, expected);
        assertOrderedMapsEqual(KeyValueParser.parseMap(KeyValueParser.toLine(expected)), expected);
    }
    
    @Test
    public void testConfigParseList() throws Exception {
        assertParsedList(Arrays.asList("a", "b b"));
        assertParsedList(Arrays.asList("\"a\" \"a\""));
        assertParsedList(Arrays.<String>asList());
        
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i <= 127; i++) {
            ascii.append((char)i);
        }
        assertParsedList(Arrays.asList(ascii.toString()));
    }
    
    @Test
    public void testConfigParseListWithEmptyValue() throws Exception {
        assertParsedList(Arrays.asList(""));
        assertEquals(KeyValueParser.parseList("\"\""), Arrays.asList(""));
    }
    
    private void assertParsedList(List<String> expected) {
        assertEquals(KeyValueParser.parseList(KeyValueParser.toLine(expected)), expected);
    }
    
    private void assertOrderedMapsEqual(Map<?,?> map1, Map<?,?> map2) {
        Assert.assertEquals(map1, map2);
        Assert.assertEquals(ImmutableList.copyOf(map1.keySet()), ImmutableList.copyOf(map2.keySet()));
    }
}

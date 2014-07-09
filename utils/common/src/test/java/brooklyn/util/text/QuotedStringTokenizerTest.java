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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

public class QuotedStringTokenizerTest {

    // have to initialise to use the methods (instance as it can take custom tokens)
    private QuotedStringTokenizer defaultTokenizer= new QuotedStringTokenizer("", true); 

    @Test
    public void testQuoting() throws Exception {
        assertQuoteUnquoteFor("a=b");
        assertQuoteUnquoteFor("a=\"things\",b=c");
        assertQuoteUnquoteFor("thing=\"\"");
        assertQuoteUnquoteFor("\"thing\"=\"\"");
        assertQuoteUnquoteFor("");
        assertQuoteUnquoteFor("\"");
        assertQuoteUnquoteFor("\"\"");

        assertUnquoteFor("", "''");
        assertUnquoteFor("thing=", "\"thing\"=\"\"");
        assertUnquoteFor("a=", "a=\"\"");
    }

    @Test
    public void testTokenizing() throws Exception {
        testResultingTokens("foo,bar,baz", "\"", false, ",", false, "foo", "bar", "baz");
        testResultingTokens("\"foo,bar\",baz", "\"", false, ",", false, "foo,bar", "baz");
        testResultingTokens("\"foo,,bar\",baz", "\"", false, ",", false, "foo,,bar", "baz");

        // Have seen "the operator ""foo"" is not recognised" entries in BAML CSV files.
        testResultingTokens("foo \"\"bar\"\" baz", "\"", false, ",", false, "foo bar baz");
        testResultingTokens("\"foo \"\"bar\"\" baz\"", "\"", false, ",", false, "foo bar baz");

        // FIXME: would like to return empty tokens when we encounter adjacent delimiters, but need
        // to work around brain-dead java.util.StringTokenizer to do this.
        // testResultingTokens("foo,,baz", "\"", false, ",", false, "foo", "", "baz");
    }

    @Test
    public void testTokenizingBuilder() throws Exception {
        Assert.assertEquals(Arrays.asList("foo", "bar"), QuotedStringTokenizer.builder().buildList("foo bar"));
        Assert.assertEquals(Arrays.asList("foo,bar"), QuotedStringTokenizer.builder().buildList("foo,bar"));
        Assert.assertEquals(Arrays.asList("foo", "bar"), QuotedStringTokenizer.builder().delimiterChars(",").buildList("foo,bar"));
        Assert.assertEquals(Arrays.asList("foo", " bar"), QuotedStringTokenizer.builder().delimiterChars(",").buildList("foo, bar"));
        Assert.assertEquals(Arrays.asList("foo", "bar"), QuotedStringTokenizer.builder().addDelimiterChars(",").buildList("foo, bar"));
    }

    @Test
    public void testCommaInQuotes() throws Exception {
        List<String> l = QuotedStringTokenizer.builder().addDelimiterChars(",").buildList("location1,byon:(hosts=\"loc2,loc3\"),location4");
        Assert.assertEquals(Arrays.asList("location1", "byon:(hosts=\"loc2,loc3\")", "location4"), l);
    }

    /** not implemented yet */
    @Test(enabled=false)
    public void testCommaInParentheses() throws Exception {
        List<String> l = QuotedStringTokenizer.builder().addDelimiterChars(",").buildList("location1, byon:(hosts=\"loc2,loc3\",user=foo),location4");
        Assert.assertEquals(Arrays.asList("location1", "byon:(hosts=\"loc2,loc3\",user=foo)", "location4"), l);
    }

    private void testResultingTokens(String input, String quoteChars, boolean includeQuotes, String delimiterChars, boolean includeDelimiters, String... expectedTokens) {
        QuotedStringTokenizer tok = new QuotedStringTokenizer(input, quoteChars, includeQuotes, delimiterChars, includeDelimiters);
        testResultingTokens(input, tok, expectedTokens);
    }

    private void testResultingTokens(String input, QuotedStringTokenizer tok, String... expectedTokens) {
        List<String> actual = new LinkedList<String>();
        while (tok.hasMoreTokens()) actual.add(tok.nextToken());
        assertEquals(actual, Arrays.asList(expectedTokens), "Wrong tokens returned.");
    }

    private void assertQuoteUnquoteFor(String unquoted) {
        String quoted = defaultTokenizer.quoteToken(unquoted);
        String reunquoted = defaultTokenizer.unquoteToken(quoted);
        //System.out.println("orig="+unquoted+"  quoted="+quoted+"   reunquoted="+reunquoted);
        assertEquals(reunquoted, unquoted);
    }

    private void assertUnquoteFor(String expected, String quoted) {
        String unquoted = defaultTokenizer.unquoteToken(quoted);
        //System.out.println("expected="+expected+"  quoted="+quoted+"   unquoted="+unquoted);
        assertEquals(unquoted, expected);
    }
}

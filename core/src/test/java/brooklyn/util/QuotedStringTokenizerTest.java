/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package brooklyn.util;

import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * The ConfigParserTest
 *
 * @author aled
 **/
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

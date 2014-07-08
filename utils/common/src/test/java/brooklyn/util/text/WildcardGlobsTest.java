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

import java.util.Arrays;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.text.WildcardGlobs.InvalidPatternException;
import brooklyn.util.text.WildcardGlobs.PhraseTreatment;
import brooklyn.util.text.WildcardGlobs.SpecialistGlobExpander;

@Test
public class WildcardGlobsTest extends Assert {

    @Test
	public void testBasic() throws InvalidPatternException {
		assertTrue(WildcardGlobs.isGlobMatched("a?{ex,in}", "akin")); 
		assertTrue(WildcardGlobs.isGlobMatched("a?{ex,in}", "alex"));
		assertFalse(WildcardGlobs.isGlobMatched("a?{ex,in}", "appin"));
	}
	
	@Test
	public void testEmpty() throws InvalidPatternException {
		assertTrue(WildcardGlobs.isGlobMatched("a{,?}{,b}", "a"));
		assertTrue(WildcardGlobs.isGlobMatched("a{,?}{,b}", "ab"));
		assertTrue(WildcardGlobs.isGlobMatched("a{,?}{,b}", "ac"));
		assertTrue(WildcardGlobs.isGlobMatched("a{,?}{,b}", "acb"));
		assertFalse(WildcardGlobs.isGlobMatched("a{,?}{,b}", "abc"));
		assertFalse(WildcardGlobs.isGlobMatched("a{,?}{,b}", "accb"));
	}

	@Test
	public void testNum() throws InvalidPatternException {
		assertTrue(newGlobExpander().isGlobMatchedNumeric("{1-3}", "1"));
		assertTrue(newGlobExpander().isGlobMatchedNumeric("a{1-3}", "a1"));
		assertTrue(newGlobExpander().isGlobMatchedNumeric("a{1-3,5}", "a1"));
		assertTrue(newGlobExpander().isGlobMatchedNumeric("a{1-3,5}", "a3"));
		assertTrue(newGlobExpander().isGlobMatchedNumeric("a{1-3,5}", "a5"));
		assertFalse(newGlobExpander().isGlobMatchedNumeric("a{1-3,5}", "a4"));
		assertFalse(newGlobExpander().isGlobMatchedNumeric("a{1-3,5}", "a01"));
	}

	@Test
	public void testNumLeadZero() throws InvalidPatternException {
		assertTrue(newGlobExpander().isGlobMatchedNumeric("a{01-03,05}", "a01"));
		assertTrue(newGlobExpander().isGlobMatchedNumeric("a{ 01  - 03 , 05 }", "a01"));
		assertTrue(newGlobExpander().isGlobMatchedNumeric("a{ 01  - 03 , 05 }", "a02"));
		assertTrue(newGlobExpander().isGlobMatchedNumeric("a{ 01  - 03 , 05 }", "a03"));
		assertTrue(newGlobExpander().isGlobMatchedNumeric("a{ 01  - 03 , 05 }", "a 05 "));
		assertTrue(newGlobExpander().isGlobMatchedNumeric("a{01-03,05}", "a05"));
		assertFalse(newGlobExpander().isGlobMatchedNumeric("a{01-03,05}", "a04"));
		assertFalse(newGlobExpander().isGlobMatchedNumeric("a{01-03,05}", "a3"));
	}

	@Test
	public void testOrder() throws InvalidPatternException {
	    List<String> result;
	    result = newGlobExpander().expand("{a,b}");
	    assertEquals(result, Arrays.asList("a","b"), "Expansion was "+result);
	    result = newGlobExpander().expand("{{a},b}");
	    assertEquals(result, Arrays.asList("a","b"), "Expansion was "+result);
        result = newGlobExpander().expand("{a,b}{1,2}");
        assertEquals(result, Arrays.asList("a1","a2","b1","b2"), "Expansion was "+result);
	    result = newGlobExpander().expand("{80{8{1,2},90},8000+}");
	    assertEquals(result, Arrays.asList("8081","8082","8090","8000+"), "Expansion was "+result);
	}
	   
	@Test
	public void testQuotes() throws InvalidPatternException {
		List<String> result;
		SpecialistGlobExpander notSpecial = new SpecialistGlobExpander(true, PhraseTreatment.NOT_A_SPECIAL_CHAR, PhraseTreatment.NOT_A_SPECIAL_CHAR);
		result = notSpecial.expand("hello \"{1-3}\"");
		assertEquals(3, result.size());
		assertEquals("hello \"1\"", result.get(0));
		
		SpecialistGlobExpander expanding = new SpecialistGlobExpander(true, PhraseTreatment.INTERIOR_EXPANDABLE, PhraseTreatment.NOT_A_SPECIAL_CHAR);
		result = expanding.expand("hello \"{1-3}\"");
		assertEquals(3, result.size());
		assertEquals("hello \"1\"", result.get(0));
		result = expanding.expand("hello \"{1,2-3}\"");
		assertEquals(3, result.size());
		assertEquals("hello \"2\"", result.get(1));

		SpecialistGlobExpander notExpanding = new SpecialistGlobExpander(true, PhraseTreatment.INTERIOR_NOT_EXPANDABLE, PhraseTreatment.NOT_A_SPECIAL_CHAR);
		result = notExpanding.expand("hello \"{1,2-3}\"");
		assertEquals(1, result.size());
		assertEquals("hello \"{1,2-3}\"", result.get(0));

		
		result = notSpecial.expand("hello {\"1,2,3\"}");
		assertEquals(3, result.size());
		assertEquals("hello \"1", result.get(0));

		result = expanding.expand("hello {\"1,2,3\"}");
		assertEquals(1, result.size());
		assertEquals("hello \"1,2,3\"", result.get(0));

		result = notExpanding.expand("hello {\"1,2,3\"}");
		assertEquals(1, result.size());
		assertEquals("hello \"1,2,3\"", result.get(0));

		
		result = notSpecial.expand("hello {\"1,{02-03,04}\"}");
		assertEquals(4, result.size());
		assertEquals("hello \"1", result.get(0));
		assertEquals("hello 03\"", result.get(2));

		result = expanding.expand("hello {\"1,{02-03,04}\"}");
		assertEquals(3, result.size());
		assertEquals("hello \"1,02\"", result.get(0));

		result = notExpanding.expand("hello {\"1,{02-03,04}\"}");
		assertEquals(1, result.size());
		assertEquals("hello \"1,{02-03,04}\"", result.get(0));
		
		//no exception
		notSpecial.expand("{\"}");
		notSpecial.expand("\"{\"}");
		//exceptions
		try {
			expanding.expand("\"");			
			fail("exception expected");
		} catch (InvalidPatternException e) { /* expected */ }
		try {
			expanding.expand("{\"}");			
			fail("exception expected");
		} catch (InvalidPatternException e) { /* expected */ }
		try {
			expanding.expand("\"{\"");			
			fail("exception expected");
		} catch (InvalidPatternException e) { /* expected */ }
		try {
			notExpanding.expand("\"");			
			fail("exception expected");
		} catch (InvalidPatternException e) { /* expected */ }
		try {
			notExpanding.expand("{\"}");			
			fail("exception expected");
		} catch (InvalidPatternException e) { /* expected */ }
		//no exception
		notExpanding.expand("\"{\"");			
	}

	@Test
	public void testParen() throws InvalidPatternException {
		List<String> result;
		SpecialistGlobExpander notSpecial = new SpecialistGlobExpander(true, PhraseTreatment.NOT_A_SPECIAL_CHAR, PhraseTreatment.NOT_A_SPECIAL_CHAR);
		result = notSpecial.expand("hello ({1-3})");
		assertEquals(3, result.size());
		assertEquals("hello (1)", result.get(0));
		
		SpecialistGlobExpander expanding = new SpecialistGlobExpander(true, PhraseTreatment.INTERIOR_NOT_EXPANDABLE, PhraseTreatment.INTERIOR_EXPANDABLE);
		result = expanding.expand("hello ({1-3})");
		assertEquals(3, result.size());
		assertEquals("hello (1)", result.get(0));
		result = expanding.expand("hello ({1,2-3})");
		assertEquals(3, result.size());
		assertEquals("hello (2)", result.get(1));

		SpecialistGlobExpander notExpanding = new SpecialistGlobExpander(true, PhraseTreatment.INTERIOR_EXPANDABLE, PhraseTreatment.INTERIOR_NOT_EXPANDABLE);
		result = notExpanding.expand("hello ({1,2-3})");
		assertEquals(1, result.size());
		assertEquals("hello ({1,2-3})", result.get(0));
		
		result = notSpecial.expand("hello {(1,2,3)}");
		assertEquals(3, result.size());
		assertEquals("hello (1", result.get(0));

		result = expanding.expand("hello {(1,2,3)}");
		assertEquals(1, result.size());
		assertEquals("hello (1,2,3)", result.get(0));

		result = notExpanding.expand("hello {(1,2,3)}");
		assertEquals(1, result.size());
		assertEquals("hello (1,2,3)", result.get(0));

		
		result = notSpecial.expand("hello {(1,{02-03,04})}");
		assertEquals(4, result.size());
		assertEquals("hello (1", result.get(0));
		assertEquals("hello 03)", result.get(2));

		result = expanding.expand("hello {(1,{02-03,04})}");
		assertEquals(3, result.size());
		assertEquals("hello (1,02)", result.get(0));

		result = notExpanding.expand("hello {(1,{02-03,04})}");
		assertEquals(1, result.size());
		assertEquals("hello (1,{02-03,04})", result.get(0));
		
		try {
			notExpanding.expand("{(}");			
			fail("exception expected");
		} catch (InvalidPatternException e) { /* expected */ }
		try {
			notExpanding.expand("(()");			
			fail("exception expected");
		} catch (InvalidPatternException e) { /* expected */ }
		notExpanding.expand("({())");			
	}

	@Test
	public void testQuotesAndParen() throws InvalidPatternException {
		List<String> result;
		SpecialistGlobExpander special = new SpecialistGlobExpander(true, PhraseTreatment.INTERIOR_EXPANDABLE, PhraseTreatment.INTERIOR_NOT_EXPANDABLE);
		result = special.expand("\"{hello,goodbye}{1-2,({3-4}),(\")}\"");
		assertEquals(8, result.size());
		assertTrue(result.contains("\"goodbye2\""));
		assertTrue(result.contains("\"hello({3-4})\""));
		assertTrue(result.contains("\"goodbye(\")\""));
	}
	
	private SpecialistGlobExpander newGlobExpander() {
		return new SpecialistGlobExpander(true, PhraseTreatment.NOT_A_SPECIAL_CHAR, PhraseTreatment.NOT_A_SPECIAL_CHAR);
	}

}

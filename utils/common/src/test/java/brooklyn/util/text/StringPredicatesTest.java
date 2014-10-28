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

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;

public class StringPredicatesTest {

    @Test
    public static void testIsBlank() {
        Assert.assertTrue(StringPredicates.isBlank().apply(""));
        Assert.assertTrue(StringPredicates.isBlank().apply(" \n\t"));
        Assert.assertTrue(StringPredicates.isBlank().apply(null));
        Assert.assertFalse(StringPredicates.isBlank().apply(" hi "));
    }
    
    @Test
    public static void testContainsLiteral() {
        Assert.assertTrue(StringPredicates.containsLiteral("xx").apply("texxxt tessst"));
        Assert.assertFalse(StringPredicates.containsLiteral("xx").apply("text test"));
        Assert.assertFalse(StringPredicates.containsLiteral("xx").apply("texXxt tessst"));
        
        Assert.assertTrue(StringPredicates.containsLiteralIgnoreCase("xx").apply("texxxt tessst"));
        Assert.assertFalse(StringPredicates.containsLiteralIgnoreCase("xx").apply("text test"));
        Assert.assertTrue(StringPredicates.containsLiteralIgnoreCase("xx").apply("texXxt tessst"));
        
        Assert.assertTrue(StringPredicates.containsAllLiterals("xx", "ss").apply("texxxt tessst"));
        Assert.assertFalse(StringPredicates.containsAllLiterals("xx", "tt").apply("texxxt tessst"));
    }
    
    @Test
    public static void testEqualToAny() {
        Assert.assertTrue(StringPredicates.equalToAny(ImmutableSet.of("1", "2")).apply("2"));
        Assert.assertFalse(StringPredicates.equalToAny(ImmutableSet.of("1", "2")).apply("3"));
    }
    
    @Test
    public static void testStartsWith() {
        Assert.assertTrue(StringPredicates.startsWith("t").apply("test"));
        Assert.assertFalse(StringPredicates.startsWith("v").apply("test"));
        
        Assert.assertTrue(StringPredicates.isStringStartingWith("t").apply("test"));
        Assert.assertFalse(StringPredicates.isStringStartingWith("t").apply(true));
    }
    
    @Test
    public static void testMatches() {
        Assert.assertTrue(StringPredicates.matchesRegex("t.*").apply("test"));
        Assert.assertFalse(StringPredicates.matchesRegex("v.*").apply("test"));
        
        Assert.assertTrue(StringPredicates.matchesGlob("t*").apply("test"));
        Assert.assertFalse(StringPredicates.matchesGlob("v*").apply("test"));
    }
    
}

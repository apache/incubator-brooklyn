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
package org.apache.brooklyn.core.config;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;

public class ConfigPredicatesTest extends BrooklynAppUnitTestSupport {

    private final ConfigKey<String> STR1 = ConfigKeys.newStringConfigKey("test.str1");
    
    @Test
    public void testNameMatchingPredicate() throws Exception {
        assertTrue(ConfigPredicates.nameMatching(Predicates.equalTo("test.str1")).apply(STR1));
        assertFalse(ConfigPredicates.nameMatching(Predicates.equalTo("wrong")).apply(STR1));
    }
    
    @Test
    public void testNameMatchingGlob() throws Exception {
        assertTrue(ConfigPredicates.matchingGlob("*str*").apply(STR1));
        assertFalse(ConfigPredicates.matchingGlob("*wrong*").apply(STR1));
    }
    
    @Test
    public void testNameMatchingRegex() throws Exception {
        assertTrue(ConfigPredicates.matchingRegex(".*str.*").apply(STR1));
        assertFalse(ConfigPredicates.matchingRegex(".*wrong.*").apply(STR1));
    }
    
    @Test
    public void testNameStartingWith() throws Exception {
        assertTrue(ConfigPredicates.startingWith("test.s").apply(STR1));
        assertFalse(ConfigPredicates.startingWith("wrong.s").apply(STR1));
    }
    
    @Test
    public void testNameEqualTo() throws Exception {
        assertTrue(ConfigPredicates.nameEqualTo("test.str1").apply(STR1));
        assertFalse(ConfigPredicates.nameEqualTo("wrong").apply(STR1));
    }
    
    @Test
    public void testNameSatisfies() throws Exception {
        assertTrue(ConfigPredicates.nameSatisfies(Predicates.equalTo("test.str1")).apply(STR1));
        assertFalse(ConfigPredicates.nameSatisfies(Predicates.equalTo("wrong")).apply(STR1));
    }
    
    @Test
    public void testNameMatchesGlob() throws Exception {
        assertTrue(ConfigPredicates.nameMatchesGlob("*str*").apply(STR1));
        assertFalse(ConfigPredicates.nameMatchesGlob("*wrong*").apply(STR1));
    }
    
    @Test
    public void testNameMatchesRegex() throws Exception {
        assertTrue(ConfigPredicates.nameMatchesRegex(".*str.*").apply(STR1));
        assertFalse(ConfigPredicates.nameMatchesRegex(".*wrong.*").apply(STR1));
    }
    
    @Test
    public void testNameStartsWith() throws Exception {
        assertTrue(ConfigPredicates.nameStartsWith("test.s").apply(STR1));
        assertFalse(ConfigPredicates.nameStartsWith("wrong.s").apply(STR1));
    }
}

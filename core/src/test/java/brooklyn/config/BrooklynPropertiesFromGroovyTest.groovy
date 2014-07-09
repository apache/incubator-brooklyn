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
package brooklyn.config;

import static org.testng.Assert.assertEquals
import static org.testng.Assert.fail

import org.testng.annotations.Test

import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap

public class BrooklynPropertiesFromGroovyTest {

    @Test
    public void testGetFirstUsingFailIfNoneWithClosure() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty().addFromMap(ImmutableMap.of("akey", "aval", "bkey", "bval"));
        Object keys;
        try {
            props.getFirst(MutableMap.of("failIfNone", { keys = it }), "notThere");
        } catch (NoSuchElementException e) {
            // expected
        }
        assertEquals(keys, "notThere");
    }
    
    @Test
    public void testGetFirstMultiArgUsingFailIfNoneWithClosure() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty().addFromMap(ImmutableMap.of("akey", "aval", "bkey", "bval"));
        Object keys;
        try {
            props.getFirst(MutableMap.of("failIfNone", { it1, it2 -> keys = [it1, it2] }), "notThere", "notThere2");
        } catch (NoSuchElementException e) {
            // expected
        }
        assertEquals(keys, ImmutableList.of("notThere", "notThere2"));
    }
}

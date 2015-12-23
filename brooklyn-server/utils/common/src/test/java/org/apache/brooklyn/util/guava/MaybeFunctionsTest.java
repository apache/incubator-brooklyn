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
package org.apache.brooklyn.util.guava;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import org.testng.annotations.Test;

public class MaybeFunctionsTest {

    @Test
    public void testWrap() throws Exception {
        Maybe<Object> result = MaybeFunctions.wrap().apply("myval");
        assertEquals(result.get(), "myval");
        
        Maybe<Object> result2 = MaybeFunctions.wrap().apply(null);
        assertFalse(result2.isPresent());
    }
    
    @Test
    public void testGet() throws Exception {
        assertEquals(MaybeFunctions.<String>get().apply(Maybe.of("myval")), "myval");
    }
    
    @Test
    public void testOr() throws Exception {
        assertEquals(MaybeFunctions.or("mydefault").apply(Maybe.of("myval")), "myval");
        assertEquals(MaybeFunctions.or("mydefault").apply(Maybe.<String>absent()), "mydefault");
    }
}

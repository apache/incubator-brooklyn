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
package brooklyn.util.math;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

public class MathPredicatesTest {

    @Test
    public void testGreaterThan() throws Exception {
        assertTrue(MathPredicates.greaterThan(2d).apply(3));
        assertFalse(MathPredicates.greaterThan(2d).apply(1));
        assertFalse(MathPredicates.greaterThan(2d).apply(2));
    }
    
    @Test
    public void testGreaterThanOrEqual() throws Exception {
        assertTrue(MathPredicates.greaterThanOrEqual(2d).apply(3));
        assertFalse(MathPredicates.greaterThanOrEqual(2d).apply(1));
        assertTrue(MathPredicates.greaterThanOrEqual(2d).apply(2));
    }
    
    @Test
    public void testLessThan() throws Exception {
        assertFalse(MathPredicates.lessThanOrEqual(2d).apply(3));
        assertTrue(MathPredicates.lessThan(2d).apply(1));
        assertFalse(MathPredicates.lessThan(2d).apply(2));
    }
    
    @Test
    public void testLessThanOrEqual() throws Exception {
        assertFalse(MathPredicates.lessThanOrEqual(2d).apply(3));
        assertTrue(MathPredicates.lessThanOrEqual(2d).apply(1));
        assertTrue(MathPredicates.lessThanOrEqual(2d).apply(2));
    }
}

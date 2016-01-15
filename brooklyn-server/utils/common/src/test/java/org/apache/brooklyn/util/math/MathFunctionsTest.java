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
package org.apache.brooklyn.util.math;

import org.apache.brooklyn.test.FixedLocaleTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class MathFunctionsTest extends FixedLocaleTest {

    @Test
    public void testPlus() {
        Assert.assertEquals(MathFunctions.plus(3).apply(4), (Integer)7);
        Assert.assertEquals(MathFunctions.plus(3L).apply(4L), (Long)7L);
        Assert.assertEquals(MathFunctions.plus(0.3).apply(0.4).doubleValue(), 0.7, 0.00000001);
    }
    
    @Test
    public void testTimes() {
        Assert.assertEquals(MathFunctions.times(3).apply(4), (Integer)12);
        Assert.assertEquals(MathFunctions.times(3L).apply(4L), (Long)12L);
        Assert.assertEquals(MathFunctions.times(0.3).apply(0.4).doubleValue(), 0.12, 0.00000001);
    }
    
    @Test
    public void testDivide() {
        Assert.assertEquals(MathFunctions.divide(2.0).apply(8), 4.0, 0.00000001);
    }

    @Test
    public void testReadableString() {
        Assert.assertEquals(MathFunctions.readableString(3, 5).apply(0.0123456), "1.23E-2");
    }
    
    @Test
    public void testPercent() {
        Assert.assertEquals(MathFunctions.percent(3).apply(0.0123456), "1.23%");
    }
    
}

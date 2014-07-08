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

import brooklyn.util.text.NaturalOrderComparator;

public class NaturalOrderComparatorTest {

    public static final NaturalOrderComparator noc = new NaturalOrderComparator();
    
    ComparableVersion v = new ComparableVersion("10.5.8");
    
    @Test
    public void testBasicOnes() {
        Assert.assertTrue(v.isGreaterThanAndNotEqualTo("10.5"));
        Assert.assertTrue(v.isGreaterThanOrEqualTo("10.5.8"));
        Assert.assertFalse(v.isGreaterThanAndNotEqualTo("10.5.8"));

        Assert.assertTrue(v.isLessThanAndNotEqualTo("10.6"));
        Assert.assertTrue(v.isLessThanOrEqualTo("10.5.8"));
        Assert.assertFalse(v.isLessThanAndNotEqualTo("10.5.8"));

        Assert.assertTrue(v.isInRange("[10.5,10.6)"));
        Assert.assertFalse(v.isInRange("[10.5,10.5.8)"));
        Assert.assertTrue(v.isInRange("[10.5,)"));
        Assert.assertTrue(v.isInRange("[9,)"));
        Assert.assertFalse(v.isInRange("(10.5.8,)"));
        Assert.assertFalse(v.isInRange("[10.6,)"));
        Assert.assertTrue(v.isInRange("[,11)"));
        Assert.assertTrue(v.isInRange("[,]"));
    }

    @Test(expectedExceptions={IllegalArgumentException.class})
    public void testError1() { v.isInRange("10.5"); }
    @Test(expectedExceptions={IllegalArgumentException.class})
    public void testError2() { v.isInRange("[10.5"); }
    @Test(expectedExceptions={IllegalArgumentException.class})
    public void testError3() { v.isInRange("[10.5]"); }

}

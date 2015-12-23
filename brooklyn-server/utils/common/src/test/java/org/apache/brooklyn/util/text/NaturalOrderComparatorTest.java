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
package org.apache.brooklyn.util.text;

import org.apache.brooklyn.util.text.ComparableVersion;
import org.apache.brooklyn.util.text.NaturalOrderComparator;
import org.testng.Assert;
import org.testng.annotations.Test;

public class NaturalOrderComparatorTest {

    public static final NaturalOrderComparator noc = new NaturalOrderComparator();
    
    ComparableVersion v = new ComparableVersion("10.5.8");
    ComparableVersion v_rc2 = new ComparableVersion("10.5.8-rc2");
    
    @Test
    public void testNoc() {
        Assert.assertEquals(noc.compare("0a", "1"), -1);
        
        Assert.assertEquals(noc.compare("0", "1"), -1);
        Assert.assertEquals(noc.compare("1", "10"), -1);
        Assert.assertEquals(noc.compare("9", "10"), -1);
        Assert.assertEquals(noc.compare("a", "b"), -1);
        Assert.assertEquals(noc.compare("a9", "a10"), -1);
        
        Assert.assertEquals(noc.compare("0.9", "0.91"), -1);
        Assert.assertEquals(noc.compare("0.90", "0.91"), -1);
        Assert.assertEquals(noc.compare("1.2.x", "1.09.x"), -1);
        
        Assert.assertEquals(noc.compare("0", "1a"), -1);
        Assert.assertEquals(noc.compare("0a", "1"), -1);
    }
    
    @Test
    public void testBasicOnes() {
        Assert.assertEquals(0, noc.compare("a", "a"));
        Assert.assertTrue(noc.compare("a", "b") < 0);
        Assert.assertTrue(noc.compare("b", "a") > 0);
        
        Assert.assertTrue(noc.compare("9", "10") < 0);
        Assert.assertTrue(noc.compare("10", "9") > 0);
        
        Assert.assertTrue(noc.compare("b10", "a9") > 0);
        Assert.assertTrue(noc.compare("b9", "a10") > 0);
        
        Assert.assertTrue(noc.compare(" 9", "10") < 0);
        Assert.assertTrue(noc.compare("10", " 9") > 0);
    }

    @Test
    public void testVersionNumbers() {
        Assert.assertEquals(0, noc.compare("10.5.8", "10.5.8"));
        Assert.assertTrue(noc.compare("10.5", "9.9") > 0);
        Assert.assertTrue(noc.compare("10.5.1", "10.5") > 0);
        Assert.assertTrue(noc.compare("10.5.1", "10.6") < 0);
        Assert.assertTrue(noc.compare("10.5.1-1", "10.5.1-0") > 0);
    }

    @Test(groups="WIP", enabled=false)
    public void testUnderscoreDoesNotChangeMeaningOfNumberInNoc() {
        // why??
        Assert.assertTrue(noc.compare("0.0.0_SNAPSHOT", "0.0.1-SNAPSHOT-20141111114709760") < 0);

        Assert.assertTrue(v.isGreaterThanAndNotEqualTo(v_rc2.version));
        Assert.assertTrue(v_rc2.isLessThanAndNotEqualTo(v.version));
    }
    
    @Test(groups="WIP", enabled=false)
    public void testUnderscoreDoesNotChangeMeaningOfNumberInOurWorld() {
        Assert.assertTrue(new ComparableVersion("0.0.0_SNAPSHOT").isLessThanAndNotEqualTo("0.0.1-SNAPSHOT-20141111114709760"));
    }

}
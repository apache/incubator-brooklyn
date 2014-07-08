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

public class ComparableVersionTest {

    public static final NaturalOrderComparator noc = new NaturalOrderComparator();
    
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

}

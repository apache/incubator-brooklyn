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
package brooklyn.util.collections;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.collections.QuorumCheck.QuorumChecks;

public class QuorumChecksTest {

    @Test
    public void testAll() {
        QuorumCheck q = QuorumChecks.all();
        Assert.assertTrue(q.isQuorate(2, 2));
        Assert.assertFalse(q.isQuorate(1, 2));
        Assert.assertTrue(q.isQuorate(0, 0));
    }
    
    @Test
    public void testAlwaysTrue() {
        QuorumCheck q = QuorumChecks.alwaysTrue();
        Assert.assertTrue(q.isQuorate(0, 2));
        Assert.assertTrue(q.isQuorate(1, 2));
        Assert.assertTrue(q.isQuorate(0, 0));
    }
    
    @Test
    public void testAtLeastOne() {
        QuorumCheck q = QuorumChecks.atLeastOne();
        Assert.assertTrue(q.isQuorate(2, 2));
        Assert.assertTrue(q.isQuorate(1, 2));
        Assert.assertFalse(q.isQuorate(0, 0));
    }
    
    @Test
    public void testAllAndAtLeastOne() {
        QuorumCheck q = QuorumChecks.atLeastOne();
        Assert.assertFalse(q.isQuorate(0, 2));
        Assert.assertTrue(q.isQuorate(1, 2));
        Assert.assertFalse(q.isQuorate(0, 0));
    }
    
    @Test
    public void testAtLeastOneUnlessEmpty() {
        QuorumCheck q = QuorumChecks.atLeastOneUnlessEmpty();
        Assert.assertFalse(q.isQuorate(0, 2));
        Assert.assertTrue(q.isQuorate(1, 2));
        Assert.assertTrue(q.isQuorate(0, 0));
    }
    
    @Test
    public void testAtLeastOneUnlessEmptyString() {
        QuorumCheck q = QuorumChecks.of("atLeastOneUnlessEmpty");
        Assert.assertFalse(q.isQuorate(0, 2));
        Assert.assertTrue(q.isQuorate(1, 2));
        Assert.assertTrue(q.isQuorate(0, 0));
    }
    
    @Test
    public void testLinearTwoPointsNeedMinTwo() {
        QuorumCheck q = QuorumChecks.of("[ [0,2], [1,2] ]");
        Assert.assertTrue(q.isQuorate(2, 2));
        Assert.assertTrue(q.isQuorate(2, 10));
        Assert.assertFalse(q.isQuorate(1, 1));
    }
    
    @Test
    public void testLinearNeedHalfToTenAndTenPercentAtHundred() {
        QuorumCheck q = QuorumChecks.of("[ [0,0], [10,5], [100,10], [200, 20] ]");
        Assert.assertTrue(q.isQuorate(2, 2));
        Assert.assertTrue(q.isQuorate(1, 2));
        Assert.assertTrue(q.isQuorate(0, 0));
        Assert.assertFalse(q.isQuorate(1, 10));
        Assert.assertTrue(q.isQuorate(6, 10));
        Assert.assertFalse(q.isQuorate(7, 50));
        Assert.assertTrue(q.isQuorate(8, 50));
        Assert.assertFalse(q.isQuorate(9, 100));
        Assert.assertTrue(q.isQuorate(11, 100));
        Assert.assertFalse(q.isQuorate(19, 200));
        Assert.assertTrue(q.isQuorate(21, 200));
        Assert.assertFalse(q.isQuorate(29, 300));
        Assert.assertTrue(q.isQuorate(31, 300));
    }
    
    
    
    
}

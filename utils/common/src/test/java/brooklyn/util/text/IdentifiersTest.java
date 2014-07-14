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

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class IdentifiersTest {

    private static final Logger log = LoggerFactory.getLogger(IdentifiersTest.class);
    
    @Test
    public void testRandomId() {
        String id1 = Identifiers.makeRandomId(4);
        Assert.assertEquals(id1.length(), 4);
        String id2 = Identifiers.makeRandomId(4);
        Assert.assertNotEquals(id1, id2);
    }

    //The test is disabled since it takes a while to finish
    //and there is still the possibility that it could fail.
    @Test(enabled = false)
    public void testRandomIdRandomness() {
        int collisionTestCnt = 100000;
        int sampleSize = 2000;
        
        int duplicateTests = 0;
        for(int testIdx = 0; testIdx < collisionTestCnt; testIdx++) {
            Set<String> randomIds = new HashSet<String>();
            for (int sampleCnt = 0; sampleCnt < sampleSize; sampleCnt++) {
                if(!randomIds.add(Identifiers.makeRandomId(4))) {
                    duplicateTests++;
                    break;
                }
            }
        }
        double probability = ((double)duplicateTests)*100/collisionTestCnt;
        log.info("testRandomIdRandomness probability = " + probability);
        Assert.assertEquals(probability, 15, 0.5d, "Expected probability of collision with id of length 4 and 2000 samples is 15%.");
    }

    @Test
    public void testFromHash() {
        String id1 = Identifiers.makeIdFromHash("Hello".hashCode());
        Assert.assertTrue(!Strings.isBlank(id1));
        
        String id2 = Identifiers.makeIdFromHash("hello".hashCode());
        String id3 = Identifiers.makeIdFromHash("hello".hashCode());
        Assert.assertEquals(id2, id3);
        Assert.assertNotEquals(id1, id2);

        Assert.assertEquals(Identifiers.makeIdFromHash(0), "A");
        
        String idLong = Identifiers.makeIdFromHash(Long.MAX_VALUE);
        log.info("ID's made from hash, of 'hello' is "+id1+" and of Long.MAX_VALUE is "+idLong);
        Assert.assertTrue(idLong.length() > id1.length());
    }

    @Test
    public void testFromNegativeHash() {
        String id1 = Identifiers.makeIdFromHash(-1);
        Assert.assertTrue(!Strings.isBlank(id1));
        log.info("ID's made from hash, of -1 is "+id1+" and of Long.MIN_VALUE is "+Identifiers.makeIdFromHash(Long.MIN_VALUE));
    }

}

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
package brooklyn.entity.nosql.cassandra;

import static org.testng.Assert.assertEquals;

import java.math.BigInteger;
import java.util.List;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.nosql.cassandra.TokenGenerators.AbstractTokenGenerator;
import brooklyn.entity.nosql.cassandra.TokenGenerators.NonNeg127TokenGenerator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class NonNegTokenGeneratorTest {

    public static final BigInteger C4_1 = new BigInteger("42535295865117307932921825928971026432");
    public static final BigInteger C4_2 = new BigInteger("85070591730234615865843651857942052864");
    public static final BigInteger C4_3 = new BigInteger("127605887595351923798765477786913079296");

    // TODO Expect this behaviour to change when we better support dynamically growing/shrinking.
    // In particular, the expected behaviour for testReturnsNullWhenClusterSizeUnknown 
    // and testReturnsNullWhenGrowingClusterUnknownAmount will change.

    private AbstractTokenGenerator generator;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        generator = new NonNeg127TokenGenerator();
    }
    
    @Test
    public void testGetTokenForReplacementNode() {
        assertEquals(generator.getTokenForReplacementNode(BigInteger.ONE), BigInteger.ZERO);
        assertEquals(generator.getTokenForReplacementNode(BigInteger.ZERO), generator.max());
        assertEquals(generator.getTokenForReplacementNode(generator.max()), generator.max().subtract(BigInteger.ONE));
    }
    
    @Test
    public void testGeneratesInitialTokens() throws Exception {
        List<BigInteger> tokens = Lists.newArrayList();
        generator.growingCluster(4);
        for (int i = 0; i < 4; i++) {
            tokens.add(generator.newToken());
        }
        
        assertEquals(tokens, ImmutableList.of(
                BigInteger.ZERO, 
                C4_1,
                C4_2,
                C4_3));
    }
    
    // Expect behaviour to be changed to better choose tokens for growing clusters 
    // (but eg need to take into account how busy each node is!)
    @Test
    public void testGeneratesTokensForGrowingCluster() throws Exception {
        List<BigInteger> tokens = Lists.newArrayList();
        generator.growingCluster(4);
        for (int i = 0; i < 4; i++) {
            tokens.add(generator.newToken());
        }
        generator.growingCluster(1);
        assertEquals(generator.newToken(), C4_3.add(generator.max().add(BigInteger.ONE)).divide(BigInteger.valueOf(2)));
        generator.growingCluster(2);
        assertEquals(generator.newToken(), C4_1.divide(BigInteger.valueOf(2)));
        assertEquals(generator.newToken(), C4_2.add(C4_1).divide(BigInteger.valueOf(2)));
    }
    
    @Test
    public void testGeneratesTokensForGrowingClusterWhenInitialSizeIsOne() throws Exception {
        // initial size 1 has to do a special "average with ourself by half phase shift" computation
        List<BigInteger> tokens = Lists.newArrayList();
        generator.growingCluster(1);
        tokens.add(generator.newToken());
        
        generator.growingCluster(1);
        assertEquals(generator.newToken(), C4_2);
        generator.growingCluster(2);
        assertEquals(generator.newToken(), C4_3);
        assertEquals(generator.newToken(), C4_1);
    }
    
    @Test
    public void testReturnsNullWhenClusterSizeUnknown() throws Exception {
        assertEquals(generator.newToken(), null);
    }
    
    @Test
    public void testReturnsNullWhenGrowingClusterUnknownAmount() throws Exception {
        generator.growingCluster(4);
        for (int i = 0; i < 4; i++) {
            generator.newToken();
        }
        assertEquals(generator.newToken(), null);
    }
}

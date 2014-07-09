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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.nosql.cassandra.TokenGenerators.AbstractTokenGenerator;
import brooklyn.entity.nosql.cassandra.TokenGenerators.PosNeg63TokenGenerator;

public class PosNegTokenGeneratorTest {

    // TODO Expect this behaviour to change when we better support dynamically growing/shrinking.
    // In particular, the expected behaviour for testReturnsNullWhenClusterSizeUnknown 
    // and testReturnsNullWhenGrowingClusterUnknownAmount will change.

    private AbstractTokenGenerator generator;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        generator = new PosNeg63TokenGenerator();
    }
    
    @Test
    public void testGetTokenForReplacementNode() {
        assertEquals(generator.getTokenForReplacementNode(BigInteger.ONE), BigInteger.ZERO);
        assertEquals(generator.getTokenForReplacementNode(BigInteger.ZERO), BigInteger.ONE.negate());
        assertEquals(generator.getTokenForReplacementNode(generator.min()), generator.max());
        assertEquals(generator.getTokenForReplacementNode(generator.max()), generator.max().subtract(BigInteger.ONE));
    }
    
    @Test
    public void testGeneratesInitialTokens() throws Exception {
        generator.growingCluster(4);
        assertEquals(generator.newToken(), generator.min());
        assertEquals(generator.newToken(), generator.min().add(generator.range().divide(BigInteger.valueOf(4))));
    }
}

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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import brooklyn.util.collections.MutableList;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class TokenGenerators {

    /**
     * Sub-classes are recommended to call {@link #checkRangeValid()} at construction time.
     */
    public static abstract class AbstractTokenGenerator implements TokenGenerator, Serializable {
        
        private static final long serialVersionUID = -1884526356161711176L;
        
        public static final BigInteger TWO = BigInteger.valueOf(2);
        
        public abstract BigInteger max();
        public abstract BigInteger min();
        public abstract BigInteger range();

        private final Set<BigInteger> currentTokens = Sets.newTreeSet();
        private final List<BigInteger> nextTokens = Lists.newArrayList();
        private BigInteger origin = BigInteger.ZERO;
        
        protected void checkRangeValid() {
            Preconditions.checkState(range().equals(max().subtract(min()).add(BigInteger.ONE)), 
                    "min=%s; max=%s; range=%s", min(), max(), range());
        }
        
        @Override
        public void setOrigin(BigInteger shift) {
            this.origin = Preconditions.checkNotNull(shift, "shift");
        }
        
        /**
         * Unless we're explicitly starting a new cluster or resizing by a pre-defined number of nodes, then
         * let Cassandra decide (i.e. return null).
         */
        @Override
        public synchronized BigInteger newToken() {
            BigInteger result = (nextTokens.isEmpty()) ? null : nextTokens.remove(0);
            if (result != null) currentTokens.add(result);
            return result;
        }

        @Override
        public synchronized BigInteger getTokenForReplacementNode(BigInteger oldToken) {
            checkNotNull(oldToken, "oldToken");
            return normalize(oldToken.subtract(BigInteger.ONE));
        }

        @Override
        public synchronized void growingCluster(int numNewNodes) {
            if (currentTokens.isEmpty() && nextTokens.isEmpty()) {
                nextTokens.addAll(generateEquidistantTokens(numNewNodes));
            } else {
                // simple strategy which iteratively finds best midpoint
                for (int i=0; i<numNewNodes; i++) {
                    nextTokens.add(generateBestNextToken());
                }
            }
        }

        @Override
        public synchronized void shrinkingCluster(Set<BigInteger> nodesToRemove) {
            currentTokens.remove(nodesToRemove);
        }

        @Override
        public synchronized void refresh(Set<BigInteger> currentNodes) {
            currentTokens.clear();
            currentTokens.addAll(currentNodes);
        }

        private List<BigInteger> generateEquidistantTokens(int numTokens) {
            List<BigInteger> result = Lists.newArrayList();
            for (int i = 0; i < numTokens; i++) {
                BigInteger token = range().multiply(BigInteger.valueOf(i)).divide(BigInteger.valueOf(numTokens)).add(min());
                token = normalize(token.add(origin));
                result.add(token);
            }
            return result;
        }
        
        private BigInteger normalize(BigInteger input) {
            while (input.compareTo(min()) < 0)
                input = input.add(range());
            while (input.compareTo(max()) > 0)
                input = input.subtract(range());
            return input;
        }
        
        private BigInteger generateBestNextToken() {
            List<BigInteger> allTokens = MutableList.<BigInteger>of().appendAll(currentTokens).appendAll(nextTokens);
            Collections.sort(allTokens);
            Iterator<BigInteger> ti = allTokens.iterator();
            
            BigInteger thisValue = ti.next();
            BigInteger prevValue = allTokens.get(allTokens.size()-1).subtract(range());
            
            BigInteger bestNewTokenSoFar = normalize(prevValue.add(thisValue).divide(TWO));
            BigInteger biggestRangeSizeSoFar = thisValue.subtract(prevValue);
            
            while (ti.hasNext()) {
                prevValue = thisValue;
                thisValue = ti.next();
                
                BigInteger rangeHere = thisValue.subtract(prevValue);
                if (rangeHere.compareTo(biggestRangeSizeSoFar) > 0) {
                    bestNewTokenSoFar = prevValue.add(thisValue).divide(TWO);
                    biggestRangeSizeSoFar = rangeHere;
                }
            }
            return bestNewTokenSoFar;
        }

    }

    public static class PosNeg63TokenGenerator extends AbstractTokenGenerator {
        private static final long serialVersionUID = 7327403957176106754L;
        
        public static final BigInteger MIN_TOKEN = TWO.pow(63).negate();
        public static final BigInteger MAX_TOKEN = TWO.pow(63).subtract(BigInteger.ONE);
        public static final BigInteger RANGE = TWO.pow(64);

        public PosNeg63TokenGenerator() {
            checkRangeValid();
        }

        @Override public BigInteger max() { return MAX_TOKEN; }
        @Override public BigInteger min() { return MIN_TOKEN; }
        @Override public BigInteger range() { return RANGE; }
    }
    
    /** token generator used by cassandra pre v1.2 */
    public static class NonNeg127TokenGenerator extends AbstractTokenGenerator {
        private static final long serialVersionUID = 1357426905711548198L;
        
        public static final BigInteger MIN_TOKEN = BigInteger.ZERO;
        public static final BigInteger MAX_TOKEN = TWO.pow(127).subtract(BigInteger.ONE);
        public static final BigInteger RANGE = TWO.pow(127);

        public NonNeg127TokenGenerator() {
            checkRangeValid();
        }
        
        @Override public BigInteger max() { return MAX_TOKEN; }
        @Override public BigInteger min() { return MIN_TOKEN; }
        @Override public BigInteger range() { return RANGE; }
    }
    
}

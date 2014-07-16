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

import java.math.BigInteger;
import java.util.Set;

public interface TokenGenerator {

    BigInteger max();
    BigInteger min();
    BigInteger range();

    void setOrigin(BigInteger shift);
    
    BigInteger newToken();
    
    BigInteger getTokenForReplacementNode(BigInteger oldToken);
    
    Set<BigInteger> getTokensForReplacementNode(Set<BigInteger> oldTokens);
    
    /**
     * Indicates that we are starting a new cluster of the given number of nodes,
     * so expect that number of consecutive calls to {@link #newToken()}.
     * 
     * @param numNewNodes
     */
    void growingCluster(int numNewNodes);

    void shrinkingCluster(Set<BigInteger> nodesToRemove);
    
    void refresh(Set<BigInteger> currentNodes);
}

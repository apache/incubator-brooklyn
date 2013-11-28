package brooklyn.entity.nosql.cassandra;

import java.math.BigInteger;
import java.util.Set;

public interface TokenGenerator {

    BigInteger newToken();
    
    BigInteger getTokenForReplacementNode(BigInteger oldToken);
    
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

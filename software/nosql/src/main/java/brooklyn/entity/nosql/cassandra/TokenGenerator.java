package brooklyn.entity.nosql.cassandra;

import java.math.BigInteger;
import java.util.Set;

public interface TokenGenerator {

    public static final BigInteger MIN_TOKEN = BigInteger.ZERO;
    public static final BigInteger MAX_TOKEN = BigInteger.valueOf(2).pow(127).subtract(BigInteger.ONE);

    public static final TokenGenerator NOOP = new TokenGenerator() {
        @Override public BigInteger newToken() {
            return null;
        }
        @Override public BigInteger getTokenForReplacementNode(BigInteger oldToken) {
            return (oldToken.longValue() == 0) ? MAX_TOKEN : oldToken.subtract(BigInteger.ONE);
        }
        @Override public void growingCluster(int numNewNodes) {
        }
        @Override public void shrinkingCluster(Set<BigInteger> nodesToRemove) {
        }
        @Override public void refresh(Set<BigInteger> currentNodes) {
        }
    };
    
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

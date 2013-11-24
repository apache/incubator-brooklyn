package brooklyn.entity.nosql.cassandra;

import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class BasicTokenGenerator implements TokenGenerator {

    private final Set<BigInteger> currentTokens = Sets.newTreeSet();
    
    private final List<BigInteger> nextTokens = Lists.newArrayList();
    
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
        return (oldToken.longValue() == 0) ? MAX_TOKEN : oldToken.subtract(BigInteger.ONE);
    }

    @Override
    public synchronized void growingCluster(int numNewNodes) {
        if (currentTokens.isEmpty()) {
            nextTokens.addAll(generateEquidistantTokens(numNewNodes));
        } else {
            // TODO add the new tokens amongst the existing nodes?
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
            BigInteger token = MAX_TOKEN.divide(BigInteger.valueOf(numTokens)).multiply(BigInteger.valueOf(i));
            result.add(token);
        }
        return result;
    }
}

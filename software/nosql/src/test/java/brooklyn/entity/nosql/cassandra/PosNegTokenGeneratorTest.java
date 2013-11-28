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

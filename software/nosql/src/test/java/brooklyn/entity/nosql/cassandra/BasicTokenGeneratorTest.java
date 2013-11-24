package brooklyn.entity.nosql.cassandra;

import static org.testng.Assert.assertEquals;

import java.math.BigInteger;
import java.util.List;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class BasicTokenGeneratorTest {

    // TODO Expect this behaviour to change when we better support dynamically growing/shrinking.
    // In particular, the expected behaviour for testReturnsNullWhenClusterSizeUnknown 
    // and testReturnsNullWhenGrowingClusterUnknownAmount will change.
    // Also, we're not 

    private BasicTokenGenerator generator;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        generator = new BasicTokenGenerator();
    }
    
    @Test
    public void testGetTokenForReplacementNode() {
        assertEquals(generator.getTokenForReplacementNode(BigInteger.ONE), BigInteger.ZERO);
        assertEquals(generator.getTokenForReplacementNode(BigInteger.ZERO), TokenGenerator.MAX_TOKEN);
        assertEquals(generator.getTokenForReplacementNode(TokenGenerator.MAX_TOKEN), TokenGenerator.MAX_TOKEN.subtract(BigInteger.ONE));
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
                new BigInteger("42535295865117307932921825928971026431"),
                new BigInteger("85070591730234615865843651857942052862"),
                new BigInteger("127605887595351923798765477786913079293")));
    }
    
    // TODO Not currently implemented, so returns null; but confirming it doesn't
    // give error or duplicate token. Expect behaviour to be changed to better choose 
    // tokens for growing clusters (but then need to take into account how busy each 
    // node is?)
    @Test
    public void testGeneratesTokensForGrowingCluster() throws Exception {
        List<BigInteger> tokens = Lists.newArrayList();
        generator.growingCluster(4);
        for (int i = 0; i < 4; i++) {
            tokens.add(generator.newToken());
        }
        generator.growingCluster(4);
        for (int i = 0; i < (4+1); i++) {
            assertEquals(generator.newToken(), null);
        }
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

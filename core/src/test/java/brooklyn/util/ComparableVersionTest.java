package brooklyn.util;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.NaturalOrderComparator;

public class ComparableVersionTest {

    public static final NaturalOrderComparator noc = new NaturalOrderComparator();
    
    @Test
    public void testBasicOnes() {
        Assert.assertEquals(0, noc.compare("a", "a"));
        Assert.assertTrue(noc.compare("a", "b") < 0);
        Assert.assertTrue(noc.compare("b", "a") > 0);
        
        Assert.assertTrue(noc.compare("9", "10") < 0);
        Assert.assertTrue(noc.compare("10", "9") > 0);
        
        Assert.assertTrue(noc.compare("b10", "a9") > 0);
        Assert.assertTrue(noc.compare("b9", "a10") > 0);
        
        Assert.assertTrue(noc.compare(" 9", "10") < 0);
        Assert.assertTrue(noc.compare("10", " 9") > 0);
    }

    @Test
    public void testVersionNumbers() {
        Assert.assertEquals(0, noc.compare("10.5.8", "10.5.8"));
        Assert.assertTrue(noc.compare("10.5", "9.9") > 0);
        Assert.assertTrue(noc.compare("10.5.1", "10.5") > 0);
        Assert.assertTrue(noc.compare("10.5.1", "10.6") < 0);
        Assert.assertTrue(noc.compare("10.5.1-1", "10.5.1-0") > 0);
    }

}

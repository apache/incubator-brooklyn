package brooklyn.util;

import org.testng.Assert;
import org.testng.annotations.Test;

public class NaturalOrderComparatorTest {

    public static final NaturalOrderComparator noc = new NaturalOrderComparator();
    
    ComparableVersion v = new ComparableVersion("10.5.8");
    
    @Test
    public void testBasicOnes() {
        Assert.assertTrue(v.isGreaterThanAndNotEqualTo("10.5"));
        Assert.assertTrue(v.isGreaterThanOrEqualTo("10.5.8"));
        Assert.assertFalse(v.isGreaterThanAndNotEqualTo("10.5.8"));

        Assert.assertTrue(v.isLessThanAndNotEqualTo("10.6"));
        Assert.assertTrue(v.isLessThanOrEqualTo("10.5.8"));
        Assert.assertFalse(v.isLessThanAndNotEqualTo("10.5.8"));

        Assert.assertTrue(v.isInRange("[10.5,10.6)"));
        Assert.assertFalse(v.isInRange("[10.5,10.5.8)"));
        Assert.assertTrue(v.isInRange("[10.5,)"));
        Assert.assertTrue(v.isInRange("[9,)"));
        Assert.assertFalse(v.isInRange("(10.5.8,)"));
        Assert.assertFalse(v.isInRange("[10.6,)"));
        Assert.assertTrue(v.isInRange("[,11)"));
        Assert.assertTrue(v.isInRange("[,]"));
    }

    @Test(expectedExceptions={IllegalArgumentException.class})
    public void testError1() { v.isInRange("10.5"); }
    @Test(expectedExceptions={IllegalArgumentException.class})
    public void testError2() { v.isInRange("[10.5"); }
    @Test(expectedExceptions={IllegalArgumentException.class})
    public void testError3() { v.isInRange("[10.5]"); }

}

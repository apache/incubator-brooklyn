package brooklyn.util.math;

import org.testng.Assert;
import org.testng.annotations.Test;

public class MathFunctionsTest {

    @Test
    public void testAdd() {
        Assert.assertEquals(MathFunctions.plus(3).apply(4), (Integer)7);
        Assert.assertEquals(MathFunctions.plus(0.3).apply(0.4).doubleValue(), 0.7, 0.00000001);
    }
    
    @Test
    public void testPercent() {
        Assert.assertEquals(MathFunctions.percent(3).apply(0.0123456), "1.23%");
    }
}

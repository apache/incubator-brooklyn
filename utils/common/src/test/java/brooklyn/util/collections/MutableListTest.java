package brooklyn.util.collections;

import java.util.Arrays;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class MutableListTest {

    public void testEqualsExact() {
        List<Object> a = MutableList.<Object>of("a", 1, "b", false);
        List<Object> b = MutableList.<Object>of("a", 1, "b", false);
        Assert.assertEquals(a, b);
    }
    
    public void testNotEqualsUnordered() {
        List<Object> a = MutableList.<Object>of("a", 1, "b", false);
        List<Object> b = MutableList.<Object>of("b", false, "a", 1);
        Assert.assertNotEquals(a, b);
    }

    public void testEqualsDifferentTypes() {
        List<Object> a = MutableList.<Object>of("a", 1, "b", false);
        List<Object> b = Arrays.<Object>asList("a", 1, "b", false);
        Assert.assertEquals(a, b);
        Assert.assertEquals(b, a);
    }

    public void testEqualsDifferentTypes2() {
        List<Object> a = MutableList.<Object>of("http");
        List<String> b = Arrays.<String>asList("http");
        Assert.assertEquals(a, b);
        Assert.assertEquals(b, a);
    }

}

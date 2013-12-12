package brooklyn.util.collections;

import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

@Test
public class MutableMapTest {

    public void testEqualsExact() {
        Map<Object,Object> a = MutableMap.<Object,Object>of("a", 1, "b", false);
        Map<Object,Object> b = MutableMap.<Object,Object>of("a", 1, "b", false);
        Assert.assertEquals(a, b);
    }
    
    public void testEqualsUnordered() {
        Map<Object,Object> a = MutableMap.<Object,Object>of("a", 1, "b", false);
        Map<Object,Object> b = MutableMap.<Object,Object>of("b", false, "a", 1);
        Assert.assertEquals(a, b);
    }

    public void testEqualsDifferentTypes() {
        Map<Object,Object> a = MutableMap.<Object,Object>of("a", 1, "b", false);
        Map<Object,Object> b = ImmutableMap.<Object,Object>of("b", false, "a", 1);
        Assert.assertEquals(a, b);
    }

}

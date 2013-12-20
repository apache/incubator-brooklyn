package brooklyn.util.collections;

import java.util.ArrayList;
import java.util.Arrays;
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

    public void testListOfMaps() {
        MutableMap<Object, Object> map = MutableMap.<Object,Object>of("a", 1, 2, Arrays.<Object>asList(true, "8"));
        ArrayList<Object> l = new ArrayList<Object>();
        l.add(true); l.add("8");
        MutableMap<Object, Object> map2 = MutableMap.<Object,Object>of(2, l, "a", 1);
        Assert.assertEquals(map, map2);
    }
    
}

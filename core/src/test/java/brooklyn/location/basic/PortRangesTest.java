package brooklyn.location.basic;

import java.util.Iterator;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import brooklyn.location.PortRange;
import brooklyn.util.flags.TypeCoercions;

public class PortRangesTest {

    @Test
    public void testSingleRange() {
        PortRange r = PortRanges.fromInteger(1234);
        assertContents(r, 1234);
    }

    @Test
    public void testFromCollection() {
        PortRange r = PortRanges.fromCollection(ImmutableList.of(1234, 2345));
        assertContents(r, 1234, 2345);
    }

    @Test
    public void testFromString() {
        PortRange r = PortRanges.fromString("80,8080,8000,8080-8099");
        assertContents(r, 80, 8080, 8000, 
                8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8089,
                8090, 8091, 8092, 8093, 8094, 8095, 8096, 8097, 8098, 8099);
    }

    @Test
    public void testFromStringWithSpaces() {
        PortRange r = PortRanges.fromString(" 80 , 8080  , 8000 , 8080  - 8099 ");
        assertContents(r, 80, 8080, 8000, 
                8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8089,
                8090, 8091, 8092, 8093, 8094, 8095, 8096, 8097, 8098, 8099);
    }

    @Test
    public void testCoercion() {
        PortRanges.init();
        PortRange r = TypeCoercions.coerce("80", PortRange.class);
        assertContents(r, 80);
    }

    private static <T> void assertContents(Iterable<T> actual, T ...expected) {
        Iterator<T> i = actual.iterator();
        int c = 0;
        while (i.hasNext()) {
            if (expected.length<=c) {
                Assert.fail("Iterable contained more than the "+c+" expected elements");
            }
            Assert.assertEquals(i.next(), expected[c++]);
        }
        if (expected.length>c) {
            Assert.fail("Iterable contained only "+c+" elements, "+expected.length+" expected");
        }
    }
}

package brooklyn.util;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class StringUtilsTest {

    public void testRemovePrefix() {
        Assert.assertEquals(StringUtils.removeStart("xyz", "x"), "yz");
        Assert.assertEquals(StringUtils.removeStart("xyz", "."), "xyz");
        Assert.assertEquals(StringUtils.removeStart("http://foo.com", "http://"), "foo.com");
    }
    
    public void testRemoveSuffix() {
        Assert.assertEquals(StringUtils.removeEnd("xyz", "z"), "xy");
        Assert.assertEquals(StringUtils.removeEnd("xyz", "."), "xyz");
        Assert.assertEquals(StringUtils.removeEnd("http://foo.com/", "/"), "http://foo.com");
    }

    public void testReplaceAllNonRegex() {
        Assert.assertEquals(StringUtils.replace("xyz", "x", ""), "yz");
        Assert.assertEquals(StringUtils.replace("xyz", ".", ""), "xyz");
        Assert.assertEquals(StringUtils.replace("http://foo.com/", "/", ""), "http:foo.com");
        Assert.assertEquals(StringUtils.replace("http://foo.com/", "http:", "https:"), "https://foo.com/");
    }

}

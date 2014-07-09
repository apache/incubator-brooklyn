package brooklyn.util.text;

import org.testng.Assert;
import org.testng.annotations.Test;

public class StringShortenerTest {

    @Test
    public void testSimpleShortener() {
        StringShortener ss = new StringShortener()
            .separator("-")
            .append("1", "hello")
            .append("2", "new")
            .append("3", "world")
            .canRemove("2")
            .canTruncate("1", 2)
            .canTruncate("3", 2);
        
        Assert.assertEquals(ss.getStringOfMaxLength(12), "hello-world");
        Assert.assertEquals(ss.getStringOfMaxLength(10), "hell-world");
        Assert.assertEquals(ss.getStringOfMaxLength(6), "he-wor");
        Assert.assertEquals(ss.getStringOfMaxLength(5), "he-wo");
        Assert.assertEquals(ss.getStringOfMaxLength(4), "he-w");
        Assert.assertEquals(ss.getStringOfMaxLength(0), "");
    }

    @Test
    public void testEdgeCases() {
        StringShortener ss = new StringShortener();
        ss.separator(null);
        Assert.assertEquals(ss.getStringOfMaxLength(4), "");
        ss.append("1", "hello");
        Assert.assertEquals(ss.getStringOfMaxLength(8), "hello");
        Assert.assertEquals(ss.getStringOfMaxLength(4), "hell");
        ss.append("2", "world");
        ss.append("3", null);
        Assert.assertEquals(ss.getStringOfMaxLength(15), "helloworld");
        Assert.assertEquals(ss.getStringOfMaxLength(8), "hellowor");
        ss.canTruncate("1", 2);
        Assert.assertEquals(ss.getStringOfMaxLength(8), "helworld");
        Assert.assertEquals(ss.getStringOfMaxLength(5), "hewor");
        Assert.assertEquals(ss.getStringOfMaxLength(2), "he");
        Assert.assertEquals(ss.getStringOfMaxLength(0), "");
    }

}

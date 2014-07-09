package brooklyn.util.text;

import org.testng.Assert;
import org.testng.annotations.Test;

public class StringFunctionsTest {

    @Test
    public static void testPrepend() {
        Assert.assertEquals(StringFunctions.prepend("Hello ").apply("World"), "Hello World");
    }
    
    @Test
    public static void testFormatter() {
        Assert.assertEquals(StringFunctions.formatter("Hello %s").apply("World"), "Hello World");
    }
    
    @Test
    public static void testFormatterForArray() {
        Assert.assertEquals(StringFunctions.formatterForArray("Hello %s").apply(new Object[] { "World" }), "Hello World");
    }
    
    @Test
    public static void testSurround() {
        Assert.assertEquals(StringFunctions.surround("goodbye ", " world").apply("cruel"), "goodbye cruel world");
    }
    
    @Test
    public static void testLowerCase() {
        Assert.assertEquals(StringFunctions.toLowerCase().apply("Hello World"), "hello world");
    }
    
    @Test
    public static void testUpperCase() {
        Assert.assertEquals(StringFunctions.toUpperCase().apply("Hello World"), "HELLO WORLD");
    }
    
}

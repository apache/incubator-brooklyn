package brooklyn.util.net;

import java.util.Arrays;

import junit.framework.Assert;

import org.testng.annotations.Test;

public class CidrTest {

    public static void assertBytesEquals(int[] actual, int[] expected) {
        if (actual.length != expected.length)
            Assert.fail("Arrays of different length: "+Arrays.toString(actual)+" actual, "+Arrays.toString(expected)+" expected");
        for (int i=0; i<actual.length; i++)
            if (actual[i]!=expected[i])
                Assert.fail("Arrays differ in element "+i+": "+Arrays.toString(actual)+" actual, "+Arrays.toString(expected)+" expected");
    }
    
    @Test
    public void test_10_0_0_0_String() {
        Cidr c = new Cidr("10.0.0.0/8");
        Assert.assertEquals(c.toString(), "10.0.0.0/8");
        assertBytesEquals(c.getBytes(), new int[] { 10, 0, 0, 0 });
        Assert.assertEquals(c.getLength(), 8);
    }

    @Test
    public void test_10_0_0_0_Byte() {
        Cidr c = new Cidr(10);
        Assert.assertEquals(c.toString(), "10.0.0.0/8");
        assertBytesEquals(c.getBytes(), new int[] { 10, 0, 0, 0 });
        Assert.assertEquals(c.getLength(), 8);
    }

    @Test
    public void test_10_0_0_0_ExtraBytes() {
        Cidr c = new Cidr(10, 0);
        Assert.assertEquals(c.toString(), "10.0.0.0/16");
        assertBytesEquals(c.getBytes(), new int[] { 10, 0, 0, 0 });
        Assert.assertEquals(c.getLength(), 16);
    }

    @Test
    public void test_0_0_0_0_Bytes() {
        Cidr c = new Cidr();
        Assert.assertEquals(c.toString(), "0.0.0.0/0");
        assertBytesEquals(c.getBytes(), new int[] { 0, 0, 0, 0 });
        Assert.assertEquals(c.getLength(), 0);
    }

    @Test
    public void test_0_0_0_0_32_Bytes() {
        Cidr c = new Cidr(0, 0, 0, 0);
        Assert.assertEquals(c.toString(), "0.0.0.0/32");
        assertBytesEquals(c.getBytes(), new int[] { 0, 0, 0, 0 });
        Assert.assertEquals(c.getLength(), 32);
    }

    @Test
    public void test_0_0_0_0_String() {
        Cidr c = new Cidr("0.0.0.0/0");
        Assert.assertEquals(c.toString(), "0.0.0.0/0");
        assertBytesEquals(c.getBytes(), new int[] { 0, 0, 0, 0 });
        Assert.assertEquals(c.getLength(), 0);
    }

    @Test
    public void test_0_0_0_0_32_String() {
        Cidr c = new Cidr("0.0.0.0/32");
        Assert.assertEquals(c.toString(), "0.0.0.0/32");
        assertBytesEquals(c.getBytes(), new int[] { 0, 0, 0, 0 });
        Assert.assertEquals(c.getLength(), 32);
    }

    @Test
    public void testSubnet() {
        Cidr c = new Cidr("0.0.0.0/0");
        Cidr c10 = c.subnet(10);
        Assert.assertEquals(c10, new Cidr(10));
        Assert.assertEquals(c10.subnet(88, 1), new Cidr(10, 88, 1));
    }
    
    @Test
    public void testNetmask() {
        Cidr c = new Cidr(10, 0);
        Assert.assertEquals(c.netmask().getHostAddress(), "255.255.0.0");
    }

    @Test
    public void testNetmaskOdd() {
        Cidr c = new Cidr("10.0/13");
        Assert.assertEquals(c.netmask().getHostAddress(), "255.31.0.0");
    }

    @Test
    public void testAddressAtOffset() {
        Cidr c = new Cidr(10, 0);
        Assert.assertEquals(c.addressAtOffset(3).getHostAddress(), "10.0.0.3");
        Assert.assertEquals(c.addressAtOffset(256*256*8+1).getHostAddress(), "10.8.0.1");
    }


}

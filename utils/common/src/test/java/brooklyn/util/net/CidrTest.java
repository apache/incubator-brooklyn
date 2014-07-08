/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.util.net;

import java.util.Arrays;

import org.testng.Assert;
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
    public void test_10_0_0_0_7_LessABit() {
        Cidr c = new Cidr("10.0.0.0/7");
        Assert.assertEquals(c.toString(), "10.0.0.0/7");
        assertBytesEquals(c.getBytes(), new int[] { 10, 0, 0, 0 });
        Assert.assertEquals(c.getLength(), 7);
    }

    @Test
    public void test_10_0_0_0_6_LessTwoBitsOneIsSignificant() {
        Cidr c = new Cidr("10.0.0.1/6");
        Assert.assertEquals(c.toString(), "8.0.0.0/6");
        assertBytesEquals(c.getBytes(), new int[] { 8, 0, 0, 0 });
        Assert.assertEquals(c.getLength(), 6);
    }

    @Test
    public void test_10_0_blah_6() {
        Cidr c = new Cidr("10.0../6");
        Assert.assertEquals(c.toString(), "8.0.0.0/6");
        assertBytesEquals(c.getBytes(), new int[] { 8, 0, 0, 0 });
        Assert.assertEquals(c.getLength(), 6);
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

    @Test
    public void testCommonPrefixLength() {
        Cidr c1 = new Cidr("10.0.0.0/8");
        Cidr c2 = new Cidr("11.0.0.0/8");
        Assert.assertEquals(c1.commonPrefixLength(c2), 7);
        Assert.assertEquals(c2.commonPrefixLength(c1), 7);
        Assert.assertEquals(c2.commonPrefix(c1), c1.commonPrefix(c2));
        Assert.assertEquals(c2.commonPrefix(c1), new Cidr("10.0../7"));
        Cidr c1s = new Cidr("10.0../6");
        Assert.assertEquals(c2.commonPrefixLength(c1s), 6);
        
        Assert.assertTrue(c1s.contains(c1));
        Assert.assertTrue(c1s.contains(c2));
        Assert.assertFalse(c1.contains(c2));
        Assert.assertFalse(c2.contains(c1));
    }

    @Test
    public void testContains() {
        Assert.assertTrue(Cidr._172_16.contains(new Cidr("172.17.0.1/32")));
        Assert.assertFalse(Cidr._172_16.contains(new Cidr("172.144.0.1/32")));
    }
    
    @Test
    public void testIsCanonical() {
        Assert.assertTrue(Cidr.isCanonical("10.0.0.0/8"));
        Assert.assertTrue(Cidr.isCanonical("10.0.0.0/16"));
        Assert.assertTrue(Cidr.isCanonical(Cidr._172_16.toString()));
        Assert.assertFalse(Cidr.isCanonical("10.0.0.1/8"));
        Assert.assertFalse(Cidr.isCanonical("/0"));
        Assert.assertFalse(Cidr.isCanonical("10.0.0.0/33"));
    }
}

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
package brooklyn.util.math;

import java.math.BigInteger;
import java.util.BitSet;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.primitives.Booleans;

public class BitListTest {

    @Test
    public void checkBitListToNumber() {
        BitList bl;
        bl = BitList.newInstance(true);
        Assert.assertEquals(bl.asBytes(), new byte[] { 1 });
        Assert.assertEquals(bl.asBigInteger(), BigInteger.valueOf(1));
        
        Assert.assertEquals(BitList.newInstance(new boolean[0]).asBigInteger(), BigInteger.valueOf(0));
        
        bl = BitList.newInstance(false, false, false, false, 
                                 false, false, false, false, 
                                 true, false);
        Assert.assertEquals(bl.asBytes(), new byte[] { 0, 1 });
        Assert.assertEquals(bl.intValue(), 256); 
        Assert.assertEquals(bl.longValue(), 256); 
        Assert.assertEquals(bl.byteValue(), 0); 
        
        Assert.assertEquals(BitList.newInstanceFromBytes(0, 1).resized(10), bl);
        
        Assert.assertEquals(""+bl, "00000000:10");
    }
    
    @Test
    public void checkSimpleBitsToBytesAndBack() {
        BitSet bs = new BitSet();
        bs.set(0); bs.set(2);   //5
        bs.set(8); bs.set(15);  //129 (unsigned)
        bs.set(17);    // 2
        byte[] bytes = BitList.newInstance(bs, 24).asBytes();
        
        Assert.assertEquals(bytes.length, 3);
        Assert.assertEquals(bytes[0], (byte)5);
        Assert.assertEquals(bytes[1], (byte)129);
        Assert.assertEquals(bytes[2], (byte)2);
        
        BitList bs2 = BitList.newInstance(bytes);
        Assert.assertEquals(bs2.asBitSet(), bs);
    }

    @Test
    public void checkBitsToUnsignedBytesAndBack() {
        BitSet bs = new BitSet();
        bs.set(0); bs.set(2);   //5
        bs.set(8); bs.set(15);  //129 (unsigned)
        bs.set(17);    // 2
        int[] bytes = BitList.newInstance(bs, 24).asUnsignedBytes();
        
        Assert.assertEquals(bytes.length, 3);
        Assert.assertEquals(bytes[0], 5);
        Assert.assertEquals(bytes[1], 129);
        Assert.assertEquals(bytes[2], 2);
        
        BitList bs2 = BitList.newInstanceFromBytes(bytes);
        Assert.assertEquals(bs2.asBitSet(), bs);
    }

    @Test
    public void checkAsList() {
        BitList bl = BitList.newInstanceFromBytes(2, 129, 5);
        Assert.assertEquals(BitList.newInstance(bl.asBitSet(), bl.length), bl);
        Assert.assertEquals(BitList.newInstance(bl.asBytes()), bl);
        Assert.assertEquals(BitList.newInstance(bl.asArray()), bl);
        Assert.assertEquals(BitList.newInstance(bl.asList()), bl);
        Assert.assertEquals(BitList.newInstance(bl.asBigInteger()).resized(24), bl);
    }

    @Test
    public void checkReverseTiny() {
        BitList bl = BitList.newInstance(true, false);
        Assert.assertEquals(Booleans.asList(bl.reversed().asArray()), Booleans.asList(false, true));
        Assert.assertEquals(bl.intValue(), 1);
        Assert.assertEquals(bl.reversed().intValue(), 2);
        Assert.assertEquals(bl.reversed().reversed(), bl);
    }
    
    @Test
    public void checkReverseNumbers() {
        BitList bl = BitList.newInstanceFromBytes(2, 129, 5);
        Assert.assertEquals(bl.reversed().asBytes(), new byte[] { (byte)160, (byte)129, (byte)64  });
        Assert.assertEquals(BitUtils.reverseBitSignificance( bl.reversed().asBytes() ), new byte[] { 5, (byte)129, 2 });
    }

    @Test
    public void checkCommonPrefixLength() {
        Assert.assertEquals(BitList.newInstance(false, true, false).commonPrefixLength(BitList.newInstance(true, false, false)), 0);
        Assert.assertEquals(BitList.newInstance(false, true, false).commonPrefixLength(BitList.newInstance(false, false, false)), 1);
        Assert.assertEquals(BitList.newInstance(false, true, false).commonPrefixLength(BitList.newInstance(false, true, true)), 2);
        Assert.assertEquals(BitList.newInstance(false, true, false).commonPrefixLength(BitList.newInstance(false, true, false)), 3);
    }

}
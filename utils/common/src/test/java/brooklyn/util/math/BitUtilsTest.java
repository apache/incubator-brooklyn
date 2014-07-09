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

import org.testng.Assert;
import org.testng.annotations.Test;

public class BitUtilsTest {

    @Test
    public void checkReverseBitSignificance() {
        Assert.assertEquals(BitUtils.reverseBitSignificanceInByte(1), (byte)128);
        Assert.assertEquals(BitUtils.reverseBitSignificanceInByte(2), 64);
        Assert.assertEquals(BitUtils.reverseBitSignificanceInByte(8), 16);
        Assert.assertEquals(BitUtils.reverseBitSignificanceInByte(16), 8);
        Assert.assertEquals(BitUtils.reverseBitSignificanceInByte(128), 1);
        Assert.assertEquals(BitUtils.reverseBitSignificanceInByte(160), 5);
        Assert.assertEquals(BitUtils.reverseBitSignificanceInByte(-96), 5);
        Assert.assertEquals(BitUtils.reverseBitSignificanceInByte(3), (byte)192);
        Assert.assertEquals(BitUtils.reverseBitSignificanceInBytes(1, 2), new byte[] { (byte)128, 64 });
        Assert.assertEquals(BitUtils.reverseBitSignificanceInBytes(3, 8, 16, 192, 255), new byte[] { (byte)192, 16, 8, 3, (byte)255 });
    }
    
    @Test
    public void checkUnsigned() {
        Assert.assertEquals(BitUtils.unsigned((byte)-96), 160);
        Assert.assertEquals(BitUtils.unsigned((byte)160), 160);
        Assert.assertEquals(BitUtils.unsignedByte(-96), 160);
        Assert.assertEquals(BitUtils.unsignedByte(-96-256-256), 160);
        Assert.assertEquals(BitUtils.unsignedByte(-96+256), 160);
        Assert.assertEquals(BitUtils.unsignedByte(-96+256+256), 160);
    }
}

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
package brooklyn.util.javalang;

import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableList;
import brooklyn.util.guava.Maybe;
import brooklyn.util.text.Strings;

public class MemoryUsageTrackerTest {

    @Test(groups="Integration")
    public void testBigUsage() {
        List<Maybe<byte[]>> references = MutableList.of();
        long created = 0;
        while (created < 2*Runtime.getRuntime().maxMemory()) {
            byte d[] = new byte[1000000];
            references.add(Maybe.soft(d));
            MemoryUsageTracker.SOFT_REFERENCES.track(d, d.length);
            created += d.length;
            
            System.out.println("created "+Strings.makeSizeString(created) +
                " ... in use: "+Strings.makeSizeString(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())+" / " +
                Strings.makeSizeString(Runtime.getRuntime().totalMemory()) +
                " ... reclaimable: "+Strings.makeSizeString(MemoryUsageTracker.SOFT_REFERENCES.getBytesUsed()) +
                " ... live refs: "+Strings.makeSizeString(sizeOfActiveReferences(references)));
            
            assertLessThan(MemoryUsageTracker.SOFT_REFERENCES.getBytesUsed(), Runtime.getRuntime().maxMemory());
            assertLessThan(MemoryUsageTracker.SOFT_REFERENCES.getBytesUsed(), Runtime.getRuntime().totalMemory());
            assertLessThan(MemoryUsageTracker.SOFT_REFERENCES.getBytesUsed(), Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        }
    }

    private long sizeOfActiveReferences(List<Maybe<byte[]>> references) {
        long size = 0;
        for (Maybe<byte[]> ref: references) {
            byte[] deref = ref.orNull();
            if (deref!=null) size += deref.length;
        }
        return size;
    }
    
    private static void assertLessThan(long lhs, long rhs) {
        Assert.assertTrue(lhs<rhs, "Expected "+lhs+" < "+rhs);
    }
    
}

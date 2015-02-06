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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.test.Asserts;
import brooklyn.util.collections.MutableList;
import brooklyn.util.guava.Maybe;
import brooklyn.util.text.Strings;

public class MemoryUsageTrackerTest {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryUsageTrackerTest.class);

    @Test(groups="Integration")
    public void testBigUsage() {
        final int ALLOCATION_CHUNK_SIZE = 10*1000*1000; // 10MB
        
        // Don't just use runtime.maxMemory()*2; javadoc says:
        //     If there is no inherent limit then the value java.lang.Long.MAX_VALUE will be returned.
        // Therefore cap at 10GB.
        final long MAX_MEMORY_CAP = 10*1024*1024*1024L; // 10GB
        final long maxMemory = Math.min(Runtime.getRuntime().maxMemory(), MAX_MEMORY_CAP);
        
        List<Maybe<byte[]>> references = MutableList.of();
        long created = 0;
        while (created < 2*maxMemory) {
            byte d[] = new byte[ALLOCATION_CHUNK_SIZE];
            references.add(Maybe.soft(d));
            MemoryUsageTracker.SOFT_REFERENCES.track(d, d.length);
            created += d.length;
            
            long totalMemory = Runtime.getRuntime().totalMemory();
            long freeMemory = Runtime.getRuntime().freeMemory();
            
            LOG.info("created "+Strings.makeSizeString(created) +
                " ... in use: "+Strings.makeSizeString(totalMemory - freeMemory)+" / " +
                Strings.makeSizeString(totalMemory) +
                " ... reclaimable: "+Strings.makeSizeString(MemoryUsageTracker.SOFT_REFERENCES.getBytesUsed()) +
                " ... live refs: "+Strings.makeSizeString(sizeOfActiveReferences(references)) +
                " ... maxMem="+maxMemory+"; totalMem="+totalMemory+"; usedMem="+(totalMemory-freeMemory));
        }
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                long totalMemory = Runtime.getRuntime().totalMemory();
                long freeMemory = Runtime.getRuntime().freeMemory();
                assertLessThan(MemoryUsageTracker.SOFT_REFERENCES.getBytesUsed(), maxMemory);
                assertLessThan(MemoryUsageTracker.SOFT_REFERENCES.getBytesUsed(), totalMemory);
                assertLessThan(MemoryUsageTracker.SOFT_REFERENCES.getBytesUsed(), totalMemory - freeMemory);
            }});
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

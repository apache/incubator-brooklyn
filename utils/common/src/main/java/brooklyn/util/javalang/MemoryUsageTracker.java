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

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

/** 
 * Tracks the amount of memory consumed by the given objects in use.
 * <p>
 * {@link WeakReference}s are used internally, so that shortly after a {@link #track(Object, long)}ed object is GC'd, 
 * the {@link #getBytesUsed()} value decrements appropriately.
 */
public class MemoryUsageTracker {

    /**
     * Shared instance for use for tracking memory used by {@link SoftReference}.
     * <p>
     * Callers should only use this field to {@link #track(Object, long)} objects which have (or will soon have)
     * given up their strong references, so that only soft or weak references remain.
     * Provided size estimates are accurate, {@link #getBytesUsed()} will report
     * the amount of used memory which is reclaimable by collecting soft references.
     * <p>
     * This is particularly handy for tracking {@link SoftReference}s, because otherwise you can quickly get to a state
     * where {@link Runtime#freeMemory()} looks very low.
     **/
    public static final MemoryUsageTracker SOFT_REFERENCES = new MemoryUsageTracker();
    
    AtomicLong bytesUsed = new AtomicLong(0);
    
    Cache<Object, Long> memoryTrackedReferences = CacheBuilder.newBuilder()
            .weakKeys()
            .removalListener(new RemovalListener<Object,Long>() {
                @Override
                public void onRemoval(RemovalNotification<Object, Long> notification) {
                    bytesUsed.addAndGet(-notification.getValue());
                }
            }).build();
    
    public void track(Object instance, long bytesUsedByInstance) {
        bytesUsed.addAndGet(bytesUsedByInstance);
        memoryTrackedReferences.put(instance, bytesUsedByInstance);
    }
    
    public long getBytesUsed() {
        memoryTrackedReferences.cleanUp();
        return bytesUsed.get();
    }
    
}

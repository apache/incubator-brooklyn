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
package brooklyn.entity.group;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.DynamicGroupImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

public class DynamicMultiGroupImpl extends DynamicGroupImpl implements DynamicMultiGroup {

    /**
     * Convenience factory method for the common use-case of deriving the bucket directly from a sensor value.
     *
     * @see DynamicMultiGroup#BUCKET_FUNCTION
     */
    public static Function<Entity, String> bucketFromAttribute(final AttributeSensor<?> sensor, final String defaultValue) {
        return new Function<Entity, String>() {
            @Override
            public String apply(@Nullable Entity input) {
                Object value = input.getAttribute(sensor);
                if (value == null) {
                    return defaultValue;
                } else {
                    return String.valueOf(value);
                }
            };
        };
    }

    /**
     * Convenience factory method for the common use-case of deriving the bucket directly from a sensor value.
     *
     * @see DynamicMultiGroup#BUCKET_FUNCTION
     */
    public static Function<Entity, String> bucketFromAttribute(final AttributeSensor<?> sensor) {
        return bucketFromAttribute(sensor, null);
    }

    private Map<String, BasicGroup> buckets = Maps.newHashMap();
    private volatile FunctionFeed rescan;

    @Override
    public void init() {
        super.init();

        Long interval = getConfig(RESCAN_INTERVAL);
        if (interval != null && interval > 0L) {
            rescan = FunctionFeed.builder()
                    .entity(this)
                    .poll(new FunctionPollConfig<Object, Void>(RESCAN)
                            .period(interval, TimeUnit.SECONDS)
                            .callable(new Callable<Void>() {
                                    @Override
                                    public Void call() throws Exception {
                                        rescanEntities();
                                        return null;
                                    }
                                }))
                    .build();
        }
    }

    @Override
    public void stop() {
        super.stop();

        if (rescan != null && rescan.isActivated()) {
            rescan.stop();
        }
    }

    @Override
    protected void onEntityAdded(Entity item) {
        synchronized (memberChangeMutex) {
            super.onEntityAdded(item);
            distributeEntities();
        }
    }

    @Override
    protected void onEntityRemoved(Entity item) {
        synchronized (memberChangeMutex) {
            super.onEntityRemoved(item);
            distributeEntities();
        }
    }
    
    @Override
    protected void onEntityChanged(Entity item) {
        synchronized (memberChangeMutex) {
            super.onEntityChanged(item);
            distributeEntities();
        }
    }

    @Override
    public void rescanEntities() {
        synchronized (memberChangeMutex) {
            super.rescanEntities();
            distributeEntities();
        }
    }

    @Override
    public void distributeEntities() {
        synchronized (memberChangeMutex) {
            Function<Entity, String> bucketFunction = getConfig(BUCKET_FUNCTION);
            EntitySpec<? extends BasicGroup> bucketSpec = getConfig(BUCKET_SPEC);
            if (bucketFunction == null || bucketSpec == null) return;

            // Bucketize the members where the function gives a non-null bucket
            Multimap<String, Entity> entityMapping = Multimaps.index(
                    Iterables.filter(getMembers(), Predicates.compose(Predicates.notNull(), bucketFunction)), bucketFunction);

            // Now fill the buckets
            for (String name : entityMapping.keySet()) {
                BasicGroup bucket = buckets.get(name);
                if (bucket == null) {
                    bucket = addChild(EntitySpec.create(bucketSpec).displayName(name));
                    Entities.manage(bucket);
                    buckets.put(name, bucket);
                }
                bucket.setMembers(entityMapping.get(name));
            }

            // Remove any now-empty buckets
            Set<String> empty = Sets.difference(buckets.keySet(), entityMapping.keySet());
            for (String name : empty) {
                Group removed = buckets.remove(name);
                removeChild(removed);
                Entities.unmanage(removed);
            }
        }
    }

}

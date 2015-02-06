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
package brooklyn.util.guava;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.Map;
import java.util.Set;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class KeyTransformingLoadingCacheTest {

    LoadingCache<Integer, Integer> doublingCache;
    LoadingCache<String, Integer> stringDoubler;
    LoadingCache<Map<String, Integer>, Map<Integer, String>> keyValueSwapCache;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        // Doubles Integer inputs
        doublingCache = CacheBuilder.newBuilder()
                .recordStats()
                .build(new CacheLoader<Integer, Integer>() {
                    @Override
                    public Integer load(Integer key) throws Exception {
                        return key * 2;
                    }
                });
        // Turns string to integer and doubles
        stringDoubler = KeyTransformingLoadingCache.from(doublingCache,
                new Function<String, Integer>(){
                    @Override
                    public Integer apply(String input) {
                        return Integer.valueOf(input);
                    }
                });
        // Swaps keys with values
        keyValueSwapCache = CacheBuilder.newBuilder()
                .recordStats()
                .build(new CacheLoader<Map<String, Integer>, Map<Integer, String>>() {
                    @Override
                    public Map<Integer, String> load(Map<String, Integer> key) throws Exception {
                        Map<Integer, String> out = Maps.newHashMapWithExpectedSize(key.size());
                        for (Map.Entry<String, Integer> entry : key.entrySet()) {
                            out.put(entry.getValue(), entry.getKey());
                        }
                        return out;
                    }
                });
    }

    @Test
    public void testIdentityCache() {
        assertEquals(stringDoubler.getUnchecked("10"), Integer.valueOf(20));
    }

    @Test
    public void testGetIfPresent() {
        assertNull(stringDoubler.getIfPresent("10"), "Cache should be empty");
        assertEquals(stringDoubler.getUnchecked("10"), Integer.valueOf(20), "Cache should load value for '10'");
        assertEquals(stringDoubler.getIfPresent("10"), Integer.valueOf(20), "Cache should load value for '10'");
        assertNull(stringDoubler.getIfPresent("20"), "Cache should have no value for '20'");
        assertNull(stringDoubler.getIfPresent(new Object()), "Cache should have no value for arbitrary Object");
    }

    @Test
    public void testInvalidate() {
        stringDoubler.getUnchecked("10");
        assertEquals(stringDoubler.size(), 1);
        stringDoubler.invalidate(new Object());
        assertEquals(stringDoubler.size(), 1);
        stringDoubler.invalidate("10");
        assertEquals(stringDoubler.size(), 0, "Expected cache to be empty after sole entry was invalidated");
    }

    @Test
    public void testSubsetOfMapKeys() {
        final Set<String> validKeys = ImmutableSet.of("a", "b", "c");
        LoadingCache<Map<String, Integer>, Map<Integer, String>> keySubset =
                KeyTransformingLoadingCache.from(keyValueSwapCache, new Function<Map<String, Integer>, Map<String, Integer>>() {
                    @Override
                    public Map<String, Integer> apply(Map<String, Integer> input) {
                        Map<String, Integer> replacement = Maps.newHashMap(input);
                        replacement.keySet().retainAll(validKeys);
                        return replacement;
                    }
                });

        Map<Integer, String> output = keySubset.getUnchecked(ImmutableMap.of("a", 1, "b", 2, "d", 4));
        assertEquals(output, ImmutableMap.of(1, "a", 2, "b"));
        assertEquals(keySubset.size(), 1, "Expected cache to contain one value");
        assertEquals(keySubset.stats().loadCount(), 1, "Expected cache to have loaded one value");

        // Check input with different key reducing to same map gives same output
        Map<Integer, String> output2 = keySubset.getUnchecked(ImmutableMap.of("a", 1, "b", 2, "z", 26));
        assertEquals(output2, output);
        assertEquals(keySubset.size(), 1, "Expected cache to contain one value");
        assertEquals(keySubset.stats().loadCount(), 1, "Expected cache to have loaded one value");

        // And
        keySubset.getUnchecked(ImmutableMap.of("c", 3));
        assertEquals(keySubset.size(), 2, "Expected cache to contain two values");
        assertEquals(keySubset.stats().loadCount(), 2, "Expected cache to have loaded a second value");
    }

}

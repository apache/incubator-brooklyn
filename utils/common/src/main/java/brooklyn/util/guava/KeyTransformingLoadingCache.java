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

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Function;
import com.google.common.cache.AbstractLoadingCache;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;

/**
 * A cache that transforms its keys before deferring to a delegate {@link LoadingCache}.
 */
// Concise names welcome.
public class KeyTransformingLoadingCache<A, B, V> extends AbstractLoadingCache<A, V> {

    private final LoadingCache<B, V> delegate;
    private final Function<A, B> keyTransformer;

    public KeyTransformingLoadingCache(LoadingCache<B, V> delegate, Function<A, B> keyTransformer) {
        this.delegate = delegate;
        this.keyTransformer = keyTransformer;
    }

    public static <A, B, V> KeyTransformingLoadingCache<A, B, V> from(LoadingCache<B, V> delegate, Function<A, B> keyTransformer) {
        return new KeyTransformingLoadingCache<A, B, V>(delegate, keyTransformer);
    }

    protected Function<A, B> keyTransformer() {
        return keyTransformer;
    }

    protected LoadingCache<B, V> delegate() {
        return delegate;
    }

    @Override
    public V getIfPresent(Object key) {
        try {
            @SuppressWarnings("unchecked")
            A cast = (A) key;
            return delegate().getIfPresent(keyTransformer().apply(cast));
        } catch (ClassCastException e) {
            return null;
        }
    }

    @Override
    public V get(A key, Callable<? extends V> valueLoader) throws ExecutionException {
        return delegate().get(keyTransformer().apply(key), valueLoader);
    }

    /**
     * Undefined because we can't prohibit a surjective {@link #keyTransformer()}.
     * @throws UnsupportedOperationException
     */
    @Override
    public ImmutableMap<A, V> getAllPresent(Iterable<?> keys) {
        throw new UnsupportedOperationException("getAllPresent in "+getClass().getName() + " undefined");
    }

    @Override
    public void put(A key, V value) {
        delegate().put(keyTransformer().apply(key), value);
    }

    @Override
    public void invalidate(Object key) {
        try {
            @SuppressWarnings("unchecked")
            A cast = (A) key;
            delegate().invalidate(keyTransformer().apply(cast));
        } catch (ClassCastException e) {
            // Ignore
        }
    }

    @Override
    public void invalidateAll() {
        delegate().invalidateAll();
    }

    @Override
    public long size() {
        return delegate().size();
    }

    @Override
    public CacheStats stats() {
        return delegate().stats();
    }

    @Override
    public V get(A key) throws ExecutionException {
        return delegate().get(keyTransformer().apply(key));
    }

    @Override
    public void refresh(A key) {
        delegate().refresh(keyTransformer().apply(key));
    }

    /**
     * Undefined because input values are not tracked.
     * @throws UnsupportedOperationException
     */
    @Override
    public ConcurrentMap<A, V> asMap() {
        throw new UnsupportedOperationException("asMap in " + getClass().getName() + " undefined");
    }

    @Override
    public void cleanUp() {
        delegate().cleanUp();
    }

    // Users can avoid middle type parameter.
    public static class KeyTransformingSameTypeLoadingCache<A, V> extends KeyTransformingLoadingCache<A, A, V> {
        public KeyTransformingSameTypeLoadingCache(LoadingCache<A, V> delegate, Function<A, A> keyTransformer) {
            super(delegate, keyTransformer);
        }

        // IDE note: This was named `from` to be consistent with KeyTransformingLoadingCache but Intellij 13
        // claims a name clash with the superclass `from`:
        // java: name clash: <A,V>from(LoadingCache<A,V>, Function<A,A>) in KeyTransformingSameTypeLoadingCache
        // and <A,B,V>from(LoadingCache<B,V>, Function<A,B>) in KeyTransformingLoadingCache have the same erasure,
        // yet neither hides the other
        public static <A, V> KeyTransformingSameTypeLoadingCache<A, V> with(LoadingCache<A, V> delegate, Function<A, A> keyTransformer) {
            return new KeyTransformingSameTypeLoadingCache<A, V>(delegate, keyTransformer);
        }
    }
}

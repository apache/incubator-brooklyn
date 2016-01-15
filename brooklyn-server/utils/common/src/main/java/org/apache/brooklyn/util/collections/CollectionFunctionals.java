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
package org.apache.brooklyn.util.collections;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.util.collections.QuorumCheck.QuorumChecks;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/** things which it seems should be in guava, but i can't find 
 * @author alex */
public class CollectionFunctionals {

    private static final class EqualsSetPredicate implements Predicate<Iterable<?>> {
        private final Iterable<?> target;

        private EqualsSetPredicate(Iterable<?> target) {
            this.target = target;
        }

        @Override
        public boolean apply(@Nullable Iterable<?> input) {
            if (input==null) return false;
            return Sets.newHashSet(target).equals(Sets.newHashSet(input));
        }
    }

    private static final class KeysOfMapFunction<K> implements Function<Map<K, ?>, Set<K>> {
        @Override
        public Set<K> apply(Map<K, ?> input) {
            if (input==null) return null;
            return input.keySet();
        }

        @Override public String toString() { return "keys"; }
    }

    private static final class SizeSupplier implements Supplier<Integer> {
        private final Iterable<?> collection;

        private SizeSupplier(Iterable<?> collection) {
            this.collection = collection;
        }

        @Override
        public Integer get() {
            return Iterables.size(collection);
        }

        @Override public String toString() { return "sizeSupplier("+collection+")"; }
    }

    public static final class SizeFunction implements Function<Iterable<?>, Integer> {
        private final Integer valueIfInputNull;

        private SizeFunction(Integer valueIfInputNull) {
            this.valueIfInputNull = valueIfInputNull;
        }

        @Override
        public Integer apply(Iterable<?> input) {
            if (input==null) return valueIfInputNull;
            return Iterables.size(input);
        }

        @Override public String toString() { return "sizeFunction"; }
    }

    public static Supplier<Integer> sizeSupplier(final Iterable<?> collection) {
        return new SizeSupplier(collection);
    }
    
    public static Function<Iterable<?>, Integer> sizeFunction() { return sizeFunction(null); }
    
    public static Function<Iterable<?>, Integer> sizeFunction(final Integer valueIfInputNull) {
        return new SizeFunction(valueIfInputNull);
    }

    public static final class FirstElementFunction<T> implements Function<Iterable<? extends T>, T> {
        public FirstElementFunction() {
        }

        @Override
        public T apply(Iterable<? extends T> input) {
            if (input==null || Iterables.isEmpty(input)) return null;
            return Iterables.get(input, 0);
        }

        @Override public String toString() { return "firstElementFunction"; }
    }

    public static <T> Function<Iterable<? extends T>, T> firstElement() {
        return new FirstElementFunction<T>();
    }
    
    public static <K> Function<Map<K,?>,Set<K>> keys() {
        return new KeysOfMapFunction<K>();
    }

    public static <K> Function<Map<K, ?>, Integer> mapSize() {
        return mapSize(null);
    }
    
    public static <K> Function<Map<K, ?>, Integer> mapSize(Integer valueIfNull) {
        return Functions.compose(CollectionFunctionals.sizeFunction(valueIfNull), CollectionFunctionals.<K>keys());
    }

    /** default guava Equals predicate will reflect order of target, and will fail when matching against a list;
     * this treats them both as sets */
    public static Predicate<Iterable<?>> equalsSetOf(Object... target) {
        return equalsSet(Arrays.asList(target));
    }
    public static Predicate<Iterable<?>> equalsSet(final Iterable<?> target) {
        return new EqualsSetPredicate(target);
    }

    public static Predicate<Iterable<?>> sizeEquals(int targetSize) {
        return Predicates.compose(Predicates.equalTo(targetSize), CollectionFunctionals.sizeFunction());
    }

    public static Predicate<Iterable<?>> empty() {
        return sizeEquals(0);
    }

    public static Predicate<Iterable<?>> notEmpty() {
        return Predicates.not(empty());
    }

    public static <K> Predicate<Map<K,?>> mapSizeEquals(int targetSize) {
        return Predicates.compose(Predicates.equalTo(targetSize), CollectionFunctionals.<K>mapSize());
    }

    public static <T,I extends Iterable<T>> Function<I, List<T>> limit(final int max) {
        return new LimitFunction<T,I>(max);
    }

    private static final class LimitFunction<T, I extends Iterable<T>> implements Function<I, List<T>> {
        private final int max;
        private LimitFunction(int max) {
            this.max = max;
        }
        @Override
        public List<T> apply(I input) {
            if (input==null) return null;
            MutableList<T> result = MutableList.of();
            for (T i: input) {
                result.add(i);
                if (result.size()>=max)
                    return result;
            }
            return result;
        }
    }

    // ---------
    public static <I,T extends Collection<I>> Predicate<T> contains(I item) {
        return new CollectionContains<I,T>(item);
    }
    
    private static final class CollectionContains<I,T extends Collection<I>> implements Predicate<T> {
        private final I item;
        private CollectionContains(I item) {
            this.item = item;
        }
        @Override
        public boolean apply(T input) {
            if (input==null) return false;
            return input.contains(item);
        }
        @Override
        public String toString() {
            return "contains("+item+")";
        }
    }

    // ---------
    
    /** 
     * Returns a predicate for a collection which is true if 
     * all elements in the collection given to the predicate
     * which satisfies the predicate given here.
     * <p>
     * This will return true for the empty set.
     * To require additionally that there is at least one
     * use {@link #quorum(QuorumCheck, Predicate)} with
     * {@link QuorumChecks#allAndAtLeastOne()}. */
    public static <T,TT extends Iterable<T>> Predicate<TT> all(Predicate<T> attributeSatisfies) {
        return quorum(QuorumChecks.all(), attributeSatisfies);
    }

    /** Returns a predicate for a collection which is true if 
     * there is at least one element in the collection given to the predicate
     * which satisfies the predicate given here. 
     */
    public static <T,TT extends Iterable<T>> Predicate<TT> any(Predicate<T> attributeSatisfies) {
        // implementation could be more efficient -- ie succeed fast
        return quorum(QuorumChecks.atLeastOne(), attributeSatisfies);
    }

    /** Returns a predicate for a collection which is true if 
     * the number of elements in the collection satisfying the predicate given here
     * passes the {@link QuorumCheck} given here.
     */
    public static <T,TT extends Iterable<T>> Predicate<TT> quorum(QuorumCheck quorumCheck, Predicate<T> attributeSatisfies) {
        return new QuorumSatisfies<T, TT>(quorumCheck, attributeSatisfies);
    }

    private static final class QuorumSatisfies<I,T extends Iterable<I>> implements Predicate<T> {
        private final Predicate<I> itemCheck;
        private final QuorumCheck quorumCheck;
        private QuorumSatisfies(QuorumCheck quorumCheck, Predicate<I> itemCheck) {
            this.itemCheck = Preconditions.checkNotNull(itemCheck, "itemCheck");
            this.quorumCheck = Preconditions.checkNotNull(quorumCheck, "quorumCheck");
        }
        @Override
        public boolean apply(T input) {
            if (input==null) return false;
            int sizeHealthy = 0, totalSize = 0;
            for (I item: input) {
                totalSize++;
                if (itemCheck.apply(item)) sizeHealthy++;
            }
            return quorumCheck.isQuorate(sizeHealthy, totalSize);
        }
        @Override
        public String toString() {
            return quorumCheck.toString()+"("+itemCheck+")";
        }
    }



}

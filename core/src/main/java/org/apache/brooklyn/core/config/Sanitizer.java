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
package org.apache.brooklyn.core.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.util.core.config.ConfigBag;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public final class Sanitizer {

    /**
     * Names that, if they appear anywhere in an attribute/config/field
     * indicates that it may be private, so should not be logged etc.
     */
    public static final List<String> SECRET_NAMES = ImmutableList.of(
            "password", 
            "passwd", 
            "credential", 
            "secret", 
            "private",
            "access.cert", 
            "access.key");

    public static final Predicate<Object> IS_SECRET_PREDICATE = new IsSecretPredicate();

    private static class IsSecretPredicate implements Predicate<Object> {
        @Override
        public boolean apply(Object name) {
            String lowerName = name.toString().toLowerCase();
            for (String secretName : SECRET_NAMES) {
                if (lowerName.contains(secretName))
                    return true;
            }
            return false;
        }
    };

    /**
     * Kept only in case this anonymous inner class has made it into any persisted state.
     * 
     * @deprecated since 0.7.0
     */
    @Deprecated
    @SuppressWarnings("unused")
    private static final Predicate<Object> IS_SECRET_PREDICATE_DEPRECATED = new Predicate<Object>() {
        @Override
        public boolean apply(Object name) {
            String lowerName = name.toString().toLowerCase();
            for (String secretName : SECRET_NAMES) {
                if (lowerName.contains(secretName))
                    return true;
            }
            return false;
        }
    };

    public static Sanitizer newInstance(Predicate<Object> sanitizingNeededCheck) {
        return new Sanitizer(sanitizingNeededCheck);
    }
    
    public static Sanitizer newInstance(){
        return newInstance(IS_SECRET_PREDICATE);
    }

    public static Map<String, Object> sanitize(ConfigBag input) {
        return sanitize(input.getAllConfig());
    }

    public static <K> Map<K, Object> sanitize(Map<K, ?> input) {
        return sanitize(input, Sets.newHashSet());
    }

    static <K> Map<K, Object> sanitize(Map<K, ?> input, Set<Object> visited) {
        return newInstance().apply(input, visited);
    }
    
    private Predicate<Object> predicate;

    private Sanitizer(Predicate<Object> sanitizingNeededCheck) {
        predicate = sanitizingNeededCheck;
    }

    public <K> Map<K, Object> apply(Map<K, ?> input) {
        return apply(input, Sets.newHashSet());
    }

    private <K> Map<K, Object> apply(Map<K, ?> input, Set<Object> visited) {
        Map<K, Object> result = Maps.newLinkedHashMap();
        for (Map.Entry<K, ?> e : input.entrySet()) {
            if (predicate.apply(e.getKey())){
                result.put(e.getKey(), "xxxxxxxx");
                continue;
            } 
            
            // need to compare object reference, not equality since we may miss some.
            // not a perfect identifier, but very low probability of collision.
            if (visited.contains(System.identityHashCode(e.getValue()))) {
                result.put(e.getKey(), e.getValue());
                continue;
            }

            visited.add(System.identityHashCode(e.getValue()));
            if (e.getValue() instanceof Map) {
                result.put(e.getKey(), apply((Map<?, ?>) e.getValue(), visited));
            } else if (e.getValue() instanceof List) {
                result.put(e.getKey(), applyList((List<?>) e.getValue(), visited));
            } else if (e.getValue() instanceof Set) {
                result.put(e.getKey(), applySet((Set<?>) e.getValue(), visited));
            } else {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }
    
    private List<Object> applyIterable(Iterable<?> input, Set<Object> visited){
        List<Object> result = Lists.newArrayList();
        for(Object o : input){
            if(visited.contains(System.identityHashCode(o))){
                result.add(o);
                continue;
            }

            visited.add(System.identityHashCode(o));
            if (o instanceof Map) {
                result.add(apply((Map<?, ?>) o, visited));
            } else if (o instanceof List) {
                result.add(applyList((List<?>) o, visited));
            } else if (o instanceof Set) {
                result.add(applySet((Set<?>) o, visited));
            } else {
                result.add(o);
            }

        }
        return result;
    }
    
    private List<Object> applyList(List<?> input, Set<Object> visited) {
       return applyIterable(input, visited);
    }
    
    private Set<Object> applySet(Set<?> input, Set<Object> visited) {
        Set<Object> result = Sets.newLinkedHashSet();
        result.addAll(applyIterable(input, visited));
        return result;
    }
}

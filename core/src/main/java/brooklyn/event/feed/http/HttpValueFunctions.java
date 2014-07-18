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
package brooklyn.event.feed.http;

import java.util.List;

import brooklyn.util.guava.Functionals;
import brooklyn.util.http.HttpToolResponse;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.gson.JsonElement;

public class HttpValueFunctions {

    private HttpValueFunctions() {} // instead use static utility methods
    
    public static Function<HttpToolResponse, Integer> responseCode() {
        return new Function<HttpToolResponse, Integer>() {
            @Override public Integer apply(HttpToolResponse input) {
                return input.getResponseCode();
            }
        };
    }

    public static Function<HttpToolResponse, Boolean> responseCodeEquals(final int expected) {
        return Functionals.chain(HttpValueFunctions.responseCode(), Functions.forPredicate(Predicates.equalTo(expected)));
    }
    
    public static Function<HttpToolResponse, Boolean> responseCodeEquals(final int... expected) {
        List<Integer> expectedList = Lists.newArrayList();
        for (int e : expected) {
            expectedList.add((Integer)e);
        }
        return Functionals.chain(HttpValueFunctions.responseCode(), Functions.forPredicate(Predicates.in(expectedList)));
    }
    
    public static Function<HttpToolResponse, String> stringContentsFunction() {
        return new Function<HttpToolResponse, String>() {
            @Override public String apply(HttpToolResponse input) {
                return input.getContentAsString();
            }
        };
    }
    
    public static Function<HttpToolResponse, JsonElement> jsonContents() {
        return Functionals.chain(stringContentsFunction(), JsonFunctions.asJson());
    }
    
    public static <T> Function<HttpToolResponse, T> jsonContents(String element, Class<T> expected) {
        return jsonContents(new String[] {element}, expected);
    }
    
    public static <T> Function<HttpToolResponse, T> jsonContents(String[] elements, Class<T> expected) {
        return Functionals.chain(jsonContents(), JsonFunctions.walk(elements), JsonFunctions.cast(expected));
    }

    public static <T> Function<HttpToolResponse, T> jsonContentsFromPath(String path){
        return Functionals.chain(jsonContents(), JsonFunctions.<T>getPath(path));
    }
    
    public static Function<HttpToolResponse, Long> latency() {
        return new Function<HttpToolResponse, Long>() {
            public Long apply(HttpToolResponse input) {
                return input.getLatencyFullContent();
            }
        };
    }
    
    /** @deprecated since 0.7.0 use {@link Functionals#chain(Function, Function)} */ @Deprecated
    public static <A,B,C> Function<A,C> chain(final Function<A,? extends B> f1, final Function<B,C> f2) {
        return Functionals.chain(f1, f2);
    }
    
    /** @deprecated since 0.7.0 use {@link Functionals#chain(Function, Function, Function)} */ @Deprecated
    public static <A,B,C,D> Function<A,D> chain(final Function<A,? extends B> f1, final Function<B,? extends C> f2, final Function<C,D> f3) {
        return Functionals.chain(f1, f2, f3);
    }

    /** @deprecated since 0.7.0 use {@link Functionals#chain(Function, Function, Function, Function)} */ @Deprecated
    public static <A,B,C,D,E> Function<A,E> chain(final Function<A,? extends B> f1, final Function<B,? extends C> f2, final Function<C,? extends D> f3, final Function<D,E> f4) {
        return Functionals.chain(f1, f2, f3, f4);
    }

}

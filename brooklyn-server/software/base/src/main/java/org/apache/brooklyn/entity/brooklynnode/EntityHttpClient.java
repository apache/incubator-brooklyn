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
package org.apache.brooklyn.entity.brooklynnode;

import java.util.Map;

import org.apache.brooklyn.util.http.HttpTool;
import org.apache.brooklyn.util.http.HttpToolResponse;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Range;

/**
 * Helpful methods for making HTTP requests to {@link BrooklynNode} entities.
 */
public interface EntityHttpClient {
    public static class ResponseCodePredicates {
        private static class ResponseCodeHealthyPredicate implements Predicate<Integer> {
            @Override
            public boolean apply(Integer input) {
                return HttpTool.isStatusCodeHealthy(input);
            }
        }
        public static Predicate<Integer> informational() {return Range.closed(100, 199);}
        public static Predicate<Integer> success() {return new ResponseCodeHealthyPredicate();}
        public static Predicate<Integer> redirect() {return Range.closed(300, 399);}
        public static Predicate<Integer> clientError() {return Range.closed(400, 499);}
        public static Predicate<Integer> serverError() {return Range.closed(500, 599);}
        public static Predicate<Integer> invalid() {return Predicates.or(Range.atMost(99), Range.atLeast(600));}
    }

    /**
     * @return An HTTP client builder configured to access the {@link
     *         BrooklynNode#WEB_CONSOLE_URI web console URI} at the
     *         given entity, or null if the entity has no URI.
     */
    public HttpTool.HttpClientBuilder getHttpClientForBrooklynNode();

    /**
     * Configure which response codes are treated as successful
     * @param successPredicate A predicate which returns true is the response code is acceptable
     * @return this
     */
    public EntityHttpClient responseSuccess(Predicate<Integer> responseSuccess);

    /**
     * Makes an HTTP GET to a Brooklyn node entity.
     * @param path Relative path to resource on server, e.g v1/catalog
     * @return The server's response
     */
    public HttpToolResponse get(String path);

    /**
     * Makes an HTTP POST to a Brooklyn node entity.
     * @param path Relative path to resource on server, e.g v1/catalog
     * @param body byte array of serialized JSON to attach to the request
     * @return The server's response
     */
    public HttpToolResponse post(String path, Map<String, String> headers, byte[] body);

    /**
     * Makes an HTTP POST to a Brooklyn node entity.
     * @param path Relative path to resource on server, e.g v1/catalog
     * @param formParams The parameters to send in a x-www-form-urlencoded format
     * @return The server's response
     */
    public HttpToolResponse post(String path, Map<String, String> headers, Map<String, String> formParams);

    /**
     * Makes an HTTP DELETE to a Brooklyn node entity.
     * @param path Relative path to resource on server, e.g v1/catalog
     * @return The server's response
     */
    public HttpToolResponse delete(String path, Map<String, String> headers);

}

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
package brooklyn.entity.brooklynnode;

import java.util.Map;

import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;

/**
 * Helpful methods for making HTTP requests to {@link BrooklynNode} entities.
 */
public interface EntityHttpClient {
    /**
     * @return An HTTP client builder configured to access the {@link
     *         BrooklynNode#WEB_CONSOLE_URI web console URI} at the
     *         given entity, or null if the entity has no URI.
     */
    public HttpTool.HttpClientBuilder getHttpClientForBrooklynNode();

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
     * @param body byte array of serialized JSON to attach to the request
     * @return The server's response
     */
    public HttpToolResponse post(String path, Map<String, String> headers, Map<String, String> formParams);

}

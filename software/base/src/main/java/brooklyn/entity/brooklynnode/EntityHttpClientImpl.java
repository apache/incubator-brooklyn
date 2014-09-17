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

import java.net.URI;
import java.util.Map;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.net.Urls;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.Tasks;

import com.google.common.base.Preconditions;

public class EntityHttpClientImpl implements EntityHttpClient {
    private static final Logger LOG = LoggerFactory.getLogger(EntityHttpClientImpl.class);

    protected static interface HttpCall {
        public HttpToolResponse call(HttpClient client, URI uri);
    }

    protected BrooklynNode node;

    protected EntityHttpClientImpl(BrooklynNode node) {
        this.node = node;
    }

    @Override
    public HttpTool.HttpClientBuilder getHttpClientForBrooklynNode() {
        URI baseUri = node.getAttribute(BrooklynNode.WEB_CONSOLE_URI);
        if (baseUri == null) {
            return null;
        }
        HttpTool.HttpClientBuilder builder = HttpTool.httpClientBuilder()
                .trustAll()
                .laxRedirect(true)
                .uri(baseUri);
        if (node.getConfig(BrooklynNode.MANAGEMENT_USER) != null) {
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(
                    node.getConfig(BrooklynNode.MANAGEMENT_USER),
                    node.getConfig(BrooklynNode.MANAGEMENT_PASSWORD));
            builder.credentials(credentials);
        }
        return builder;
    }

    protected HttpToolResponse exec(String path, HttpCall httpCall) {
        HttpClient client = Preconditions.checkNotNull(getHttpClientForBrooklynNode(), "No address info for "+node)
            .build();
        URI baseUri = node.getAttribute(BrooklynNode.WEB_CONSOLE_URI);
        URI uri = URI.create(Urls.mergePaths(baseUri.toString(), path));

        HttpToolResponse result;
        try {
            result = httpCall.call(client, uri);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            throw new IllegalStateException("Invalid response invoking " + uri + ": " + e, e);
        }
        Tasks.addTagDynamically(BrooklynTaskTags.tagForStream("http_response", Streams.byteArray(result.getContent())));
        if (!HttpTool.isStatusCodeHealthy(result.getResponseCode())) {
            LOG.warn("Invalid response invoking {}: response code {}\n{}: {}",
                    new Object[]{uri, result.getResponseCode(), result, new String(result.getContent())});
            throw new IllegalStateException("Invalid response invoking " + uri + ": response code " + result.getResponseCode());
        }
        return result;
    }

    @Override
    public HttpToolResponse get(String path) {
        return exec(path, new HttpCall() {
            @Override
            public HttpToolResponse call(HttpClient client, URI uri) {
                return HttpTool.httpGet(client, uri, MutableMap.<String, String>of());
            }
        });
    }

    @Override
    public HttpToolResponse post(String path, final Map<String, String> headers, final byte[] body) {
        return exec(path, new HttpCall() {
            @Override
            public HttpToolResponse call(HttpClient client, URI uri) {
                return HttpTool.httpPost(client, uri, headers, body);
            }
        });
    }

    @Override
    public HttpToolResponse post(String path, final Map<String, String> headers, final Map<String, String> formParams) {
        return exec(path, new HttpCall() {
            @Override
            public HttpToolResponse call(HttpClient client, URI uri) {
                return HttpTool.httpPost(client, uri, headers, formParams);
            }
        });
    }
}

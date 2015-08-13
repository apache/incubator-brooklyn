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

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.event.AttributeSensor;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.net.Urls;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.Tasks;

public class EntityHttpClientImpl implements EntityHttpClient {
    private static final Logger LOG = LoggerFactory.getLogger(EntityHttpClientImpl.class);

    protected static interface HttpCall {
        public HttpToolResponse call(HttpClient client, URI uri);
    }

    protected Entity entity;
    protected AttributeSensor<?> urlSensor;
    protected ConfigKey<?> urlConfig;
    protected Predicate<Integer> responseSuccess = ResponseCodePredicates.success();

    protected EntityHttpClientImpl(Entity entity, AttributeSensor<?> urlSensor) {
        this.entity = entity;
        this.urlSensor = urlSensor;
    }

    protected EntityHttpClientImpl(Entity entity, ConfigKey<?> urlConfig) {
        this.entity = entity;
        this.urlConfig = urlConfig;
    }

    @Override
    public HttpTool.HttpClientBuilder getHttpClientForBrooklynNode() {
        String baseUrl = getEntityUrl();
        HttpTool.HttpClientBuilder builder = HttpTool.httpClientBuilder()
                .trustAll()
                .laxRedirect(true)
                .uri(baseUrl);
        if (entity.getConfig(BrooklynNode.MANAGEMENT_USER) != null) {
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(
                    entity.getConfig(BrooklynNode.MANAGEMENT_USER),
                    entity.getConfig(BrooklynNode.MANAGEMENT_PASSWORD));
            builder.credentials(credentials);
        }
        return builder;
    }

    @Override
    public EntityHttpClient responseSuccess(Predicate<Integer> responseSuccess) {
        this.responseSuccess = checkNotNull(responseSuccess, "responseSuccess");
        return this;
    }

    protected HttpToolResponse exec(String path, HttpCall httpCall) {
        HttpClient client = Preconditions.checkNotNull(getHttpClientForBrooklynNode(), "No address info for "+entity)
                .build();
        String baseUri = getEntityUrl();
        URI uri = URI.create(Urls.mergePaths(baseUri, path));

        HttpToolResponse result;
        try {
            result = httpCall.call(client, uri);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            throw new IllegalStateException("Invalid response invoking " + uri + ": " + e, e);
        }
        Tasks.addTagDynamically(BrooklynTaskTags.tagForStream("http_response", Streams.byteArray(result.getContent())));
        if (!responseSuccess.apply(result.getResponseCode())) {
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

    protected String getEntityUrl() {
        Preconditions.checkState(urlSensor == null ^ urlConfig == null, "Exactly one of urlSensor and urlConfig should be non-null for entity " + entity);
        Object url = null;
        if (urlSensor != null) {
            url = entity.getAttribute(urlSensor);
        } else if (urlConfig != null) {
            url = entity.getConfig(urlConfig);
        }
        Preconditions.checkNotNull(url, "URL sensor " + urlSensor + " for entity " + entity + " is empty");
        return url.toString();
    }

    @Override
    public HttpToolResponse delete(String path, final Map<String, String> headers) {
        return exec(path, new HttpCall() {
            @Override
            public HttpToolResponse call(HttpClient client, URI uri) {
                return HttpTool.httpDelete(client, uri, headers);
            }
        });
    }
}

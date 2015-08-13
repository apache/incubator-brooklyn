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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;

import brooklyn.entity.brooklynnode.EntityHttpClient;
import brooklyn.util.http.HttpTool.HttpClientBuilder;
import brooklyn.util.http.HttpToolResponse;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

public class CallbackEntityHttpClient implements EntityHttpClient {
    public static class Request {
        private Entity entity;
        private String method;
        private String path;
        private Map<String, String> params;
        public Request(Entity entity, String method, String path, Map<String, String> params) {
            this.entity = entity;
            this.method = method;
            this.path = path;
            this.params = params;
        }
        public Entity getEntity() {
            return entity;
        }
        public String getMethod() {
            return method;
        }
        public String getPath() {
            return path;
        }
        public Map<String, String> getParams() {
            return params;
        }
    }
    private Function<Request, String> callback;
    private Entity entity;

    public CallbackEntityHttpClient(Entity entity, Function<Request, String> callback) {
        this.entity = entity;
        this.callback = callback;
    }

    @Override
    public HttpClientBuilder getHttpClientForBrooklynNode() {
        throw new IllegalStateException("Method call not expected");
    }

    @Override
    public HttpToolResponse get(String path) {
        String result = callback.apply(new Request(entity, HttpGet.METHOD_NAME, path, Collections.<String, String>emptyMap()));
        return new HttpToolResponse(HttpStatus.SC_OK, null, result.getBytes(), 0, 0, 0);
    }

    @Override
    public HttpToolResponse post(String path, Map<String, String> headers, byte[] body) {
        throw new IllegalStateException("Method call not expected");
    }

    @Override
    public HttpToolResponse post(String path, Map<String, String> headers, Map<String, String> formParams) {
        String result = callback.apply(new Request(entity, HttpPost.METHOD_NAME, path, formParams));
        return new HttpToolResponse(HttpStatus.SC_OK, Collections.<String, List<String>>emptyMap(), result.getBytes(), 0, 0, 0);
    }
    
    @Override
    public HttpToolResponse delete(String path, Map<String, String> headers) {
        throw new IllegalStateException("Method call not expected");
    }

    @Override
    public EntityHttpClient responseSuccess(Predicate<Integer> successPredicate) {
        throw new IllegalStateException("Method call not expected");
    }
}

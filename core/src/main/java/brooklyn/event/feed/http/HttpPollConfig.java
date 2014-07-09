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

import java.net.URI;
import java.util.Map;

import javax.annotation.Nullable;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.PollConfig;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.net.URLParamEncoder;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class HttpPollConfig<T> extends PollConfig<HttpToolResponse, T, HttpPollConfig<T>> {

    private String method = "GET";
    private String suburl = "";
    private Map<String, String> vars = ImmutableMap.<String,String>of();
    private Map<String, String> headers = ImmutableMap.<String,String>of();
    private byte[] body;
    private Duration connectionTimeout;
    private Duration socketTimeout;
    
    public static final Predicate<HttpToolResponse> DEFAULT_SUCCESS = new Predicate<HttpToolResponse>() {
        @Override
        public boolean apply(@Nullable HttpToolResponse input) {
            return input != null && input.getResponseCode() >= 200 && input.getResponseCode() <= 399;
        }};
    
    public HttpPollConfig(AttributeSensor<T> sensor) {
        super(sensor);
        super.checkSuccess(DEFAULT_SUCCESS);
    }

    public HttpPollConfig(HttpPollConfig<T> other) {
        super(other);
        suburl = other.suburl;
        vars = other.vars;
        method = other.method;
        headers = other.headers;
    }
    
    public static <T> HttpPollConfig<T> forSensor(AttributeSensor<T> sensor) {
        return new HttpPollConfig<T>(sensor);
    }
    
    public static HttpPollConfig<Void> forMultiple() {
        return new HttpPollConfig<Void>(PollConfig.NO_SENSOR);
    }
    
    public String getSuburl() {
        return suburl;
    }
    
    public Map<String, String> getVars() {
        return vars;
    }
    
    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }
    
    public Duration getSocketTimeout() {
        return socketTimeout;
    }
    
    public String getMethod() {
        return method;
    }
    
    public byte[] getBody() {
        return body;
    }
    
    public HttpPollConfig<T> method(String val) {
        this.method = val; return this;
    }
    
    public HttpPollConfig<T> suburl(String val) {
        this.suburl = val; return this;
    }
    
    public HttpPollConfig<T> vars(Map<String,String> val) {
        this.vars = val; return this;
    }
    
    public HttpPollConfig<T> headers(Map<String,String> val) {
        this.headers = val; return this;
    }
    
    public HttpPollConfig<T> body(byte[] val) {
        this.body = val; return this;
    }
    public HttpPollConfig<T> connectionTimeout(Duration val) {
        this.connectionTimeout = val;
        return this;
    }
    public HttpPollConfig<T> socketTimeout(Duration val) {
        this.socketTimeout = val;
        return this;
    }
    public URI buildUri(URI baseUri, Map<String,String> baseUriVars) {
        String uri = (baseUri != null ? baseUri.toString() : "") + (suburl != null ? suburl : "");
        Map<String,String> allvars = concat(baseUriVars, vars);
        
        if (allvars != null && allvars.size() > 0) {
            Iterable<String> args = Iterables.transform(allvars.entrySet(), 
                    new Function<Map.Entry<String,String>,String>() {
                        @Override public String apply(Map.Entry<String,String> entry) {
                            String k = entry.getKey();
                            String v = entry.getValue();
                            return URLParamEncoder.encode(k) + (v != null ? "=" + URLParamEncoder.encode(v) : "");
                        }
                    });
            uri += "?" + Joiner.on("&").join(args);
        }
        
        return URI.create(uri);
    }

    public Map<String, String> buildHeaders(Map<String, String> baseHeaders) {
        return MutableMap.<String,String>builder()
                .putAll(baseHeaders)
                .putAll(headers)
                .build();
    }
    
    @SuppressWarnings("unchecked")
    private <K,V> Map<K,V> concat(Map<? extends K,? extends V> map1, Map<? extends K,? extends V> map2) {
        if (map1 == null || map1.isEmpty()) return (Map<K,V>) map2;
        if (map2 == null || map2.isEmpty()) return (Map<K,V>) map1;
        
        // TODO Not using Immutable builder, because that fails if duplicates in map1 and map2
        return MutableMap.<K,V>builder().putAll(map1).putAll(map2).build();
    }

    @Override
    public String toString() {
        return "http["+suburl+"]";
    }
    
}

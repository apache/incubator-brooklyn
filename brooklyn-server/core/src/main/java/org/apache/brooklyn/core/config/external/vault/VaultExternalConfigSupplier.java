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
package org.apache.brooklyn.core.config.external.vault;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.config.external.AbstractExternalConfigSupplier;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.brooklyn.util.http.HttpToolResponse;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.text.Strings;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public abstract class VaultExternalConfigSupplier extends AbstractExternalConfigSupplier {
    public static final String CHARSET_NAME = "UTF-8";
    public static final ImmutableMap<String, String> MINIMAL_HEADERS = ImmutableMap.of(
            "Content-Type", "application/json; charset=" + CHARSET_NAME,
            "Accept", "application/json",
            "Accept-Charset", CHARSET_NAME);
    private static final Logger LOG = LoggerFactory.getLogger(VaultExternalConfigSupplier.class);
    protected final Map<String, String> config;
    protected final String name;
    protected final HttpClient httpClient;
    protected final Gson gson;
    protected final String endpoint;
    protected final String path;
    protected final String token;
    protected final ImmutableMap<String, String> headersWithToken;

    public VaultExternalConfigSupplier(ManagementContext managementContext, String name, Map<String, String> config) {
        super(managementContext, name);
        this.config = config;
        this.name = name;
        httpClient = HttpTool.httpClientBuilder().build();
        gson = new GsonBuilder().create();

        List<String> errors = Lists.newArrayListWithCapacity(2);
        endpoint = config.get("endpoint");
        if (Strings.isBlank(endpoint)) errors.add("missing configuration 'endpoint'");
        path = config.get("path");
        if (Strings.isBlank(path)) errors.add("missing configuration 'path'");
        if (!errors.isEmpty()) {
            String message = String.format("Problem configuration Vault external config supplier '%s': %s",
                    name, Joiner.on(System.lineSeparator()).join(errors));
            throw new IllegalArgumentException(message);
        }

        token = initAndLogIn(config);
        headersWithToken = ImmutableMap.<String, String>builder()
                .putAll(MINIMAL_HEADERS)
                .put("X-Vault-Token", token)
                .build();
    }

    protected abstract String initAndLogIn(Map<String, String> config);

    @Override
    public String get(String key) {
        JsonObject response = apiGet(Urls.mergePaths("v1", path), headersWithToken);
        return response.getAsJsonObject("data").get(key).getAsString();
    }

    protected JsonObject apiGet(String path, ImmutableMap<String, String> headers) {
        try {
            String uri = Urls.mergePaths(endpoint, path);
            LOG.debug("Vault request - GET: {}", uri);
            LOG.debug("Vault request - headers: {}", headers.toString());
            HttpToolResponse response = HttpTool.httpGet(httpClient, Urls.toUri(uri), headers);
            LOG.debug("Vault response - code: {} {}", new Object[]{Integer.toString(response.getResponseCode()), response.getReasonPhrase()});
            LOG.debug("Vault response - headers: {}", response.getHeaderLists().toString());
            String responseBody = new String(response.getContent(), CHARSET_NAME);
            LOG.debug("Vault response - body: {}", responseBody);
            if (HttpTool.isStatusCodeHealthy(response.getResponseCode())) {
                return gson.fromJson(responseBody, JsonObject.class);
            } else {
                throw new IllegalStateException("HTTP request returned error");
            }
        } catch (UnsupportedEncodingException e) {
            throw Exceptions.propagate(e);
        }
    }

    protected JsonObject apiPost(String path, ImmutableMap<String, String> headers, ImmutableMap<String, String> requestData) {
        try {
            String body = gson.toJson(requestData);
            String uri = Urls.mergePaths(endpoint, path);
            LOG.debug("Vault request - POST: {}", uri);
            LOG.debug("Vault request - headers: {}", headers.toString());
            LOG.debug("Vault request - body: {}", body);
            HttpToolResponse response = HttpTool.httpPost(httpClient, Urls.toUri(uri), headers, body.getBytes(CHARSET_NAME));
            LOG.debug("Vault response - code: {} {}", new Object[]{Integer.toString(response.getResponseCode()), response.getReasonPhrase()});
            LOG.debug("Vault response - headers: {}", response.getHeaderLists().toString());
            String responseBody = new String(response.getContent(), CHARSET_NAME);
            LOG.debug("Vault response - body: {}", responseBody);
            if (HttpTool.isStatusCodeHealthy(response.getResponseCode())) {
                return gson.fromJson(responseBody, JsonObject.class);
            } else {
                throw new IllegalStateException("HTTP request returned error");
            }
        } catch (UnsupportedEncodingException e) {
            throw Exceptions.propagate(e);
        }
    }
}

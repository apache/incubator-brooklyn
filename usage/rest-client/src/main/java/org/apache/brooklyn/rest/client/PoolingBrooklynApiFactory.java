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
package org.apache.brooklyn.rest.client;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jboss.resteasy.client.ClientExecutor;
import org.jboss.resteasy.client.core.executors.ApacheHttpClient4Executor;

import java.net.URL;

/**
 * BrooklynApi factory.
 * <p/>
 * This Factory uses a {@link PoolingHttpClientConnectionManager} to reuse HTTP connections.
 */
public class PoolingBrooklynApiFactory implements BrooklynApiFactory {

    private Supplier<PoolingHttpClientConnectionManager> connectionManagerSupplier = Suppliers.memoize(new Supplier<PoolingHttpClientConnectionManager>() {
        @Override
        public PoolingHttpClientConnectionManager get() {
            PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
            connManager.setMaxTotal(maxPoolSize);
            connManager.setDefaultMaxPerRoute(maxPoolSize);
            return connManager;
        }
    });

    private Supplier<RequestConfig> reqConfSupplier = Suppliers.memoize(new Supplier<RequestConfig>() {
        @Override
        public RequestConfig get() {

            return RequestConfig.custom()
                    .setConnectTimeout(timeOutInMillis)
                    .setConnectionRequestTimeout(timeOutInMillis)
                    .build();
        }
    });

    protected final int maxPoolSize;
    protected final int timeOutInMillis;

    @SuppressWarnings("deprecation")
    private ClientExecutor getPoolingClientExecutor(Credentials credentials) {
        CredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(AuthScope.ANY, credentials);

        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(provider)
                .setDefaultRequestConfig(reqConfSupplier.get())
                .setConnectionManager(connectionManagerSupplier.get())
                .build();

        return new ApacheHttpClient4Executor(httpClient);
    }

    /**
     * Creates a new PoolingBrooklynApiFactory with a max pool size of 20
     * connections, and a connection timeout of 5000 milliseconds
     */
    public PoolingBrooklynApiFactory() {
        this(20, 5000);
    }

    /**
     * Creates a new PoolingBrooklynApiFactory
     * @param maxPoolSize the maximum size of the pool
     * @param timeOutInMillis connection timeout in milliseconds
     */
    public PoolingBrooklynApiFactory(int maxPoolSize, int timeOutInMillis) {
        this.maxPoolSize = maxPoolSize;
        this.timeOutInMillis = timeOutInMillis;
    }

    @SuppressWarnings("deprecation")
    @Override
    public BrooklynApi getBrooklynApi(URL endpoint, String username, String password) {
        UsernamePasswordCredentials credentials =
                (username != null) ? new UsernamePasswordCredentials(username, password)
                        : null;
        return new BrooklynApi(getPoolingClientExecutor(credentials), endpoint);
    }
}

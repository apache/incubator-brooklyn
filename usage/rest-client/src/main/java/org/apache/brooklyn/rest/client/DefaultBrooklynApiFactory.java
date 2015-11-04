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

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jboss.resteasy.client.ClientExecutor;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.core.executors.ApacheHttpClient4Executor;

import java.net.URL;

/**
 * BrooklynApi factory.
 * <p/>
 * This Factory creates BrooklynApi instances that use an {@link ApacheHttpClient4Executor}
 */
public class DefaultBrooklynApiFactory implements BrooklynApiFactory {

    @SuppressWarnings("deprecation")
    @Override
    public BrooklynApi getBrooklynApi(URL endpoint, String username, String password) {
        ClientExecutor clientExecutor;
        if (username != null) {
            DefaultHttpClient httpClient = new DefaultHttpClient();
            httpClient.getCredentialsProvider().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
            clientExecutor = new ApacheHttpClient4Executor(httpClient);
        } else {
            clientExecutor = ClientRequest.getDefaultExecutor();
        }
        return new BrooklynApi(clientExecutor, endpoint);
    }
}

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
package org.apache.brooklyn.test.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;

import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.net.Networking;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;

public class TestHttpServer {
    private static class HandlerTuple {
        String path;
        HttpRequestHandler handler;
        public HandlerTuple(String path, HttpRequestHandler handler) {
            this.path = path;
            this.handler = handler;
        }
    }
    private HttpServer server;
    private List<HttpRequestInterceptor> requestInterceptors = MutableList.of();
    private List<HttpResponseInterceptor> responseInterceptors = MutableList.of(new ResponseContent(), new ResponseConnControl());
    private int basePort = 50505;
    private Collection<HandlerTuple> handlers = MutableList.of();

    public TestHttpServer interceptor(HttpResponseInterceptor interceptor) {
        checkNotStarted();
        responseInterceptors.add(interceptor);
        return this;
    }

    public TestHttpServer requestInterceptors(List<HttpResponseInterceptor> interceptors) {
        checkNotStarted();
        this.responseInterceptors = interceptors;
        return this;
    }

    public TestHttpServer interceptor(HttpRequestInterceptor interceptor) {
        checkNotStarted();
        requestInterceptors.add(interceptor);
        return this;
    }

    public TestHttpServer responseInterceptors(List<HttpRequestInterceptor> interceptors) {
        checkNotStarted();
        this.requestInterceptors = interceptors;
        return this;
    }

    public TestHttpServer basePort(int basePort) {
        checkNotStarted();
        this.basePort = basePort;
        return this;
    }

    public TestHttpServer handler(String path, HttpRequestHandler handler) {
        checkNotStarted();
        handlers.add(new HandlerTuple(path, handler));
        return this;
    }

    public TestHttpServer start() {
        checkNotStarted();

        HttpProcessor httpProcessor = new ImmutableHttpProcessor(requestInterceptors, responseInterceptors);
        int port = Networking.nextAvailablePort(basePort);
        ServerBootstrap bootstrap = ServerBootstrap.bootstrap()
            .setListenerPort(port)
            .setLocalAddress(getLocalAddress())
            .setHttpProcessor(httpProcessor);

        for (HandlerTuple tuple : handlers) {
            bootstrap.registerHandler(tuple.path, tuple.handler);
        }
        server = bootstrap.create();

        try {
            server.start();
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }

        return this;
    }

    public void stop() {
        if (null != server) {
            server.stop();
            server = null;
        }
    }

    private void checkNotStarted() {
        if (server != null) {
            throw new IllegalStateException("Server already started");
        }
    }

    private InetAddress getLocalAddress() {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw Exceptions.propagate(e);
        }
    }

    public String getUrl() {
        try {
            if (server==null) {
                // guess the URL, in those cases where the server is not started yet
                return new URL("http", getLocalAddress().getHostAddress(), basePort, "").toExternalForm();
            }
            return new URL("http", server.getInetAddress().getHostAddress(), server.getLocalPort(), "").toExternalForm();
        } catch (MalformedURLException e) {
            throw Exceptions.propagate(e);
        }
    }

}

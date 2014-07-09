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
package brooklyn.util.http;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;

import javax.net.ssl.SSLSocketFactory;

import com.google.common.base.Throwables;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

/** like MockWebServer (and delegating) but:
 * <li> allows subclassing
 * <li> easy way to create instance which returns localhost for {@link #getHostName()}
 *      (since otherwise you can get failures on networks which misconfigure hostname) 
 * */
public class BetterMockWebServer {

    final MockWebServer delegate = new MockWebServer();
    String hostname = null;
    boolean isHttps = false;
    
    public static BetterMockWebServer newInstanceLocalhost() {
        return new BetterMockWebServer().setHostName("localhost");
    }
    
    /** use {@link #newInstanceLocalhost()} or subclass */
    protected BetterMockWebServer() {}

    public BetterMockWebServer setHostName(String hostname) {
        this.hostname = hostname;
        return this;
    }


    // --- delegate methods (unchanged)
    
    public void enqueue(MockResponse response) {
        delegate.enqueue(response);
    }

    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    public String getCookieDomain() {
        return delegate.getCookieDomain();
    }

    public String getHostName() {
        if (hostname!=null) return hostname;
        return delegate.getHostName();
    }

    public int getPort() {
        return delegate.getPort();
    }

    public int getRequestCount() {
        return delegate.getRequestCount();
    }

    public URL getUrl(String path) {
        try {
            return isHttps
                ? new URL("https://" + getHostName() + ":" + getPort() + path)
                : new URL("http://" + getHostName() + ":" + getPort() + path);
        } catch (MalformedURLException e) {
            throw Throwables.propagate(e);
        }
    }

    public int hashCode() {
        return delegate.hashCode();
    }

    public void play() throws IOException {
        delegate.play();
    }

    public void play(int port) throws IOException {
        delegate.play(port);
    }

    public void setBodyLimit(int maxBodyLength) {
        delegate.setBodyLimit(maxBodyLength);
    }

    public void setDispatcher(Dispatcher dispatcher) {
        delegate.setDispatcher(dispatcher);
    }

    public void shutdown() throws IOException {
        delegate.shutdown();
    }

    public RecordedRequest takeRequest() throws InterruptedException {
        return delegate.takeRequest();
    }

    public Proxy toProxyAddress() {
        return delegate.toProxyAddress();
    }

    public String toString() {
        return delegate.toString();
    }

    public void useHttps(SSLSocketFactory sslSocketFactory, boolean tunnelProxy) {
        isHttps = true;
        delegate.useHttps(sslSocketFactory, tunnelProxy);
    }
    
    
    
}

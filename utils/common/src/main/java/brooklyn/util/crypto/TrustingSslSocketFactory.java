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
package brooklyn.util.crypto;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

/** An SSLSocketFactory which trusts all endpoints (ie encryption but no authentication) */
public class TrustingSslSocketFactory extends SSLSocketFactory {

    private static final Logger logger = LoggerFactory.getLogger(TrustingSslSocketFactory.class);
    
    private static TrustingSslSocketFactory INSTANCE;
    public synchronized static TrustingSslSocketFactory getInstance() {
        if (INSTANCE==null) INSTANCE = new TrustingSslSocketFactory();
        return INSTANCE;
    }
    
    private static SSLContext sslContext; 
    static {
        try {
            sslContext = SSLContext.getInstance("TLS");
        } catch (Exception e) {
            logger.error("Unable to set up SSLContext with TLS. Https activity will likely fail.", e);
        }
    }

    // no reason this can't be public, but no reason it should be necessary;
    // just use getInstance to get the shared INSTANCE
    protected TrustingSslSocketFactory() {
        super();
        try {
            sslContext.init(null, new TrustManager[] { SslTrustUtils.TRUST_ALL }, null);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
        return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
    }

    @Override
    public Socket createSocket() throws IOException {
        return sslContext.getSocketFactory().createSocket();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return sslContext.getSocketFactory().getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return sslContext.getSocketFactory().getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(String arg0, int arg1) throws IOException, UnknownHostException {
        return sslContext.getSocketFactory().createSocket(arg0, arg1);
    }

    @Override
    public Socket createSocket(InetAddress arg0, int arg1) throws IOException {
        return sslContext.getSocketFactory().createSocket(arg0, arg1);
    }

    @Override
    public Socket createSocket(String arg0, int arg1, InetAddress arg2, int arg3) throws IOException, UnknownHostException {
        return sslContext.getSocketFactory().createSocket(arg0, arg1, arg2, arg3);
    }

    @Override
    public Socket createSocket(InetAddress arg0, int arg1, InetAddress arg2, int arg3) throws IOException {
        return sslContext.getSocketFactory().createSocket(arg0, arg1, arg2, arg3);
    }
}
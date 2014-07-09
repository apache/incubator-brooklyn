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
package brooklyn.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

// FIXME copied from brooklyn-core because core not visible here

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

    /** configures a connection to accept all certificates, if it is for https */
    public static <T extends URLConnection> T configure(T connection) {
        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection)connection).setSSLSocketFactory(getInstance());
        }
        return connection;
    }
    
    /** trusts all SSL certificates */
    public static final TrustManager TRUST_ALL = new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
            
        }
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
        }
    };

    // no reason this can't be public, but no reason it should be necessary;
    // just use getInstance to get the shared INSTANCE
    protected TrustingSslSocketFactory() {
        super();
        try {
            sslContext.init(null, new TrustManager[] { TRUST_ALL }, null);
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
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
package brooklyn.util.jmx.jmxmp;

import java.io.IOException;
import java.net.MalformedURLException;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import brooklyn.util.crypto.SecureKeys;
import brooklyn.util.crypto.SslTrustUtils;

@SuppressWarnings({"rawtypes","unchecked"})
public class JmxmpClient {

    public void connect(String urlString, Map env) throws MalformedURLException, IOException {
        JMXServiceURL url = new JMXServiceURL(urlString);
        System.out.println("JmxmpClient connecting to "+url);
        JMXConnector jmxc = JMXConnectorFactory.connect(url, env); 
        
        MBeanServerConnection mbsc = jmxc.getMBeanServerConnection(); 
        String domains[] = mbsc.getDomains(); 
        for (int i = 0; i < domains.length; i++) { 
            System.out.println("Domain[" + i + "] = " + domains[i]); 
        } 

        jmxc.close();
    } 

    /** tries to connect to the given JMX url over tls, 
     * optionally using the given keystore (if null using a randomly generated key)
     * and optionally using the given truststore (if null trusting all) */
    public void connectTls(String urlString, KeyStore keyStore, String keyStorePass, KeyStore trustStore) throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, InvalidKeyException, CertificateException, SecurityException, SignatureException, IOException, KeyManagementException { 
        Map env = new LinkedHashMap(); 

        env.put("jmx.remote.profiles", JmxmpAgent.TLS_JMX_REMOTE_PROFILES);

        if (keyStore==null) throw new NullPointerException("keyStore must be supplied");
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); //"SunX509");
        kmf.init(keyStore, (keyStorePass!=null ? keyStorePass : "").toCharArray());

        TrustManager tms = trustStore!=null ? SecureKeys.getTrustManager(trustStore) : SslTrustUtils.TRUST_ALL;

        SSLContext ctx = SSLContext.getInstance("TLSv1");
        ctx.init(kmf.getKeyManagers(), new TrustManager[] { tms }, null);
        SSLSocketFactory ssf = ctx.getSocketFactory(); 
        env.put(JmxmpAgent.TLS_SOCKET_FACTORY_PROPERTY, ssf); 

        connect(urlString, env); 
    }

    public static void main(String[] args) throws Exception {
        new JmxmpClient().connect("service:jmx:jmxmp://localhost:1099", null);
    }
}

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
package brooklyn.entity.java;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import brooklyn.util.collections.MutableMap.Builder;
import brooklyn.util.crypto.FluentKeySigner;
import brooklyn.util.crypto.SecureKeys;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.jmx.jmxmp.JmxmpAgent;
import brooklyn.util.net.Urls;

import com.google.common.base.Preconditions;

public class JmxmpSslSupport {

    final static String BROOKLYN_VERSION = "0.7.0-SNAPSHOT";  // BROOKLYN_VERSION (updated by script)
    
    private final JmxSupport jmxSupport;
    
    private KeyStore agentTrustStore;
    private KeyStore agentKeyStore;
    
    public JmxmpSslSupport(JmxSupport jmxSupport) {
        this.jmxSupport = Preconditions.checkNotNull(jmxSupport);
    }
    
    public String getJmxSslKeyStoreFilePath() {
        return Urls.mergePaths(jmxSupport.getRunDir(), "jmx-keystore");
    }
    
    public String getJmxSslTrustStoreFilePath() {
        return Urls.mergePaths(jmxSupport.getRunDir(), "jmx-truststore");
    }
    
    public void applyAgentJmxJavaSystemProperties(Builder<String, Object> result) {
        result.
            put(JmxmpAgent.USE_SSL_PROPERTY, true).
            put(JmxmpAgent.AUTHENTICATE_CLIENTS_PROPERTY, true).
            // the option below wants a jmxremote.password file; we use certs (above) to authenticate
            put("com.sun.management.jmxremote.authenticate", false);

        result.
            put(JmxmpAgent.JMXMP_KEYSTORE_FILE_PROPERTY, getJmxSslKeyStoreFilePath()).
            put(JmxmpAgent.JMXMP_TRUSTSTORE_FILE_PROPERTY, getJmxSslTrustStoreFilePath());
    }

    public FluentKeySigner getBrooklynRootSigner() {
        // TODO use brooklyn root CA keys etc
        return new FluentKeySigner("brooklyn-root");
    }

    /** builds remote keystores, stores config keys/certs, and copies necessary files across */
    public void install() {
        try {
            // build truststore and keystore
            FluentKeySigner signer = getBrooklynRootSigner();
            KeyPair jmxAgentKey = SecureKeys.newKeyPair();
            X509Certificate jmxAgentCert = signer.newCertificateFor("jmxmp-agent", jmxAgentKey);

            agentKeyStore = SecureKeys.newKeyStore();
            agentKeyStore.setKeyEntry("jmxmp-agent", jmxAgentKey.getPrivate(), 
                    // TODO jmx.ssl.agent.keyPassword
                    "".toCharArray(),
                    new Certificate[] { jmxAgentCert });
            ByteArrayOutputStream agentKeyStoreBytes = new ByteArrayOutputStream();
            agentKeyStore.store(agentKeyStoreBytes, 
                    // TODO jmx.ssl.agent.keyStorePassword
                    "".toCharArray());
            
            agentTrustStore = SecureKeys.newKeyStore();
            agentTrustStore.setCertificateEntry("brooklyn", getJmxAccessCert());
            ByteArrayOutputStream agentTrustStoreBytes = new ByteArrayOutputStream();
            agentTrustStore.store(agentTrustStoreBytes, "".toCharArray());
            
            // install the truststore and keystore
            jmxSupport.getMachine().get().copyTo(new ByteArrayInputStream(agentKeyStoreBytes.toByteArray()), getJmxSslKeyStoreFilePath());
            jmxSupport.getMachine().get().copyTo(new ByteArrayInputStream(agentTrustStoreBytes.toByteArray()), getJmxSslTrustStoreFilePath());
            
            // and rely on JmxSupport to install the agent
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public synchronized Certificate getJmxAccessCert() {
        Certificate cert = jmxSupport.getConfig(UsesJmx.JMX_SSL_ACCESS_CERT);
        if (cert!=null) return cert;
        // TODO load from keyStoreUrl
        KeyPair jmxAccessKey = SecureKeys.newKeyPair();
        X509Certificate jmxAccessCert = getBrooklynRootSigner().newCertificateFor("brooklyn-jmx-access", jmxAccessKey);

        jmxSupport.setConfig(UsesJmx.JMX_SSL_ACCESS_CERT, jmxAccessCert);
        jmxSupport.setConfig(UsesJmx.JMX_SSL_ACCESS_KEY, jmxAccessKey.getPrivate());
        
        return jmxAccessCert;
    }
    
    public synchronized PrivateKey getJmxAccessKey() {
        PrivateKey key = jmxSupport.getConfig(UsesJmx.JMX_SSL_ACCESS_KEY);
        if (key!=null) return key;
        getJmxAccessCert();
        return jmxSupport.getConfig(UsesJmx.JMX_SSL_ACCESS_KEY);
    }

}

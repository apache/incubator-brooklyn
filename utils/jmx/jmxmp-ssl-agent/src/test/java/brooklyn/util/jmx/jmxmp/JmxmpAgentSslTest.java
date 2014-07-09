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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Properties;

import javax.management.remote.JMXConnectorServer;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.extension.AuthorityKeyIdentifierStructure;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.util.crypto.FluentKeySigner;
import brooklyn.util.crypto.SecureKeys;

public class JmxmpAgentSslTest {

    KeyPair caRootKey;
    FluentKeySigner caRootSigner;
    X509Certificate caRootCert;

    KeyPair caChildKey;
    X509Certificate caChildCert;
    FluentKeySigner caChildSigner;

    KeyPair grandchildKey;
    X509Certificate grandchildCert;

    KeyPair child2Key;
    X509Certificate child2Cert;

    KeyPair selfSign1Key;
    X509Certificate selfSign1Cert;

    KeyPair selfSign2Key;
    X509Certificate selfSign2Cert;        

    KeyStore serverKeystore;
    KeyStore serverTruststore;
    KeyStore clientTruststore;
    KeyStore clientKeystore;

    JMXConnectorServer server;
    
    static { Security.addProvider(new BouncyCastleProvider()); }
    
    @BeforeMethod
    public void setup() throws Exception {
        caRootSigner = new FluentKeySigner("ca-root").selfsign();
        caRootKey = caRootSigner.getKey();
        caRootCert = caRootSigner.getAuthorityCertificate();

        caChildKey = SecureKeys.newKeyPair();
        caChildCert = caRootSigner.newCertificateFor("ca-child", caChildKey);
        caChildSigner = new FluentKeySigner("ca-child", caChildKey).
                authorityKeyIdentifier(new AuthorityKeyIdentifierStructure(caChildCert));

        grandchildKey = SecureKeys.newKeyPair();
        grandchildCert = caChildSigner.newCertificateFor("grandchild", grandchildKey);

        child2Key = SecureKeys.newKeyPair();
        child2Cert = 
                caRootSigner.
                newCertificateFor("child-2", child2Key);

        selfSign1Key = SecureKeys.newKeyPair();
        selfSign1Cert = 
                new FluentKeySigner("self-1", selfSign1Key).
                newCertificateFor("self-1", selfSign1Key);

        selfSign2Key = SecureKeys.newKeyPair();
        selfSign2Cert = 
                new FluentKeySigner("self-2", selfSign2Key).
                newCertificateFor("self-2", selfSign2Key);        

        serverKeystore = KeyStore.getInstance(KeyStore.getDefaultType());
        serverKeystore.load(null, null);

        serverTruststore = KeyStore.getInstance(KeyStore.getDefaultType());
        serverTruststore.load(null, null);

        clientTruststore = KeyStore.getInstance(KeyStore.getDefaultType());
        clientTruststore.load(null, null);

        clientKeystore = KeyStore.getInstance(KeyStore.getDefaultType());
        clientKeystore.load(null, null);
    }

    @AfterMethod
    public void teardown() throws Exception {
        if (server!=null) server.stop();
        server = null;
    }
    
    private Properties saveStoresAndGetConnectorProperties() throws 
            KeyStoreException, IOException, NoSuchAlgorithmException,
            CertificateException, FileNotFoundException {
        String keystoreFile = File.createTempFile("server-keystore", ".jmx.test").getAbsolutePath();
        String truststoreFile = File.createTempFile("server-truststore", ".jmx.test").getAbsolutePath();
        if (serverKeystore!=null) serverKeystore.store( new FileOutputStream(keystoreFile), new char[0]);
        if (serverTruststore!=null) serverTruststore.store( new FileOutputStream(truststoreFile), new char[0]);
        Properties p = new Properties();
        p.put(JmxmpAgent.JMXMP_KEYSTORE_FILE_PROPERTY, keystoreFile);
        p.put(JmxmpAgent.JMXMP_TRUSTSTORE_FILE_PROPERTY, truststoreFile);
        p.put(JmxmpAgent.USE_SSL_PROPERTY, "true");
        p.put(JmxmpAgent.AUTHENTICATE_CLIENTS_PROPERTY, "true");
        return p;
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testNoAuth() throws Exception {
        serverKeystore = null;  
        serverTruststore = null;
        clientKeystore = null;
        clientTruststore = null;

        Properties p = saveStoresAndGetConnectorProperties();
        p.put(JmxmpAgent.USE_SSL_PROPERTY, "false");
        p.put(JmxmpAgent.AUTHENTICATE_CLIENTS_PROPERTY, "false");
        
        server = new JmxmpAgent().startJmxmpConnector(p);
        new JmxmpClient().connect("service:jmx:jmxmp://localhost:11099", new LinkedHashMap());
    }

    @SuppressWarnings("rawtypes")
    @Test(expectedExceptions = { IllegalStateException.class })
    public void testAuthWithoutSslFails() throws Exception {
        serverKeystore = null;  
        serverTruststore = null;
        clientKeystore = null;
        clientTruststore = null;

        Properties p = saveStoresAndGetConnectorProperties();
        p.put(JmxmpAgent.USE_SSL_PROPERTY, "false");
        p.put(JmxmpAgent.AUTHENTICATE_CLIENTS_PROPERTY, "true");
        
        server = new JmxmpAgent().startJmxmpConnector(p);
        new JmxmpClient().connect("service:jmx:jmxmp://localhost:11099", new LinkedHashMap());
    }

    @Test
    public void testAllGoodSignatures() throws Exception {
        serverKeystore.setKeyEntry("child-2", child2Key.getPrivate(), new char[]{},  
                new java.security.cert.Certificate[]{ child2Cert, caRootCert });
        serverTruststore.setCertificateEntry("ca-child", caChildCert);
        
        clientKeystore.setKeyEntry("grandchild", grandchildKey.getPrivate(), new char[] {},
                new java.security.cert.Certificate[]{ grandchildCert });
        clientTruststore.setCertificateEntry("ca-root", caRootCert);

        Properties p = saveStoresAndGetConnectorProperties();
        server = new JmxmpAgent().startJmxmpConnector(p);
        new JmxmpClient().connectTls("service:jmx:jmxmp://localhost:11099",
                clientKeystore, "", clientTruststore);
    }

    @Test(expectedExceptions = { Exception.class })
    public void testWrongServerKey() throws Exception {
        /** not a trusted key */
        serverKeystore.setKeyEntry("self-1", selfSign1Key.getPrivate(), new char[]{},  
                new java.security.cert.Certificate[]{ selfSign1Cert });
        serverTruststore.setCertificateEntry("ca-child", caChildCert);
        
        clientKeystore.setKeyEntry("grandchild", grandchildKey.getPrivate(), new char[] {},
                new java.security.cert.Certificate[]{ grandchildCert });
        clientTruststore.setCertificateEntry("ca-root", caRootCert);

        Properties p = saveStoresAndGetConnectorProperties();
        server = new JmxmpAgent().startJmxmpConnector(p);
        new JmxmpClient().connectTls("service:jmx:jmxmp://localhost:11099",
                clientKeystore, "", clientTruststore);
    }

    @Test(expectedExceptions = { Exception.class })
    public void testLyingServerChain() throws Exception {
        /** caChildCert hasn't signed this */
        serverKeystore.setKeyEntry("self-1", selfSign1Key.getPrivate(), new char[]{},  
                new java.security.cert.Certificate[]{ selfSign1Cert, caChildCert });
        serverTruststore.setCertificateEntry("ca-child", caChildCert);
        
        clientKeystore.setKeyEntry("grandchild", grandchildKey.getPrivate(), new char[] {},
                new java.security.cert.Certificate[]{ grandchildCert, caChildCert });
        clientTruststore.setCertificateEntry("ca-root", caRootCert);

        Properties p = saveStoresAndGetConnectorProperties();
        server = new JmxmpAgent().startJmxmpConnector(p);
        new JmxmpClient().connectTls("service:jmx:jmxmp://localhost:11099",
                clientKeystore, "", clientTruststore);
    }

    @Test(expectedExceptions = { Exception.class })
    public void testWrongClientKey() throws Exception {
        serverKeystore.setKeyEntry("child-2", child2Key.getPrivate(), new char[]{},  
                new java.security.cert.Certificate[]{ child2Cert, caRootCert });
        serverTruststore.setCertificateEntry("ca-child", caChildCert);
        
        /** this key should not have access */
        clientKeystore.setKeyEntry("self-1", selfSign1Key.getPrivate(), new char[] {},
                new java.security.cert.Certificate[]{ selfSign1Cert });
        clientTruststore.setCertificateEntry("ca-root", caRootCert);

        Properties p = saveStoresAndGetConnectorProperties();
        server = new JmxmpAgent().startJmxmpConnector(p);
        new JmxmpClient().connectTls("service:jmx:jmxmp://localhost:11099",
                clientKeystore, "", clientTruststore);
    }

    @Test(expectedExceptions = { Exception.class })
    public void testLyingClientChain() throws Exception {
        serverKeystore.setKeyEntry("child-2", child2Key.getPrivate(), new char[]{},  
                new java.security.cert.Certificate[]{ child2Cert, caRootCert });
        serverTruststore.setCertificateEntry("ca-child", caChildCert);
        
        /** caChildCert hasn't signed this */
        clientKeystore.setKeyEntry("self-1", selfSign1Key.getPrivate(), new char[] {},
                new java.security.cert.Certificate[]{ selfSign1Cert, caChildCert });
        clientTruststore.setCertificateEntry("ca-root", caRootCert);

        Properties p = saveStoresAndGetConnectorProperties();
        server = new JmxmpAgent().startJmxmpConnector(p);
        new JmxmpClient().connectTls("service:jmx:jmxmp://localhost:11099",
                clientKeystore, "", clientTruststore);
    }


}

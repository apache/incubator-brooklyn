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
package org.apache.brooklyn.util.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.NoSuchElementException;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import org.apache.brooklyn.util.exceptions.Exceptions;

/**
 * Utility methods for generating and working with keys, with no BC dependencies
 */
public class SecureKeysWithoutBouncyCastle {

    private static KeyPairGenerator defaultKeyPairGenerator = newKeyPairGenerator("RSA", 1024);  

    protected SecureKeysWithoutBouncyCastle() {}

    public static KeyPairGenerator newKeyPairGenerator(String algorithm, int bits) {
        KeyPairGenerator keyPairGenerator;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw Exceptions.propagate(e);
        }
        keyPairGenerator.initialize(bits);
        return keyPairGenerator;
    }
    
    public static KeyPair newKeyPair() {
        return defaultKeyPairGenerator.generateKeyPair();
    }

    public static KeyPair newKeyPair(String algorithm, int bits) {
        return newKeyPairGenerator(algorithm, bits).generateKeyPair();
    }

    /** returns a new keystore, of the default type, and initialized to be empty.
     * (everyone always forgets to load(null,null).) */
    public static KeyStore newKeyStore() {
        try {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            return store;
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    /** returns keystore of default type read from given source */
    public static KeyStore newKeyStore(InputStream source, String passphrase) {
        try {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(source, passphrase!=null ? passphrase.toCharArray() : new char[0]);
            return store;
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    /** see {@link #getTrustManager(KeyStore, Class)}, matching any type */
    public static TrustManager getTrustManager(KeyStore trustStore) {
        return getTrustManager(trustStore, null);
    }
    /** returns the trust manager inferred from trustStore, matching the type (if not null);
     * throws exception if there are none, or if there are multiple */
    @SuppressWarnings("unchecked")
    public static <T extends TrustManager> T getTrustManager(KeyStore trustStore, Class<T> type) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            T result = null;
            for (TrustManager tm: tmf.getTrustManagers()) {
                if (type==null || type.isInstance(tm)) {
                    if (result!=null)
                        throw new IllegalStateException("Multiple trust managers matching "+type+" inferred from "+trustStore);
                    result = (T)tm;
                }
            }
            if (result!=null)
                return result;
            throw new NoSuchElementException("No trust manager matching "+type+" can be inferred from "+trustStore);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    public static X509TrustManager getTrustManager(X509Certificate certificate) {
        try {
            KeyStore ks = newKeyStore();
            ks.setCertificateEntry("", certificate);
            return getTrustManager(ks, X509TrustManager.class);
        } catch (KeyStoreException e) {
            throw Exceptions.propagate(e);
        }
    }

    /** converts a certificate to the canonical implementation, commonly sun.security.x509.X509CertImpl,
     * which is required in some places -- the Bouncy Castle X509 impl is not accepted 
     * (e.g. where certs are chained, passed to trust manager) */
    public static X509Certificate getCanonicalImpl(X509Certificate inCert) {
        try {
            KeyStore store = newKeyStore();
            store.setCertificateEntry("to-canonical", inCert);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            store.store(out, "".toCharArray());

            KeyStore store2 = KeyStore.getInstance(KeyStore.getDefaultType());
            store2.load(new ByteArrayInputStream(out.toByteArray()), "".toCharArray());
            return (X509Certificate) store2.getCertificate("to-canonical");
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public static boolean isCertificateAuthorizedBy(X509Certificate candidate, X509Certificate authority) {
        try {
            candidate = getCanonicalImpl(candidate);
            getTrustManager(authority).checkClientTrusted(new X509Certificate[] { candidate }, "RSA");
            return true;
        } catch (CertificateException e) {
            return false;
        }
    }
    
    public static X500Principal getX500PrincipalWithCommonName(String commonName) {
        return new X500Principal("" + "C=None," + "L=None," + "O=None," + "OU=None," + "CN=" + commonName);
    }
    
}

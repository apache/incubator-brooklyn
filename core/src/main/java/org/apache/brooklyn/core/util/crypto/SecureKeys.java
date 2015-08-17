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
package org.apache.brooklyn.core.util.crypto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;

import org.apache.brooklyn.core.internal.BrooklynInitialization;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.openssl.PasswordFinder;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.crypto.AuthorizedKeysParser;
import brooklyn.util.crypto.SecureKeysWithoutBouncyCastle;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.stream.Streams;

import com.google.common.base.Objects;
import com.google.common.base.Throwables;

/**
 * Utility methods for generating and working with keys,
 * extending the parent class with useful things provided by BouncyCastle crypto library.
 * (Parent class is in a different project where BC is not included as a dependency.)
 */
public class SecureKeys extends SecureKeysWithoutBouncyCastle {

    private static final Logger log = LoggerFactory.getLogger(SecureKeys.class);
    
    static { BrooklynInitialization.initSecureKeysBouncyCastleProvider(); }
    
    public static void initBouncyCastleProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }
    
    public static class PassphraseProblem extends IllegalStateException {
        private static final long serialVersionUID = -3382824813899223447L;
        public PassphraseProblem(String message) { super("Passphrase problem with this key: "+message); }
        public PassphraseProblem(String message, Exception cause) { super("Passphrase problem with this key: "+message, cause); }
    }
    
    private SecureKeys() {}
    
    /** RFC1773 order, with None for other values. Normally prefer X500Principal. */
    public static X509Principal getX509PrincipalWithCommonName(String commonName) {
        return new X509Principal("" + "C=None," + "L=None," + "O=None," + "OU=None," + "CN=" + commonName);
    }

    /** reads RSA or DSA / pem style private key files (viz {@link #toPem(KeyPair)}), extracting also the public key if possible
     * @throws IllegalStateException on errors, in particular {@link PassphraseProblem} if that is the problem */
    public static KeyPair readPem(InputStream input, final String passphrase) {
        // TODO cache is only for fallback "reader" strategy (2015-01); delete when Parser confirmed working
        byte[] cache = Streams.readFully(input);
        input = new ByteArrayInputStream(cache);

        try {
            PEMParser pemParser = new PEMParser(new InputStreamReader(input));

            Object object = pemParser.readObject();
            pemParser.close();

            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            KeyPair kp = null;
            if (object==null) {
                throw new IllegalStateException("PEM parsing failed: missing or invalid data");
            } else if (object instanceof PEMEncryptedKeyPair) {
                if (passphrase==null) throw new PassphraseProblem("passphrase required");
                try {
                    PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build(passphrase.toCharArray());
                    kp = converter.getKeyPair(((PEMEncryptedKeyPair) object).decryptKeyPair(decProv));
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    throw new PassphraseProblem("wrong passphrase", e);
                }
            } else  if (object instanceof PEMKeyPair) {
                kp = converter.getKeyPair((PEMKeyPair) object);
            } else if (object instanceof PrivateKeyInfo) {
                PrivateKey privKey = converter.getPrivateKey((PrivateKeyInfo) object);
                kp = new KeyPair(null, privKey);
            } else {
                throw new IllegalStateException("PEM parser support missing for: "+object);
            }

            return kp;

        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);

            // older code relied on PEMReader, now deprecated
            // replaced with above based on http://stackoverflow.com/questions/14919048/bouncy-castle-pemreader-pemparser
            // passes the same tests (Jan 2015) but leaving the old code as a fallback for the time being 

            input = new ByteArrayInputStream(cache);
            try {
                Security.addProvider(new BouncyCastleProvider());
                @SuppressWarnings("deprecation")
                org.bouncycastle.openssl.PEMReader pr = new org.bouncycastle.openssl.PEMReader(new InputStreamReader(input), new PasswordFinder() {
                    public char[] getPassword() {
                        return passphrase!=null ? passphrase.toCharArray() : new char[0];
                    }
                });
                @SuppressWarnings("deprecation")
                KeyPair result = (KeyPair) pr.readObject();
                pr.close();
                if (result==null)
                    throw Exceptions.propagate(e);
                
                log.warn("PEMParser failed when deprecated PEMReader succeeded, with "+result+"; had: "+e);

                return result;

            } catch (Exception e2) {
                Exceptions.propagateIfFatal(e2);
                throw Exceptions.propagate(e);
            }
        }
    }

    /** because KeyPair.equals is not implemented :( */
    public static boolean equal(KeyPair k1, KeyPair k2) {
        return Objects.equal(k2.getPrivate(), k1.getPrivate()) && Objects.equal(k2.getPublic(), k1.getPublic());
    }

    /** returns the PEM (base64, ie for id_rsa) string for the private key / key pair;
     * this starts -----BEGIN PRIVATE KEY----- and ends similarly, like id_rsa.
     * also see {@link #readPem(InputStream, String)} */
    public static String toPem(KeyPair key) {
        return stringPem(key);
    }

    /** returns id_rsa.pub style file, of public key */
    public static String toPub(KeyPair key) {
        return AuthorizedKeysParser.encodePublicKey(key.getPublic());
    }
    
    /** opposite of {@link #toPub(KeyPair)}, given text */
    public static PublicKey fromPub(String pubText) {
        return AuthorizedKeysParser.decodePublicKey(pubText);
    }

    /** @deprecated since 0.7.0, use {@link #toPem(KeyPair)} */ @Deprecated
    public static String stringPem(KeyPair key) {
        try {
            StringWriter sw = new StringWriter();
            PEMWriter w = new PEMWriter(sw);
            w.writeObject(key);
            w.close();
            return sw.toString();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}

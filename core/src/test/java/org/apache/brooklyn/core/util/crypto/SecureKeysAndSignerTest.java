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

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.apache.brooklyn.core.util.ResourceUtils;
import org.apache.brooklyn.core.util.crypto.FluentKeySigner;
import org.apache.brooklyn.core.util.crypto.SecureKeys;
import org.apache.brooklyn.core.util.crypto.SecureKeys.PassphraseProblem;
import org.apache.brooklyn.util.crypto.AuthorizedKeysParser;
import org.apache.brooklyn.util.os.Os;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.io.Files;

public class SecureKeysAndSignerTest {

    // a bit slow, so marked as integration (but possibly due to leftover rebind-cleanup, benign failures writing to /tmp/xx)
    @Test(groups="Integration")
    public void testGenerateSignedKeys() throws Exception {
        FluentKeySigner signer = new FluentKeySigner("the-root").
            validForYears(2).
            selfsign();
        X509Certificate signerCert = signer.getAuthorityCertificate();

        KeyPair aKey = SecureKeys.newKeyPair();
        X509Certificate aCert = signer.newCertificateFor("A", aKey);
        
        KeyPair bKey = SecureKeys.newKeyPair();
        X509Certificate bCert = signer.newCertificateFor("B", bKey);

        FluentKeySigner selfSigner1 = new FluentKeySigner("self1").selfsign();
        X509Certificate selfCert1 = selfSigner1.getAuthorityCertificate();

        SecureKeys.getTrustManager(aCert).checkClientTrusted(new X509Certificate[] { aCert }, "RSA");
        SecureKeys.getTrustManager(signerCert).checkClientTrusted(new X509Certificate[] { signerCert }, "RSA");
        
        try {
            SecureKeys.getTrustManager(aCert).checkClientTrusted(new X509Certificate[] { bCert }, "RSA");
            Assert.fail("Trust manager for A should not accept B");
        } catch (CertificateException e) { /* expected */ }
        
//        SecureKeys.getTrustManager(signerCert).checkClientTrusted(new X509Certificate[] { aCert }, "RSA");
        // NB, the above failes; we have to convert to a canonical implementation, handled by the following
        
        Assert.assertTrue(SecureKeys.isCertificateAuthorizedBy(signerCert, signerCert));
        Assert.assertTrue(SecureKeys.isCertificateAuthorizedBy(aCert, signerCert));
        Assert.assertTrue(SecureKeys.isCertificateAuthorizedBy(bCert, signerCert));
        Assert.assertFalse(SecureKeys.isCertificateAuthorizedBy(signerCert, aCert));
        Assert.assertFalse(SecureKeys.isCertificateAuthorizedBy(bCert, aCert));
        
        Assert.assertTrue(SecureKeys.isCertificateAuthorizedBy(selfCert1, selfCert1));
        Assert.assertFalse(SecureKeys.isCertificateAuthorizedBy(selfCert1, signerCert));
    }

    @Test
    public void testInjectCertificateAuthority() throws Exception {
        KeyPair caKey = SecureKeys.newKeyPair();
        X509Certificate caCert = new FluentKeySigner("the-root", caKey).selfsign().getAuthorityCertificate();

        FluentKeySigner signer = new FluentKeySigner(caCert, caKey);
        Assert.assertEquals("the-root", signer.getCommonName());
        
        KeyPair aKey = SecureKeys.newKeyPair();
        X509Certificate aCert = signer.newCertificateFor("A", aKey);
        
        Assert.assertTrue(SecureKeys.isCertificateAuthorizedBy(aCert, caCert));
    }

    @Test
    public void testReadRsaKey() throws Exception {
        KeyPair key = SecureKeys.readPem(ResourceUtils.create(this).getResourceFromUrl("classpath://brooklyn/util/crypto/sample_rsa.pem"), null);
        checkNonTrivial(key);
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testReadRsaPublicKeyAsPemFails() throws Exception {
        // should fail; see next test
        SecureKeys.readPem(ResourceUtils.create(this).getResourceFromUrl("classpath://brooklyn/util/crypto/sample_rsa.pem.pub"), null);
    }
    
    @Test
    public void testReadRsaPublicKeyAsAuthKeysWorks() throws Exception {
        PublicKey key = AuthorizedKeysParser.decodePublicKey(
            ResourceUtils.create(this).getResourceAsString("classpath://brooklyn/util/crypto/sample_rsa.pem.pub"));
        KeyPair fromPem = SecureKeys.readPem(ResourceUtils.create(this).getResourceFromUrl("classpath://brooklyn/util/crypto/sample_rsa.pem"), null);        
        Assert.assertEquals(key, fromPem.getPublic());
    }

    @Test
    public void testEncodeDecodeRsaPublicKey() throws Exception {
        String data = ResourceUtils.create(this).getResourceAsString("classpath://brooklyn/util/crypto/sample_rsa.pem.pub");
        PublicKey key = AuthorizedKeysParser.decodePublicKey(data);
        String data2 = AuthorizedKeysParser.encodePublicKey(key);
        Assert.assertTrue(data.contains(data2), "Expected to find '"+data2+"' in '"+data+"'");
        PublicKey key2 = AuthorizedKeysParser.decodePublicKey(data2);
        Assert.assertEquals(key2, key);
    }

    @Test
    public void testEncodeDecodeDsaPublicKey() throws Exception {
        String data = ResourceUtils.create(this).getResourceAsString("classpath://brooklyn/util/crypto/sample_dsa.pem.pub");
        PublicKey key = AuthorizedKeysParser.decodePublicKey(data);
        String data2 = AuthorizedKeysParser.encodePublicKey(key);
        Assert.assertTrue(data.contains(data2), "Expected to find '"+data2+"' in '"+data+"'");
        PublicKey key2 = AuthorizedKeysParser.decodePublicKey(data2);
        Assert.assertEquals(key2, key);
    }

    @Test
    public void testReadDsaKey() throws Exception {
        KeyPair key = SecureKeys.readPem(ResourceUtils.create(this).getResourceFromUrl("classpath://brooklyn/util/crypto/sample_dsa.pem"), null);
        checkNonTrivial(key);
    }

    @Test(expectedExceptions=Exception.class)
    public void testCantReadRsaPassphraseKeyWithoutPassphrase() throws Exception {
        KeyPair key = SecureKeys.readPem(ResourceUtils.create(this).getResourceFromUrl("classpath://brooklyn/util/crypto/sample_rsa_passphrase.pem"), null);
        checkNonTrivial(key);
    }

    @Test(expectedExceptions=PassphraseProblem.class)
    public void testReadRsaPassphraseWithoutKeyFails() throws Exception {
        SecureKeys.readPem(ResourceUtils.create(this).getResourceFromUrl("classpath://brooklyn/util/crypto/sample_rsa_passphrase.pem"), null);
    }
    
    @Test
    public void testReadRsaPassphraseKeyAndWriteWithoutPassphrase() throws Exception {
        KeyPair key = SecureKeys.readPem(ResourceUtils.create(this).getResourceFromUrl("classpath://brooklyn/util/crypto/sample_rsa_passphrase.pem"), "passphrase");
        checkNonTrivial(key);
        File f = Os.newTempFile(getClass(), "brooklyn-sample_rsa_passphrase_without_passphrase.pem");
        Files.write(SecureKeys.stringPem(key), f, Charset.defaultCharset());
        KeyPair key2 = SecureKeys.readPem(new FileInputStream(f), null);
        checkNonTrivial(key2);
        Assert.assertEquals(key2.getPrivate().getEncoded(), key.getPrivate().getEncoded());
        Assert.assertEquals(key2.getPublic().getEncoded(), key.getPublic().getEncoded());
    }

    private void checkNonTrivial(KeyPair key) {
        Assert.assertNotEquals(key.getPrivate().getEncoded().length, 0);
        Assert.assertNotEquals(key.getPublic().getEncoded().length, 0);
    }

}

package brooklyn.util.crypto;

import java.security.KeyPair;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.testng.Assert;
import org.testng.annotations.Test;

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

}

package brooklyn.entity.java;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.crypto.FluentKeySigner;
import brooklyn.util.crypto.SecureKeys;

public class SslKeyConfigTest {

    @Test
    public void testWriteKeyAndCertThenReadThem() throws Exception {
        FluentKeySigner signer = new FluentKeySigner("brooklyn-test").selfsign();
        
        KeyStore ks = SecureKeys.newKeyStore();
        ks.setKeyEntry("key1", 
                signer.getKey().getPrivate(), "s3cr3t".toCharArray(), new Certificate[] { signer.getAuthorityCertificate() });
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ks.store(bytes, "5t0r3".toCharArray());
        
        KeyStore ks2 = SecureKeys.newKeyStore(new ByteArrayInputStream(bytes.toByteArray()), "5t0r3");
        String firstAlias = ks2.aliases().nextElement();
        Assert.assertEquals(firstAlias, "key1");
        Key k = ks2.getKey(firstAlias, "s3cr3t".toCharArray());
        Assert.assertEquals(k, signer.getKey().getPrivate());
        Certificate[] cc = ks2.getCertificateChain(firstAlias);
        Assert.assertEquals(cc, new Certificate[] { signer.getAuthorityCertificate() });
    }
    
}

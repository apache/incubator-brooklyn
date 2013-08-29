package brooklyn.util.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.NoSuchElementException;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.openssl.PasswordFinder;

import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Throwables;

/**
 * Utility methods for generating and working with keys
 */
public class SecureKeys {

    static { Security.addProvider(new BouncyCastleProvider()); }
    
    private static KeyPairGenerator defaultKeyPairGenerator = newKeyPairGenerator("RSA", 1024);  

    private SecureKeys() {}

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
            KeyStore store = SecureKeys.newKeyStore();
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
            candidate = SecureKeys.getCanonicalImpl(candidate);
            getTrustManager(authority).checkClientTrusted(new X509Certificate[] { candidate }, "RSA");
            return true;
        } catch (CertificateException e) {
            return false;
        }
    }
    
    public static X500Principal getX500PrincipalWithCommonName(String commonName) {
        return new X500Principal("" + "C=None," + "L=None," + "O=None," + "OU=None," + "CN=" + commonName);
    }
    
    /** RFC1773 order, with None for other values. Normally prefer X500Principal. */
    public static X509Principal getX509PrincipalWithCommonName(String commonName) {
        return new X509Principal("" + "C=None," + "L=None," + "O=None," + "OU=None," + "CN=" + commonName);
    }

    public static KeyPair readPem(InputStream input, final String passphrase) {
        try {
            Security.addProvider(new BouncyCastleProvider());
            PEMReader pr = new PEMReader(new InputStreamReader(input), new PasswordFinder() {
                public char[] getPassword() {
                    return passphrase!=null ? passphrase.toCharArray() : new char[0];
                }
            });
            KeyPair result = (KeyPair) pr.readObject();
            pr.close();
            return result;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    /** returns the PEM (base64, ie for id_rsa) string for the private key / key pair */
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

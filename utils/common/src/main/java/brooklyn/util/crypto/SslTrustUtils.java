package brooklyn.util.crypto;

import java.net.URLConnection;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class SslTrustUtils {

    /** configures a connection to accept all certificates, if it is for https */
    public static <T extends URLConnection> T trustAll(T connection) {
        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection)connection).setSSLSocketFactory(TrustingSslSocketFactory.getInstance());
            ((HttpsURLConnection)connection).setHostnameVerifier(ALL_HOSTS_VALID);
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
    
    /** trusts no SSL certificates */
    public static final TrustManager TRUST_NONE = new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
            throw new java.security.cert.CertificateException("No clients allowed.");
        }
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
            throw new java.security.cert.CertificateException("No servers allowed.");
        }
    };

    public static class DelegatingTrustManager implements X509TrustManager {
        private final X509TrustManager delegate;
        public DelegatingTrustManager(X509TrustManager delegate) {
            this.delegate = delegate;
        }
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
        }
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }
    }
    
    public static final HostnameVerifier ALL_HOSTS_VALID = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

}

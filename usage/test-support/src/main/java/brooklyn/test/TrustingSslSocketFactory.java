package brooklyn.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.google.common.base.Throwables;

// FIXME copied from brooklyn-core because core not visible here

public class TrustingSslSocketFactory extends SSLSocketFactory {
    
    private static TrustingSslSocketFactory INSTANCE;
    public synchronized static TrustingSslSocketFactory getInstance() {
        if (INSTANCE==null) INSTANCE = new TrustingSslSocketFactory();
        return INSTANCE;
    }
    
    private static SSLContext sslContext; 
    static {
        try {
            sslContext = SSLContext.getInstance("TLS");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** configures a connection to accept all certificates, if it is for https */
    public static <T extends URLConnection> T configure(T connection) {
        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection)connection).setSSLSocketFactory(getInstance());
        }
        return connection;
    }
    
    /** trusts all SSL certificates */
    public static final TrustManager TRUST_ALL = new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
            return null;
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

    public TrustingSslSocketFactory() {
        super();
        try {
            sslContext.init(null, new TrustManager[] { TRUST_ALL }, null);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
        return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
    }

    @Override
    public Socket createSocket() throws IOException {
        return sslContext.getSocketFactory().createSocket();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return sslContext.getSocketFactory().getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return sslContext.getSocketFactory().getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(String arg0, int arg1) throws IOException, UnknownHostException {
        return sslContext.getSocketFactory().createSocket(arg0, arg1);
    }

    @Override
    public Socket createSocket(InetAddress arg0, int arg1) throws IOException {
        return sslContext.getSocketFactory().createSocket(arg0, arg1);
    }

    @Override
    public Socket createSocket(String arg0, int arg1, InetAddress arg2, int arg3) throws IOException, UnknownHostException {
        return sslContext.getSocketFactory().createSocket(arg0, arg1, arg2, arg3);
    }

    @Override
    public Socket createSocket(InetAddress arg0, int arg1, InetAddress arg2, int arg3) throws IOException {
        return sslContext.getSocketFactory().createSocket(arg0, arg1, arg2, arg3);
    }
}
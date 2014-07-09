package brooklyn.entity.webapp;

public class HttpsSslConfig {

    private String keystoreUrl;
    private String keystorePassword;
    private String keyAlias;
    
    public HttpsSslConfig() {
    }
    
    public HttpsSslConfig keystoreUrl(String val) {
        keystoreUrl = val; return this;
    }
    
    public HttpsSslConfig keystorePassword(String val) {
        keystorePassword = val; return this;
    }
    
    public HttpsSslConfig keyAlias(String val) {
        keyAlias = val; return this;
    }
    
    public String getKeystoreUrl() {
        return keystoreUrl;
    }
    
    public String getKeystorePassword() {
        return keystorePassword;
    }
    
    public String getKeyAlias() {
        return keyAlias;
    }
}

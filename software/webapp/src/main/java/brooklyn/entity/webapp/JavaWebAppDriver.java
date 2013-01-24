package brooklyn.entity.webapp;

import java.io.File;
import java.util.List;

import brooklyn.entity.java.JavaSoftwareProcessDriver;

public interface JavaWebAppDriver extends JavaSoftwareProcessDriver {

    List<String> getEnabledProtocols();

    Integer getHttpPort();

    Integer getHttpsPort();

    HttpsSslConfig getHttpsSslConfig();
    
    void deploy(File file);

    void deploy(File f, String targetName);

    /**
     * deploys a URL as a webapp at the appserver;
     * returns a token which can be used as an argument to undeploy,
     * typically the web context with leading slash where the app can be reached (just "/" for ROOT)
     * <p>
     * see {@link JavaWebAppSoftwareProcess#deploy(String, String)} for details of how input filenames are handled
     */
    String deploy(String url, String targetName);
    
    void undeploy(String targetName);
    
    FilenameToWebContextMapper getFilenameContextMapper();
}

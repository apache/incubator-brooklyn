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

    String deploy(String url, String targetName);
}

package brooklyn.entity.webapp;

import brooklyn.entity.basic.lifecycle.JavaSoftwareProcessDriver;

import java.io.File;

public interface JavaWebAppDriver extends JavaSoftwareProcessDriver {

    Integer getHttpPort();

    void deploy(File file);

    void deploy(File f, String targetName);

    void deploy(String url, String targetName);
}

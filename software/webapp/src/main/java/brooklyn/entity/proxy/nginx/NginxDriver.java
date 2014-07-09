package brooklyn.entity.proxy.nginx;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface NginxDriver extends SoftwareProcessDriver {

    String getRunDir();

    String getPidFile();

    boolean isCustomizationCompleted();

}

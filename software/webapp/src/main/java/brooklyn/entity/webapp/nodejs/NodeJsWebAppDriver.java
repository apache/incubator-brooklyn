package brooklyn.entity.webapp.nodejs;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface NodeJsWebAppDriver extends SoftwareProcessDriver {

    Integer getHttpPort();

}

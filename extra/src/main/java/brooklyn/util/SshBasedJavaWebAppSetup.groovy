package brooklyn.util

import java.io.File
import java.util.List

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.basic.lifecycle.SshBasedJavaAppSetup;
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.location.basic.SshMachineLocation

public abstract class SshBasedJavaWebAppSetup extends SshBasedJavaAppSetup {

    int httpPort
    
    public SshBasedJavaWebAppSetup(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected void setEntityAttributes() {
        super.setEntityAttributes()
        def host = entity.getAttribute(Attributes.HOSTNAME)
        entity.setAttribute(Attributes.HTTP_PORT, httpPort)
        entity.setAttribute(JavaWebApp.ROOT_URL, "http://${host}:${httpPort}/")
    }

}

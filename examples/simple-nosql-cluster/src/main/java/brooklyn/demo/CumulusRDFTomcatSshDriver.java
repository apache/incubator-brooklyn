package brooklyn.demo;

import java.util.Map;

import brooklyn.entity.webapp.tomcat.Tomcat7SshDriver;
import brooklyn.entity.webapp.tomcat.TomcatServerImpl;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;

/** CumulusRDF Tomcat driver. */
public class CumulusRDFTomcatSshDriver extends Tomcat7SshDriver {

    public CumulusRDFTomcatSshDriver(TomcatServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    /** Customise with our copied config file. */
    public void customize() {
        super.customize();

        copyTemplate("classpath://cumulus.yaml", "cumulus.yaml");
    }


    /** Add property pointing to cumulus.yaml config file. */
    protected Map getCustomJavaSystemProperties() {
        return MutableMap.of("cumulusrdf.config-file", getRunDir() + "/cumulus.yaml");
    }

}

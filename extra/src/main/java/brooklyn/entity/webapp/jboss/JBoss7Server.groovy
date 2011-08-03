package brooklyn.entity.webapp.jboss

import groovy.lang.MetaClass
import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

class JBoss7Server extends JavaWebApp {

    public static final ConfiguredAttributeSensor<Integer> HTTP_PORT = Attributes.HTTP_PORT
    public static final ConfiguredAttributeSensor<Integer> MANAGEMENT_PORT = 
            [ Integer, "http.managementPort", "Management port", 9990 ]
    
    public JBoss7Server(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }
    
    @Override
    protected void waitForHttpPort() {
        // TODO Auto-generated method stub

    }

    @Override
    protected void initSensors() {
        // TODO Auto-generated method stub

    }

    @Override
    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return JBoss7SshSetup.newInstance(this, machine)
    }

}

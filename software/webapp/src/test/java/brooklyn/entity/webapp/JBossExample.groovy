package brooklyn.entity.webapp

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities;
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.location.basic.LocalhostMachineProvisioningLocation

class JBossExample extends AbstractApplication {

    JBoss7Server s = new JBoss7Server(this, httpPort: "8080+", war:"classpath://hello-world.war");
    
    public static void main(String[] args) {
        def ex = new JBossExample();
        ex.start( [ new LocalhostMachineProvisioningLocation(name:'london') ] )
        Entities.dumpInfo(ex)
    }
    
}

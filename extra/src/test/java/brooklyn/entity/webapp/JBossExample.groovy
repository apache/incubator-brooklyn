package brooklyn.entity.webapp

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.location.basic.LocalhostMachineProvisioningLocation

class JBossExample extends AbstractApplication {

    JBoss7Server s = new JBoss7Server(this, httpPort: 8000, war:"/tmp/hello-world.war");
    
    public static void main(String[] args) {
        def ex = new JBossExample();
        ex.start( [ new LocalhostMachineProvisioningLocation(name:'london') ] )
    }
    
}

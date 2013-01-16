package brooklyn.entity.webapp

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.jboss.JBoss7ServerImpl
import brooklyn.location.basic.LocalhostMachineProvisioningLocation

/**
 * TODO Turn into unit or integration test, or delete
 * 
 * @deprecated This should either be turned into a unit/integration test, or deleted
 */
@Deprecated
class JBossExample extends AbstractApplication {

    JBoss7Server s = new JBoss7ServerImpl(this, httpPort: "8080+", war:"classpath://hello-world.war");
    
    public static void main(String[] args) {
        def ex = new JBossExample();
        ex.start( [ new LocalhostMachineProvisioningLocation(name:'london') ] )
        Entities.dumpInfo(ex)
    }
    
}

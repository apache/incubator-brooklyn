package brooklyn.demo.legacy;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.jboss.JBoss7ServerImpl
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation

/**
 * This example starts one web app on 8080, waits for a keypress, then stops it.
 * 
 * @deprecated in 0.5; see {@link brooklyn.demo.SingleWebServerExample}
 */
@Deprecated
public class SingleWebServerExample extends AbstractApplication {

    public static final Logger LOG = LoggerFactory.getLogger(SingleWebServerExample)

    private static final String WAR_PATH = "classpath://hello-world-webapp.war"

    JBoss7Server web = new JBoss7ServerImpl(this, war: WAR_PATH, httpPort: 8080)

    public static void main(String[] args) {
        SingleWebServerExample app = new SingleWebServerExample();
        Location loc = new LocalhostMachineProvisioningLocation();
        
        LOG.info("created, now starting...");
        app.start([loc]);
        LOG.info("started, visit "+app.web.getAttribute(JBoss7Server.ROOT_URL)+"; waiting for keypress to quit");
        
        System.out.println("---- press enter to quit ----");
        System.in.read();
        LOG.info("now ending...");
        app.stop();
        LOG.info("ended.");
    }
    
}

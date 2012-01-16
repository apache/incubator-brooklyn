package brooklyn.demo

import java.util.List
import java.util.Map

import org.apache.http.util.EntityUtils;
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.basic.Entities;
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService
import brooklyn.entity.group.Cluster
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.entity.webapp.jboss.JBoss6Server
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.jclouds.JcloudsLocationFactory;
import brooklyn.policy.ResizerPolicy

/**
 * Run with:
 *   java -Xmx512m -Xms128m -XX:MaxPermSize=256m -cp target/brooklyn-example-0.2.0-SNAPSHOT-with-dependencies.jar brooklyn.demo.WebClusterExample 
 **/
public class WebClusterExample extends AbstractApplication {
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterExample)
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()

    public static final List<String> DEFAULT_LOCATIONS = [ Locations.LOCALHOST ]

    public static final String WAR_PATH = "classpath://hello-world.war"
    
    public WebClusterExample(Map props=[:]) {
        super(props)
    }
    
//    private DynamicFabric webFabric = new DynamicFabric(this,
//                displayName: 'WebApp fabric',
//                newEntity: this.&newWebCluster);
//
//    private DynamicGroup nginxEntities = new DynamicGroup(this, name: 'Web Fronts', { it in NginxController })
//    private GeoscalingDnsService geoDns = new GeoscalingDnsService(this,
//            displayName: 'Geo-DNS',
//            username: config.getFirst("brooklyn.geoscaling.username", defaultIfNone:'cloudsoft'), 
//            password: config.getFirst("brooklyn.geoscaling.password", failIfNone:true), 
//            primaryDomainName: 'geopaas.org', smartSubdomainName: 'brooklyn').
//        setTargetEntityProvider(nginxEntities)
//        
//    protected ControlledDynamicWebAppCluster newWebCluster(Map flags, Entity owner) {
//        NginxController nginxController = new NginxController(
//            domain:'brooklyn.geopaas.org',
//            port:8000 )
//
//        ControlledDynamicWebAppCluster webCluster = new ControlledDynamicWebAppCluster(flags, owner).configure(
//            name:"WebApp cluster",
//            controller:nginxController,
//            initialSize: 1, 
//            webServerFactory: this.&newWebServer )
//
//        ResizerPolicy policy = new ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND).
//            setSizeRange(1, 5).
//            setMetricRange(10, 100)
//        webCluster.cluster.addPolicy(policy)
//
//        return webCluster
//    }

    NginxController nginxController = new NginxController( 
        domain:'brooklyn.geopaas.org',
        port:8000 )

    ControlledDynamicWebAppCluster webCluster = new ControlledDynamicWebAppCluster(this,
        name:"WebApp cluster",
        war: WAR_PATH,
        controller:nginxController,
        initialSize: 1,
        webServerFactory: this.&newWebServer )

    ResizerPolicy policy = new ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND).
        setSizeRange(1, 5).
        setMetricRange(10, 100);
    
    protected JavaWebAppService newWebServer(Map flags, Entity cluster) {
        return new JBoss7Server(flags, cluster).configure(httpPort: 8080, war: WAR_PATH)
    }

    public static void main(String[] argv) {
        List<Location> locations = 
            Locations.getLocationsById(Arrays.asList(argv) ?: DEFAULT_LOCATIONS)

        WebClusterExample app = new WebClusterExample(name:'Brooklyn WebApp Cluster example')
            
        BrooklynLauncher.manage(app)
        app.start(locations)
        Entities.dumpInfo(app)
        app.webCluster.cluster.addPolicy(app.policy)
    }
    
}

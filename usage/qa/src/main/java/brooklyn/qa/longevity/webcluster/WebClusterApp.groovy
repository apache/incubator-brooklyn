package brooklyn.qa.longevity.webcluster

import java.util.Map

import brooklyn.config.BrooklynProperties
import brooklyn.enricher.CustomAggregatingEnricher
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.jboss.JBoss7ServerFactory
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocationRegistry
import brooklyn.policy.ResizerPolicy
import brooklyn.util.CommandLineUtil

public class WebClusterApp extends AbstractApplication {

    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()

    public static final String DEFAULT_LOCATION = "localhost"

    public static final String WAR_PATH = "classpath://hello-world.war"

    private static final int loadCyclePeriodMs = 2*60*1000L;
    
    public WebClusterApp(Map props=[:]) {
        super(props)
    }

    public static void main(String[] argv) {
        BasicAttributeSensor<Double> sinusoidalLoad = [ Double, "brooklyn.qa.sinusoidalLoad", "Sinusoidal server load" ]
        BasicAttributeSensor<Double> averageLoad = [ Double, "brooklyn.qa.averageLoad", "Average load in cluster" ]
    
        ArrayList args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        List<Location> locations = new LocationRegistry().getLocationsById(args ?: [DEFAULT_LOCATION])

        WebClusterApp app = new WebClusterApp(name:'Brooklyn WebApp Cluster example')
        
        NginxController nginxController = new NginxController(
                // domain: 'webclusterexample.brooklyn.local',
                port:8000)
        
        JBoss7ServerFactory jbossFactory = new JBoss7ServerFactory(httpPort: "8080+", war: WAR_PATH) {
            public JBoss7Server newEntity2(Map flags, Entity owner) {
                JBoss7Server result = super.newEntity2(flags, owner)
                result.addEnricher(new SinusoidalLoadGenerator(sinusoidalLoad, 500L, loadCyclePeriodMs, 1d))
                return result
            }
        }
        
        ControlledDynamicWebAppCluster web = new ControlledDynamicWebAppCluster(app,
            name: "WebApp cluster",
            controller: nginxController,
            initialSize: 1,
            factory: jbossFactory)
        
        
        web.cluster.addEnricher(CustomAggregatingEnricher.getAveragingEnricher([], sinusoidalLoad, averageLoad))
        web.cluster.addPolicy(new ResizerPolicy(averageLoad).
                setSizeRange(1, 3).
                setMetricRange(0.3, 0.7));
        
        BrooklynLauncher.manage(app, port)
        app.start(locations)
        Entities.dumpInfo(app)
    }
}

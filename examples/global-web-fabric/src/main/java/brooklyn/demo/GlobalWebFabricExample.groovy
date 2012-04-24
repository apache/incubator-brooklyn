package brooklyn.demo

import groovy.transform.InheritConstructors

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynProperties
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.basic.Entities
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.ElasticJavaWebAppService
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocationRegistry
import brooklyn.util.CommandLineUtil

@InheritConstructors
class GlobalWebFabricExample extends AbstractApplication {

    public static final Logger log = LoggerFactory.getLogger(GlobalWebFabricExample.class);
    
    public static final String WAR_PATH = "classpath://hello-world-webapp.war";

    static final List<String> DEFAULT_LOCATIONS = [
        "cloudfoundry", 
        "cloudfoundry:https://api.scc1.cloudsoft.carrenza.net/",
        "cloudfoundry:https://api.aws.af.cm/"
        //, "aws-ec2:us-west-1", "aws-ec2:ap-southeast-1"
        ];
        
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()
    
    
    DynamicFabric webFabric = new DynamicFabric(this, factory: new ElasticJavaWebAppService.Factory()).
        setConfig(ElasticJavaWebAppService.ROOT_WAR, WAR_PATH);
    
    private DynamicGroup nginxEntities = new DynamicGroup(this, name: 'Web Fronts (nginx\'s)', { it in NginxController })
    private GeoscalingDnsService geoDns = new GeoscalingDnsService(this,
            displayName: 'GeoScaling DNS',
            username: config.getFirst("brooklyn.geoscaling.username", defaultIfNone:'cloudsoft'),
            password: config.getFirst("brooklyn.geoscaling.password", failIfNone:true),
            primaryDomainName: 'geopaas.org', smartSubdomainName: 'brooklyn').
        setTargetEntityProvider(nginxEntities);

        
    public static void main(String[] argv) {
        ArrayList args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        List<Location> locations = new LocationRegistry().getLocationsById(args ?: DEFAULT_LOCATIONS)

        GlobalWebFabricExample app = new GlobalWebFabricExample(name: 'Brooklyn Global Web Fabric Example');
            
        BrooklynLauncher.manage(app, port)
        app.start(locations)
        Entities.dumpInfo(app)
    }
    
        
}

package brooklyn.extras.whirr

import java.util.List

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynProperties
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.Entities
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService
import brooklyn.entity.group.AbstractController
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.webapp.ElasticJavaWebAppService
import brooklyn.event.basic.DependentConfiguration
import brooklyn.extras.cloudfoundry.CloudFoundryJavaWebAppCluster
import brooklyn.extras.whirr.hadoop.WhirrHadoopCluster
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocationRegistry
import brooklyn.util.CommandLineUtil

public class WebFabricWithHadoopExample extends AbstractApplication {

    private static final Logger LOG = LoggerFactory.getLogger(WhirrHadoopExample.class);

    static final List<String> DEFAULT_LOCATIONS = [
        "aws-ec2:eu-west-1",
        "aws-ec2:ap-southeast-1",
        "aws-ec2:us-west-1",
//            "cloudfoundry:https://api.aws.af.cm/",
    ];

    public static final String WAR_PATH = "classpath://hello-world-hadoop-webapp.war";
            
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()
    
    
    DynamicFabric webFabric = new DynamicFabric(this, name: "Web Fabric", factory: new ElasticJavaWebAppService.Factory());
    
    GeoscalingDnsService geoDns = new GeoscalingDnsService(this, name: "GeoScaling DNS",
            username: config.getFirst("brooklyn.geoscaling.username", failIfNone:true),
            password: config.getFirst("brooklyn.geoscaling.password", failIfNone:true),
            primaryDomainName: config.getFirst("brooklyn.geoscaling.primaryDomain", failIfNone:true),
            smartSubdomainName: 'brooklyn');
    
    WhirrHadoopCluster cluster = new WhirrHadoopCluster(this, size: 2, memory: 2048, name: "brooklyn-hadoop-example")
    
    {
        //specify the WAR file to use
        webFabric.setConfig(ElasticJavaWebAppService.ROOT_WAR, WAR_PATH);
        //load-balancer instances must run on 80 to work with GeoDNS (default is 8000)
        webFabric.setConfig(AbstractController.PROXY_HTTP_PORT, 80);
        //CloudFoundry requires to be told what URL it should listen to, which is chosen by the GeoDNS service
        webFabric.setConfig(CloudFoundryJavaWebAppCluster.HOSTNAME_TO_USE_FOR_URL,
            DependentConfiguration.attributeWhenReady(geoDns, Attributes.HOSTNAME));

        //tell GeoDNS what to monitor
        geoDns.setTargetEntityProvider(webFabric);
    }

    start();
        
    public static void main(String[] argv) {
        ArrayList args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        List<Location> locations = new LocationRegistry().getLocationsById(args ?: DEFAULT_LOCATIONS)

        WebFabricWithHadoopExample app = new WebFabricWithHadoopExample(name: 'Brooklyn Global Web Fabric with Hadoop Example');
            
        BrooklynLauncher.manage(app, port)
        app.start(locations)
        Entities.dumpInfo(app)
    }
    
}

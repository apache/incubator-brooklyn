package brooklyn.demo;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.StringConfigMap;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService;
import brooklyn.entity.group.DynamicFabric;
import brooklyn.entity.proxy.AbstractController;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.webapp.ElasticJavaWebAppService;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.extras.cloudfoundry.CloudFoundryJavaWebAppCluster;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.launcher.BrooklynServerDetails;
import brooklyn.location.Location;
import brooklyn.location.basic.LocationRegistry;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.CommandLineUtil;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

class GlobalWebFabricExample extends ApplicationBuilder {

    public static final Logger log = LoggerFactory.getLogger(GlobalWebFabricExample.class);
    
    public static final String WAR_PATH = "classpath://hello-world-webapp.war";
    
    static final List<String> DEFAULT_LOCATIONS = ImmutableList.of(
            "aws-ec2:eu-west-1",
            "aws-ec2:ap-southeast-1",
            "aws-ec2:us-west-1" 
//            "cloudfoundry:https://api.aws.af.cm/",
        );
    
    protected void doBuild() {
        StringConfigMap config = getManagementContext().getConfig();
        
        GeoscalingDnsService geoDns = createChild(BasicEntitySpec.newInstance(GeoscalingDnsService.class)
                .displayName("GeoScaling DNS")
                .configure("username", checkNotNull(config.getFirst("brooklyn.geoscaling.username"), "username"))
                .configure("password", checkNotNull(config.getFirst("brooklyn.geoscaling.password"), "password"))
                .configure("primaryDomainName", checkNotNull(config.getFirst("brooklyn.geoscaling.primaryDomain"), "primaryDomain")) 
                .configure("smartSubdomainName", "brooklyn"));
        
        DynamicFabric webFabric = createChild(BasicEntitySpec.newInstance(DynamicFabric.class)
                .displayName("Web Fabric")
                .configure(DynamicFabric.FACTORY, new ElasticJavaWebAppService.Factory())
                
                //specify the WAR file to use
                .configure(ElasticJavaWebAppService.ROOT_WAR, WAR_PATH)
                
                //load-balancer instances must run on 80 to work with GeoDNS (default is 8000)
                .configure(AbstractController.PROXY_HTTP_PORT, PortRanges.fromInteger(80))
                
                //CloudFoundry requires to be told what URL it should listen to, which is chosen by the GeoDNS service
                .configure(CloudFoundryJavaWebAppCluster.HOSTNAME_TO_USE_FOR_URL,
                        DependentConfiguration.attributeWhenReady(geoDns, Attributes.HOSTNAME)));

        //tell GeoDNS what to monitor
        geoDns.setTargetEntityProvider(webFabric);
    }

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String locations = CommandLineUtil.getCommandLineOption(args, "--locations", Joiner.on(",").join(DEFAULT_LOCATIONS));

        BrooklynServerDetails server = BrooklynLauncher.newLauncher()
                .webconsolePort(port)
                .launch();

        // TODO instead use server.getManagementContext().getLocationRegistry().resolve(location)
        List<Location> locs = new LocationRegistry().getLocationsById(Arrays.asList(locations));

        BasicApplication app = (BasicApplication) new GlobalWebFabricExample()
                .appDisplayName("Brooklyn Global Web Fabric Example")
                .manage(server.getManagementContext());
        
        app.start(locs);
        
        Entities.dumpInfo(app);
    }
}

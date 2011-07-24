package brooklyn.demo

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.WebAppRunner
import brooklyn.location.Location
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.aws.AwsLocation
import brooklyn.location.basic.aws.AwsLocationFactory
import brooklyn.management.internal.AbstractManagementContext

public class Demo {
    public static final Logger LOG = LoggerFactory.getLogger(Demo)

    private static final Map DEFAULT_IMAGE_ID_PER_REGION = [
            "eu-west-1":"ami-89def4fd",
            "us-east-1":"ami-2342a94a",
            "us-west-1":"ami-25df8e60",
            "ap-southeast-1":"ami-21c2bd73",
            "ap-northeast-1":"ami-f0e842f1"]
    
    public static void main(String[] args) {
        // Obtain Brooklyn locations from our factory class
        List<Location> locations = []
        AwsLocationFactory factory = Locations.newAwsLocationFactory()
        ["eu-west-1", "us-east-1", "us-west-1", "ap-southeast-1", "ap-northeast-1"].each {
            String regionName = it
            String imageId = regionName+"/"+DEFAULT_IMAGE_ID_PER_REGION.get(regionName)
            AwsLocation result = factory.newLocation(regionName)
            result.setTagMapping([
                    (TomcatServer.class.getName()):[
                            imageId:imageId,
                            securityGroups:["brooklyn-all"]],
                    (NginxController.class.getName()):[
                            imageId:imageId,
                            securityGroups:["brooklyn-all"]]])
            
            locations.add(result)
        }
//        FixedListMachineProvisioningLocation montereyEastLocation = Locations.newMontereyEastLocation()
        MachineProvisioningLocation montereyEdinburghLocation = Locations.newMontereyEdinburghLocation()
        
        // Initialize the Spring Travel application entity
        SpringTravel app = new SpringTravel(name:'brooklyn-wide-area-demo', displayName:'Brooklyn Wide-Area Spring Travel Demo Application')

        // Locate the management context
        AbstractManagementContext context = app.getManagementContext()
        context.manage(app)

        // Start the web console service
        WebAppRunner web;
        try {
            web = new WebAppRunner(context)
            web.start()
        } catch (Exception e) {
            LOG.warn("Failed to start web-console", e)
        }
        
        // Start the application
        app.start(locations)
        
        addShutdownHook {
            app?.stop()
            web?.stop()
        }
    }
}

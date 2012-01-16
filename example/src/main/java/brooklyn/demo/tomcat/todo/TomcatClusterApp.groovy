package brooklyn.demo.tomcat.todo

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.location.basic.jclouds.JcloudsLocationFactory


/** Setup properties as per TomcatFabricApp description. */
public class TomcatClusterApp extends AbstractApplication {
	
    static BrooklynProperties sysProps = BrooklynProperties.Factory.newWithSystemAndEnvironment().addFromUrl("file:///~/brooklyn.properties");
    
	DynamicWebAppCluster cluster = new DynamicWebAppCluster(
		owner : this,
		initialSize: 2,
		newEntity: { properties -> new TomcatServer(properties) },
		httpPort: 8080, 
		war: "/path/to/booking-mvc.war")

	public static void main(String[] argv) {
		TomcatClusterApp demo = new TomcatClusterApp(displayName : "tomcat cluster example")
		BrooklynLauncher.manage(demo)

		JcloudsLocationFactory locFactory = JcloudsLocationFactory.newAmazonWebServicesInstance(sysProps);
		JcloudsLocation loc = locFactory.newLocation("us-west-1")
        
		demo.start([loc])
	}
}

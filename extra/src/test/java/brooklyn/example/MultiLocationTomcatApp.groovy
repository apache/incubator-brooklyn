package brooklyn.example

import brooklyn.entity.AbstractApplication
import brooklyn.entity.group.Fabric
import brooklyn.entity.webapp.tomcat.TomcatFabric
import brooklyn.example.PretendLocations.MockLocation
import brooklyn.util.internal.EntityNavigationUtils

public class MultiLocationTomcatApp extends AbstractApplication {
    Fabric tc = new TomcatFabric(displayName:'SpringTravelWebApp', war:'spring-travel.war', this);

	public static void main(String[] args) {
		def app = new MultiLocationTomcatApp()
		app.tc.template.initialSize = 2  //2 web nodes per region 
				
		EntityNavigationUtils.dump(app, "before start:  ")
		
		app.start(locations:[new MockLocation(displayName:'place-1'),new MockLocation(displayName:'place-2')])
		
		EntityNavigationUtils.dump(app, "after start:  ")
		//should add another
		app.start(location:[new MockLocation(displayName:'place-3')])
		//should skip
		app.start(locations:[new MockLocation(displayName:'place-3')])
		EntityNavigationUtils.dump(app, "after additional start:  ")
		
	}

}
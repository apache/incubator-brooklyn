package org.overpaas.example

import org.overpaas.entities.AbstractApplication
import org.overpaas.entities.Fabric
import org.overpaas.example.PretendLocations.MockLocation
import org.overpaas.util.EntityNavigationUtils
import org.overpaas.web.tomcat.TomcatFabric

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
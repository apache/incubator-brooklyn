package org.overpaas.example

import org.overpaas.entities.AbstractApplication
import org.overpaas.entities.Fabric
import org.overpaas.example.PretendLocations.AmazonLocation
import org.overpaas.example.PretendLocations.GemfireFabric
import org.overpaas.example.PretendLocations.MontereyFabric
import org.overpaas.example.PretendLocations.MontereyLatencyOptimisationPolicy
import org.overpaas.example.PretendLocations.VcloudLocation
import org.overpaas.web.tomcat.TomcatFabric

public class BigAppBixby extends AbstractApplication {
    Fabric tc = new TomcatFabric(name:'SpringTravelWebApp', war:'spring-travel.war', this);
	
	MontereyFabric mm = new MontereyFabric(name:'SpringTravelBooking', 
		osgi:['api','impl'].collect { 'com.cloudsoft.spring.booking.'+it }, this)
	
	GemfireFabric gf= new GemfireFabric(name:'SpringTravelGemfire', this)

	{
		application.tc.webCluster.template.jvmProperties << ["monterey.urls":valueWhenReady({ m.mgmtPlaneUrls })]
		application.mm.jvmProperties << ["gemfire.urls":valueWhenReady({ m.mgmtPlaneUrls })]
	}
	
	public static void main(String[] args) {
		def app = new BigAppBixby()
		app.tc.webCluster.template.initialSize = 2  //2 web nodes per region 
		app.mm.policy << new MontereyLatencyOptimisationPolicy()
		installSecretCredentials(app.properties)
		app.start(location:[new VcloudLocation(id:"vcloudmgr.monterey-west.cloudsoftcorp.com"), new AmazonLocation(id:"US-East")])
	}
	
}

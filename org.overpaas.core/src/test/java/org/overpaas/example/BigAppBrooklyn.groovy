package org.overpaas.example

import org.overpaas.entities.AbstractApplication
import org.overpaas.entities.Fabric
import org.overpaas.example.PretendLocations.AmazonLocation
import org.overpaas.example.PretendLocations.InfinispanFabric
import org.overpaas.example.PretendLocations.JBossFabric
import org.overpaas.example.PretendLocations.MontereyFabric
import org.overpaas.example.PretendLocations.MontereyLatencyOptimisationPolicy
import org.overpaas.example.PretendLocations.VcloudLocation


public class BigAppBrooklyn extends AbstractApplication {
    Fabric jb = new JBossFabric(displayName:'SeamBookingWebApp', war:'seam-booking.war', this);
	
	MontereyFabric mm = new MontereyFabric(displayName:'SeamBookingTransactions', 
		osgi:['api','impl'].collect { 'com.cloudsoft.seam.booking.'+it }, this);
	
	InfinispanFabric inf = new InfinispanFabric(displayName:'SeamBookingData', this);

	{
		app.jb.webCluster.template.jvmProperties << ["monterey.urls":valueWhenReady({ mm.mgmtPlaneUrls })]
		app.mm.jvmProperties << ["infinispan.urls":valueWhenReady({ inf.urls })]
	}
	
	public static void main(String[] args) {
		def app = new BigAppBrooklyn()
		app.tc.webCluster.template.initialSize = 2  //2 web nodes per region 
		app.mm.policy << new MontereyLatencyOptimisationPolicy()
		injectSecretCredentials(app.properties)
		app.start(location:[new VcloudLocation(id:"vcloudmgr.monterey-west.cloudsoftcorp.com"), new AmazonLocation(id:"US-East")])
	}
	
}

package brooklyn.earlythoughts

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.group.Fabric
import brooklyn.earlythoughts.PretendLocations.AmazonLocation
import brooklyn.earlythoughts.PretendLocations.InfinispanFabric
import brooklyn.earlythoughts.PretendLocations.JBossFabric
import brooklyn.earlythoughts.PretendLocations.MontereyFabric
import brooklyn.earlythoughts.PretendLocations.MontereyLatencyOptimisationPolicy
import brooklyn.earlythoughts.PretendLocations.VcloudLocation

public class BigAppBrooklyn extends AbstractApplication {

    // FIXME Aspirational for what simple app definition would look like
    
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

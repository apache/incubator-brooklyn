package brooklyn.earlythoughts

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.group.Fabric
import brooklyn.earlythoughts.PretendLocations.AmazonLocation
import brooklyn.earlythoughts.PretendLocations.TomcatFabric
import brooklyn.earlythoughts.PretendLocations.GemfireFabric
import brooklyn.earlythoughts.PretendLocations.MontereyFabric
import brooklyn.earlythoughts.PretendLocations.MontereyLatencyOptimisationPolicy
import brooklyn.earlythoughts.PretendLocations.VcloudLocation

public class BigAppBixby extends AbstractApplication {

    // FIXME Aspirational for what simple app definition would look like
    
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
        app.start([new VcloudLocation(id:"vcloudmgr.monterey-west.cloudsoftcorp.com"), new AmazonLocation(id:"US-East")])
    }
    
}

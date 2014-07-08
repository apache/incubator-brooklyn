/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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

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

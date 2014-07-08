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
package brooklyn.location.geo;

import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Objects;

import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;

public class HostGeoLookupIntegrationTest {

    public static final Logger log = LoggerFactory.getLogger(HostGeoLookupIntegrationTest.class);
    
    @Test(groups = "Integration")
    public void testLocalhostGetsLocation() throws Exception {
        SshMachineLocation l = new LocalhostMachineProvisioningLocation().obtain();
        HostGeoInfo geo = HostGeoInfo.fromLocation(l);
        log.info("localhost is in "+geo);
        Assert.assertNotNull(geo, "couldn't load data; must be online and with credit with the HostGeoLookup impl (e.g. GeoBytes)");
        Assert.assertTrue(-90 <= geo.latitude && geo.latitude <= 90); 
    }

    @Test(groups = "Integration")
    public void testGeobytesLookup() throws Exception {
        HostGeoInfo geo = new GeoBytesHostGeoLookup().getHostGeoInfo(InetAddress.getByName("geobytes.com"));
        Assert.assertEquals(geo.displayName, "Baltimore (US)");
        Assert.assertEquals(geo.latitude, 39.2894, 0.1);
        Assert.assertEquals(geo.longitude, -76.6384, 0.1);
    }

    @Test(groups = "Integration")
    public void testUtraceLookup() throws Exception {
        HostGeoInfo geo = new UtraceHostGeoLookup().getHostGeoInfo(InetAddress.getByName("utrace.de"));
        Assert.assertTrue(geo.displayName.contains("(DE)"));
        Assert.assertEquals(geo.latitude, 51, 2);
        Assert.assertEquals(geo.longitude, 9, 5);
    }

    @Test(groups = "Integration")
    public void testMaxmindLookup() throws Exception {
        HostGeoInfo geo = new MaxMindHostGeoLookup().getHostGeoInfo(InetAddress.getByName("maxmind.com"));
        log.info("maxmind.com at "+geo);
        
        // used to be Washington; now Dalas - in case this is temporary failover will accept either!
        // Also saw variation in lat/lon reported, so happy to within one degree now.
//      Assert.assertEquals(geo.displayName, "Washington, DC (US)");
//      Assert.assertEquals(geo.latitude, 38.90, 0.1);
//      Assert.assertEquals(geo.longitude, -77.02, 0.1);
        
        Assert.assertTrue(Objects.equal(geo.displayName, "Washington, DC (US)") || Objects.equal(geo.displayName, "Dallas, TX (US)"), "name="+geo.displayName);
        Assert.assertTrue(Math.abs(geo.latitude - 38.90) <= 1 || Math.abs(geo.latitude - 32.78) <= 1, "lat="+geo.latitude);
        Assert.assertTrue(Math.abs(geo.longitude - -77.02) <= 1 || Math.abs(geo.longitude - -96.82) <= 1, "lon="+geo.longitude);
    }
}

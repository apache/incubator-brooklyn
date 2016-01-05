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
package org.apache.brooklyn.core.location.geo;

import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.apache.brooklyn.core.location.geo.GeoBytesHostGeoLookup;
import org.apache.brooklyn.core.location.geo.HostGeoInfo;
import org.apache.brooklyn.core.location.geo.MaxMind2HostGeoLookup;
import org.apache.brooklyn.core.location.geo.UtraceHostGeoLookup;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.base.Objects;

public class HostGeoLookupIntegrationTest {

    public static final Logger log = LoggerFactory.getLogger(HostGeoLookupIntegrationTest.class);
    
    // Needs fast network connectivity to figure out the external IP. If response not returned in 2s fails.
    @Test(groups = "Integration")
    public void testLocalhostGetsLocation() throws Exception {
        LocalhostMachineProvisioningLocation ll = new LocalhostMachineProvisioningLocation();
        SshMachineLocation l = ll.obtain();
        HostGeoInfo geo = HostGeoInfo.fromLocation(l);
        Assert.assertNotNull(geo, "host lookup unavailable - is the maxmind database installed? or else network unavailable or too slow?");
        log.info("localhost is in "+geo);
        Assert.assertNotNull(geo, "couldn't load data; must have a valid HostGeoLookup impl (e.g. MaxMind installed, or online and with Utrace credit)");
        Assert.assertTrue(-90 <= geo.latitude && geo.latitude <= 90); 
        ll.close();
    }

    @Deprecated // see GeoBytesHostGeoLookup - their API changed
    @Test(groups = "Integration", enabled=false)
    public void testGeobytesLookup() throws Exception {
        HostGeoInfo geo = new GeoBytesHostGeoLookup().getHostGeoInfo(InetAddress.getByName("geobytes.com"));
        Assert.assertNotNull(geo, "host lookup unavailable");
        Assert.assertEquals(geo.displayName, "Baltimore (US)");
        Assert.assertEquals(geo.latitude, 39.2894, 0.1);
        Assert.assertEquals(geo.longitude, -76.6384, 0.1);
    }

    @Test(groups = "Integration")
    public void testUtraceLookup() throws Exception {
        // The test times out in a VM - VirtualBox + Ubuntu Vivid, possibly due to proxy usage?
        // Increase the timeout so we can actually test it's working correctly, regardless of test environment.
        HostGeoInfo geo = new UtraceHostGeoLookup().getHostGeoInfo(InetAddress.getByName("utrace.de"), Duration.THIRTY_SECONDS);
        Assert.assertNotNull(geo, "host lookup unavailable - maybe network not available ");
        Assert.assertTrue(geo.displayName.contains("(DE)"));
        Assert.assertEquals(geo.latitude, 51, 2);
        Assert.assertEquals(geo.longitude, 9, 5);
    }

    @Test(groups = "Integration")  // only works if maxmind database is installed to ~/.brooklyn/
    public void testMaxmindLookup() throws Exception {
        HostGeoInfo geo = new MaxMind2HostGeoLookup().getHostGeoInfo(InetAddress.getByName("maxmind.com"));
        Assert.assertNotNull(geo, "host lookup unavailable - is the maxmind database installed?");
        log.info("maxmind.com at "+geo);
        
        // used to be Washington; now Dallas - in case this changes again, we will accept either!
        // also have seen variation in lat/lon reported, so happy to within one degree now
        Assert.assertTrue(Objects.equal(geo.displayName, "Washington, DC (US)") || Objects.equal(geo.displayName, "Dallas, TX (US)"), "name="+geo.displayName);
        Assert.assertTrue(Math.abs(geo.latitude - 38.90) <= 1 || Math.abs(geo.latitude - 32.78) <= 1, "lat="+geo.latitude);
        Assert.assertTrue(Math.abs(geo.longitude - -77.02) <= 1 || Math.abs(geo.longitude - -96.82) <= 1, "lon="+geo.longitude);
    }
}

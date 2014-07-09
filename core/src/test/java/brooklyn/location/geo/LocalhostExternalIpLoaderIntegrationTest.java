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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.collect.Sets;

public class LocalhostExternalIpLoaderIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(LocalhostExternalIpLoaderIntegrationTest.class);

    @Test(groups = "Integration")
    public void testHostsAgreeOnExternalIp() {
        Set<String> ips = Sets.newHashSet();
        for (String url : LocalhostExternalIpLoader.getIpAddressWebsites()) {
            String ip = LocalhostExternalIpLoader.getIpAddressFrom(url);
            LOG.debug("IP from {}: {}", url, ip);
            ips.add(ip);
        }
        assertEquals(ips.size(), 1, "Expected all IP suppliers to agree on the external IP address of Brooklyn. " +
                "Check logs for source responses. ips=" + ips);
    }

    @Test(groups = "Integration")
    public void testLoadExternalIp() {
        assertNotNull(LocalhostExternalIpLoader.getLocalhostIpWaiting());
    }

}

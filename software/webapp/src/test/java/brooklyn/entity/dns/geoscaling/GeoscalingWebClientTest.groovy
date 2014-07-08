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
package brooklyn.entity.dns.geoscaling

import static brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_CITY_INFO
import static brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_COUNTRY_INFO
import static brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_EXTRA_INFO
import static brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_NETWORK_INFO
import static brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_UPTIME_INFO
import static org.testng.AssertJUnit.*

import org.testng.annotations.Test

import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.Domain
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.SmartSubdomain

/**
 * {@link GeoscalingWebClient} unit tests.
 */
class GeoscalingWebClientTest {
    
    private final static String USERNAME = "cloudsoft";
    private final static String PASSWORD = "cl0uds0ft";
    private final static String PRIMARY_DOMAIN = "domain"+((int)(Math.random()*10000))+".test.org";
    private final static String SUBDOMAIN = "subdomain"+((int)(Math.random()*10000));
    private final static String DEFAULT_SCRIPT = 'output[] = array("fail");'
    
    
    @Test(groups = "Integration")
    public void testSimpleNames() {
        testWebClient(PRIMARY_DOMAIN, SUBDOMAIN);
    }
    
    @Test(groups = "Integration")
    public void testMixedCaseNames() {
        testWebClient("MixedCase-"+PRIMARY_DOMAIN, "MixedCase-"+SUBDOMAIN);
    }
    
    public void testWebClient(String primaryDomainName, String smartSubdomainName) {
        GeoscalingWebClient geoscaling = new GeoscalingWebClient();
        geoscaling.login(USERNAME, PASSWORD);
        
        assertNull(geoscaling.getPrimaryDomain(primaryDomainName));
        geoscaling.createPrimaryDomain(primaryDomainName);
        Domain domain = geoscaling.getPrimaryDomain(primaryDomainName);
        assertNotNull(domain);
        
        assertNull(domain.getSmartSubdomain(smartSubdomainName));
        domain.createSmartSubdomain(smartSubdomainName);
        SmartSubdomain smartSubdomain = domain.getSmartSubdomain(smartSubdomainName);
        assertNotNull(smartSubdomain);
        
        smartSubdomain.configure(
            PROVIDE_NETWORK_INFO | PROVIDE_CITY_INFO | PROVIDE_COUNTRY_INFO | PROVIDE_EXTRA_INFO | PROVIDE_UPTIME_INFO,
            DEFAULT_SCRIPT);
        
        // TODO: read-back config and verify is as expected?
        // TODO: send actual config, test ping/dig from multiple locations?
        // TODO: rename subdomain
        
        smartSubdomain.delete();
        assertNull(domain.getSmartSubdomain(smartSubdomainName));
        domain.delete();
        assertNull(geoscaling.getPrimaryDomain(primaryDomainName));
        
        geoscaling.logout();
    }
    
}

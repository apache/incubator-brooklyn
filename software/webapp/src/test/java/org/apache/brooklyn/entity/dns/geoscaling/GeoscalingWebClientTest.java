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
package org.apache.brooklyn.entity.dns.geoscaling;

import static org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_CITY_INFO;
import static org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_COUNTRY_INFO;
import static org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_EXTRA_INFO;
import static org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_NETWORK_INFO;
import static org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_UPTIME_INFO;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import org.apache.brooklyn.core.util.http.HttpTool;
import org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient;
import org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient.Domain;
import org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient.SmartSubdomain;
import org.apache.http.client.HttpClient;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.util.text.Strings;

/**
 * {@link GeoscalingWebClient} unit tests.
 */
public class GeoscalingWebClientTest {
    
    private final static String GEOSCALING_URL = "https://www.geoscaling.com";
    private final static String USERNAME = "cloudsoft";
    private final static String PASSWORD = "cl0uds0ft";
    
    private final static String PRIMARY_DOMAIN = "domain-" + Strings.makeRandomId(5) + ".test.org";
    private final static String SUBDOMAIN = "subdomain-" + Strings.makeRandomId(5);
    
    private final static String DEFAULT_SCRIPT = "output[] = array(\"fail\");";
    
    private GeoscalingWebClient geoscaling;
    
    private Domain domain;
    private SmartSubdomain smartSubdomain;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        // Insecurely use "trustAll" so that don't need to import signature into trust store
        // before test will work on jenkins machine.
        HttpClient httpClient = HttpTool.httpClientBuilder().uri(GEOSCALING_URL).trustAll().build();
        geoscaling = new GeoscalingWebClient(httpClient);
        geoscaling.login(USERNAME, PASSWORD);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (smartSubdomain != null)
            smartSubdomain.delete();
        
        if (domain != null)
            domain.delete();
        
        if (geoscaling != null)
            geoscaling.logout();
    }
    
    @Test(groups = "Integration")
    public void testSimpleNames() {
        testWebClient(PRIMARY_DOMAIN, SUBDOMAIN);
    }
    
    @Test(groups = "Integration")
    public void testMixedCaseNames() {
        testWebClient("MixedCase-"+PRIMARY_DOMAIN, "MixedCase-"+SUBDOMAIN);
    }
    
    public void testWebClient(String primaryDomainName, String smartSubdomainName) {
        assertNull(geoscaling.getPrimaryDomain(primaryDomainName));
        geoscaling.createPrimaryDomain(primaryDomainName);
        domain = geoscaling.getPrimaryDomain(primaryDomainName);
        assertNotNull(domain);
        
        assertNull(domain.getSmartSubdomain(smartSubdomainName));
        domain.createSmartSubdomain(smartSubdomainName);
        smartSubdomain = domain.getSmartSubdomain(smartSubdomainName);
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

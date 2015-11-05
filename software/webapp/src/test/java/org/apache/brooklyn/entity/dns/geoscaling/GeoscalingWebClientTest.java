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

import org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient.Domain;
import org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient.SmartSubdomain;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.brooklyn.util.text.Strings;
import org.apache.http.client.HttpClient;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * {@link GeoscalingWebClient} unit tests.
 */
/*
Exception java.lang.RuntimeException

Message: Failed to log-in to GeoScaling service: javax.net.ssl.SSLHandshakeException: Received fatal alert: handshake_failure
Stacktrace:


at org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient.login(GeoscalingWebClient.java:208)
at org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClientTest.setUp(GeoscalingWebClientTest.java:64)
at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)
at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
at java.lang.reflect.Method.invoke(Method.java:606)
at org.testng.internal.MethodInvocationHelper.invokeMethod(MethodInvocationHelper.java:84)
at org.testng.internal.Invoker.invokeConfigurationMethod(Invoker.java:564)
at org.testng.internal.Invoker.invokeConfigurations(Invoker.java:213)
at org.testng.internal.Invoker.invokeMethod(Invoker.java:653)
at org.testng.internal.Invoker.invokeTestMethod(Invoker.java:901)
at org.testng.internal.Invoker.invokeTestMethods(Invoker.java:1231)
at org.testng.internal.TestMethodWorker.invokeTestMethods(TestMethodWorker.java:127)
at org.testng.internal.TestMethodWorker.run(TestMethodWorker.java:111)
at org.testng.TestRunner.privateRun(TestRunner.java:767)
at org.testng.TestRunner.run(TestRunner.java:617)
at org.testng.SuiteRunner.runTest(SuiteRunner.java:348)
at org.testng.SuiteRunner.runSequentially(SuiteRunner.java:343)
at org.testng.SuiteRunner.privateRun(SuiteRunner.java:305)
at org.testng.SuiteRunner.run(SuiteRunner.java:254)
at org.testng.SuiteRunnerWorker.runSuite(SuiteRunnerWorker.java:52)
at org.testng.SuiteRunnerWorker.run(SuiteRunnerWorker.java:86)
at org.testng.TestNG.runSuitesSequentially(TestNG.java:1224)
at org.testng.TestNG.runSuitesLocally(TestNG.java:1149)
at org.testng.TestNG.run(TestNG.java:1057)
at org.apache.maven.surefire.testng.TestNGExecutor.run(TestNGExecutor.java:115)
at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.executeMulti(TestNGDirectoryTestSuite.java:205)
at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.execute(TestNGDirectoryTestSuite.java:108)
at org.apache.maven.surefire.testng.TestNGProvider.invoke(TestNGProvider.java:111)
at org.apache.maven.surefire.booter.ForkedBooter.invokeProviderInSameClassLoader(ForkedBooter.java:203)
at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:155)
at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:103)
Caused by: javax.net.ssl.SSLHandshakeException: Received fatal alert: handshake_failure
at sun.security.ssl.Alerts.getSSLException(Alerts.java:192)
at sun.security.ssl.Alerts.getSSLException(Alerts.java:154)
at sun.security.ssl.SSLSocketImpl.recvAlert(SSLSocketImpl.java:1991)
at sun.security.ssl.SSLSocketImpl.readRecord(SSLSocketImpl.java:1098)
at sun.security.ssl.SSLSocketImpl.performInitialHandshake(SSLSocketImpl.java:1344)
at sun.security.ssl.SSLSocketImpl.startHandshake(SSLSocketImpl.java:1371)
at sun.security.ssl.SSLSocketImpl.startHandshake(SSLSocketImpl.java:1355)
at org.apache.http.conn.ssl.SSLSocketFactory.connectSocket(SSLSocketFactory.java:543)
at org.apache.http.conn.ssl.SSLSocketFactory.connectSocket(SSLSocketFactory.java:409)
at org.apache.http.impl.conn.DefaultClientConnectionOperator.openConnection(DefaultClientConnectionOperator.java:177)
at org.apache.http.impl.conn.ManagedClientConnectionImpl.open(ManagedClientConnectionImpl.java:304)
at org.apache.http.impl.client.DefaultRequestDirector.tryConnect(DefaultRequestDirector.java:611)
at org.apache.http.impl.client.DefaultRequestDirector.execute(DefaultRequestDirector.java:446)
at org.apache.http.impl.client.AbstractHttpClient.doExecute(AbstractHttpClient.java:882)
at org.apache.http.impl.client.CloseableHttpClient.execute(CloseableHttpClient.java:82)
at org.apache.http.impl.client.CloseableHttpClient.execute(CloseableHttpClient.java:107)
at org.apache.http.impl.client.CloseableHttpClient.execute(CloseableHttpClient.java:55)
at org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient.sendRequest(GeoscalingWebClient.java:438)
at org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient.login(GeoscalingWebClient.java:205)
... 31 more
 */
@Test(groups="Broken", enabled=false)
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

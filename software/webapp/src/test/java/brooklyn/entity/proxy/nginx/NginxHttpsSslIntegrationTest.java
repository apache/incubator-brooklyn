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
package brooklyn.entity.proxy.nginx;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.File;

import brooklyn.test.TestResourceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxy.LoadBalancer;
import brooklyn.entity.proxy.ProxySslConfig;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.Location;
import brooklyn.location.basic.PortRanges;
import brooklyn.test.Asserts;
import brooklyn.test.HttpTestUtils;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * Test the operation of the {@link NginxController} class.
 */
public class NginxHttpsSslIntegrationTest extends BrooklynAppLiveTestSupport {
    
    private static final Logger log = LoggerFactory.getLogger(NginxHttpsSslIntegrationTest.class);

    private NginxController nginx;
    private DynamicCluster cluster;
    private Location localLoc;

    private static final String CERTIFICATE_URL = "classpath://ssl/certs/localhost/server.crt";
    private static final String KEY_URL = "classpath://ssl/certs/localhost/server.key";
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        localLoc = mgmt.getLocationRegistry().resolve("localhost");
    }
    
    private static void urlContainsPort(NginxController nginx, PortAttributeSensorAndConfigKey sensor, String portRange) {
        Integer port = nginx.getAttribute(sensor);
        Assert.assertTrue(Iterables.contains(PortRanges.fromString(portRange), port), "Port "+port+" not in range "+portRange);
        String url = Preconditions.checkNotNull(nginx.getAttribute(LoadBalancer.MAIN_URI), "main uri").toString();
        Assert.assertTrue(url.contains(":"+port), "URL does not contain expected port; port "+port+", url "+url);
    }

    public String getTestWar() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/hello-world.war");
        return "classpath://hello-world.war";
    }

    /**
     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void testStartsWithGlobalSsl_withCertificateAndKeyCopy() {
        cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
            .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class))
            .configure("initialSize", 1)
            .configure(JavaWebAppService.ROOT_WAR, getTestWar()));
        
        ProxySslConfig ssl = ProxySslConfig.builder()
                .certificateSourceUrl(CERTIFICATE_URL)
                .keySourceUrl(KEY_URL)
                .build();
        
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("sticky", false)
                .configure("serverPool", cluster)
                .configure("domain", "localhost")
                .configure("httpsPort", "8453+")
                .configure("ssl", ssl));
        
        app.start(ImmutableList.of(localLoc));

        urlContainsPort(nginx, LoadBalancer.PROXY_HTTPS_PORT, "8453+");

        final String url = nginx.getAttribute(WebAppService.ROOT_URL);
        log.info("URL for nginx is "+url);
        if (!url.startsWith("https://")) Assert.fail("URL should be https: "+url);
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                // Services are running
                assertTrue(cluster.getAttribute(SoftwareProcess.SERVICE_UP));
                for (Entity member : cluster.getMembers()) {
                    assertTrue(member.getAttribute(SoftwareProcess.SERVICE_UP));
                }
                
                assertTrue(nginx.getAttribute(SoftwareProcess.SERVICE_UP));
    
                // Nginx URL is available
                HttpTestUtils.assertHttpStatusCodeEquals(url, 200);
    
                // Web-server URL is available
                for (Entity member : cluster.getMembers()) {
                    HttpTestUtils.assertHttpStatusCodeEquals(member.getAttribute(WebAppService.ROOT_URL), 200);
                }
            }});
        
        app.stop();

        // Services have stopped
        assertFalse(nginx.getAttribute(SoftwareProcess.SERVICE_UP));
        assertFalse(cluster.getAttribute(SoftwareProcess.SERVICE_UP));
        for (Entity member : cluster.getMembers()) {
            assertFalse(member.getAttribute(SoftwareProcess.SERVICE_UP));
        }
    }

    private String getFile(String file) {
        return new File(getClass().getResource("/" + file).getFile()).getAbsolutePath();
    }

    @Test(groups = "Integration")
    public void testStartsWithGlobalSsl_withPreinstalledCertificateAndKey() {
        cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
            .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class))
            .configure("initialSize", 1)
            .configure(JavaWebAppService.ROOT_WAR, getTestWar()));
        
        ProxySslConfig ssl = ProxySslConfig.builder()
                .certificateDestination(getFile("ssl/certs/localhost/server.crt"))
                .keyDestination(getFile("ssl/certs/localhost/server.key"))
                .build();
        
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("sticky", false)
                .configure("serverPool", cluster)
                .configure("domain", "localhost")
                .configure("port", "8443+")
                .configure("ssl", ssl));

        app.start(ImmutableList.of(localLoc));

        final String url = nginx.getAttribute(WebAppService.ROOT_URL);
        if (!url.startsWith("https://")) Assert.fail("URL should be https: "+url);

        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                // Services are running
                assertTrue(cluster.getAttribute(SoftwareProcess.SERVICE_UP));
                for (Entity member : cluster.getMembers()) {
                    assertTrue(member.getAttribute(SoftwareProcess.SERVICE_UP));
                }
    
                assertTrue(nginx.getAttribute(SoftwareProcess.SERVICE_UP));
    
                // Nginx URL is available
                HttpTestUtils.assertHttpStatusCodeEquals(url, 200);
    
                // Web-server URL is available
                for (Entity member : cluster.getMembers()) {
                    HttpTestUtils.assertHttpStatusCodeEquals(member.getAttribute(WebAppService.ROOT_URL), 200);
                }
            }});

        app.stop();

        // Services have stopped
        assertFalse(nginx.getAttribute(SoftwareProcess.SERVICE_UP));
        assertFalse(cluster.getAttribute(SoftwareProcess.SERVICE_UP));
        for (Entity member : cluster.getMembers()) {
            assertFalse(member.getAttribute(SoftwareProcess.SERVICE_UP));
        }
    }

    @Test(groups = "Integration")
    public void testStartsNonSslThenBecomesSsl() {
        cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
            .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class))
            .configure("initialSize", 1)
            .configure(JavaWebAppService.ROOT_WAR, getTestWar()));
        
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
            .configure("serverPool", cluster)
            .configure("domain", "localhost"));

        app.start(ImmutableList.of(localLoc));

        urlContainsPort(nginx, LoadBalancer.PROXY_HTTP_PORT, "8000-8100");
        
        ProxySslConfig ssl = ProxySslConfig.builder()
                .certificateDestination(getFile("ssl/certs/localhost/server.crt"))
                .keyDestination(getFile("ssl/certs/localhost/server.key"))
                .build();
        ((EntityInternal)nginx).setConfig(LoadBalancer.PROXY_HTTPS_PORT, PortRanges.fromString("8443+"));
        ((EntityInternal)nginx).setConfig(NginxController.SSL_CONFIG, ssl);

        try {
            log.info("restarting nginx as ssl");
            nginx.restart();
            urlContainsPort(nginx, LoadBalancer.PROXY_HTTPS_PORT, "8443-8543");

            app.stop();
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
}

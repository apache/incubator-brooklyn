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
package org.apache.brooklyn.entity.webapp.jboss;

import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.entity.webapp.JavaWebAppSoftwareProcessImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.location.access.BrooklynAccessUtils;
import org.apache.brooklyn.sensor.enricher.Enrichers;
import org.apache.brooklyn.sensor.feed.http.HttpFeed;
import org.apache.brooklyn.sensor.feed.http.HttpPollConfig;
import org.apache.brooklyn.sensor.feed.http.HttpValueFunctions;
import org.apache.brooklyn.util.guava.Functionals;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;

public class JBoss7ServerImpl extends JavaWebAppSoftwareProcessImpl implements JBoss7Server {

    public static final Logger log = LoggerFactory.getLogger(JBoss7ServerImpl.class);

    private volatile HttpFeed httpFeed;
    
    public JBoss7ServerImpl(){
        super();
    }

    public JBoss7ServerImpl(@SuppressWarnings("rawtypes") Map flags){
        this(flags, null);
    }

    public JBoss7ServerImpl(@SuppressWarnings("rawtypes") Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public Class<?> getDriverInterface() {
        return JBoss7Driver.class;
    }

    @Override
    public JBoss7Driver getDriver() {
        return (JBoss7Driver) super.getDriver();
    }
    
    static {
        RendererHints.register(MANAGEMENT_URL, RendererHints.namedActionWithUrl());
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        HostAndPort hp = BrooklynAccessUtils.getBrooklynAccessibleAddress(this,
                getAttribute(MANAGEMENT_HTTP_PORT) + getConfig(PORT_INCREMENT));
        
        String managementUri = String.format("http://%s:%s/management/subsystem/web/connector/http/read-resource",
                hp.getHostText(), hp.getPort());
        setAttribute(MANAGEMENT_URL, managementUri);
        log.debug("JBoss sensors for "+this+" reading from "+managementUri);
        Map<String, String> includeRuntimeUriVars = ImmutableMap.of("include-runtime","true");
        boolean retrieveUsageMetrics = getConfig(RETRIEVE_USAGE_METRICS);
        
        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(200)
                .baseUri(managementUri)
                .credentials(getConfig(MANAGEMENT_USER), getConfig(MANAGEMENT_PASSWORD))
                .poll(new HttpPollConfig<Integer>(MANAGEMENT_STATUS)
                        .onSuccess(HttpValueFunctions.responseCode())
                        .suppressDuplicates(true))
                .poll(new HttpPollConfig<Boolean>(MANAGEMENT_URL_UP)
                        .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                        .onFailureOrException(Functions.constant(false))
                        .suppressDuplicates(true))
                .poll(new HttpPollConfig<Integer>(REQUEST_COUNT)
                        .vars(includeRuntimeUriVars)
                        .onSuccess(HttpValueFunctions.jsonContents("requestCount", Integer.class))
                        .enabled(retrieveUsageMetrics))
                .poll(new HttpPollConfig<Integer>(ERROR_COUNT)
                        .vars(includeRuntimeUriVars)
                        .onSuccess(HttpValueFunctions.jsonContents("errorCount", Integer.class))
                        .enabled(retrieveUsageMetrics))
                .poll(new HttpPollConfig<Integer>(TOTAL_PROCESSING_TIME)
                        .vars(includeRuntimeUriVars)
                        .onSuccess(HttpValueFunctions.jsonContents("processingTime", Integer.class))
                        .enabled(retrieveUsageMetrics))
                .poll(new HttpPollConfig<Integer>(MAX_PROCESSING_TIME)
                        .vars(includeRuntimeUriVars)
                        .onSuccess(HttpValueFunctions.jsonContents("maxTime", Integer.class))
                        .enabled(retrieveUsageMetrics))
                .poll(new HttpPollConfig<Long>(BYTES_RECEIVED)
                        .vars(includeRuntimeUriVars)
                        // jboss seems to report 0 even if it has received lots of requests; dunno why.
                        .onSuccess(HttpValueFunctions.jsonContents("bytesReceived", Long.class))
                        .enabled(retrieveUsageMetrics))
                .poll(new HttpPollConfig<Long>(BYTES_SENT)
                        .vars(includeRuntimeUriVars)
                        .onSuccess(HttpValueFunctions.jsonContents("bytesSent", Long.class))
                        .enabled(retrieveUsageMetrics))
                .build();
        
        connectServiceUp();
    }
    
    protected void connectServiceUp() {
        addEnricher(Enrichers.builder().updatingMap(Attributes.SERVICE_NOT_UP_INDICATORS)
            .from(MANAGEMENT_URL_UP)
            .computing(Functionals.ifNotEquals(true).value("Management URL not reachable") )
            .suppressDuplicates(true)
            .build());
    }
    
    protected void disconnectServiceUp() {
        disconnectServiceUpIsRunning();
    }
    
    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        
        if (httpFeed != null) httpFeed.stop();
        disconnectServiceUp();
    }
    
    public int getManagementHttpsPort() {
        return getAttribute(MANAGEMENT_HTTPS_PORT);
    }
    
    public int getManagementHttpPort() {
        return getAttribute(MANAGEMENT_HTTP_PORT);
    }
    
    public int getManagementNativePort() {
        return getAttribute(MANAGEMENT_NATIVE_PORT);
    }
    
    public int getPortOffset() {
        return getConfig(PORT_INCREMENT);
    }
    
    public boolean isWelcomeRootEnabled() {
        return false;
    }

    public String getBindAddress() {
        return getConfig(BIND_ADDRESS);
    }
    
    public String getManagementBindAddress() {
        return getConfig(BIND_ADDRESS);
    }
    
    public String getUnsecureBindAddress() {
        return getConfig(BIND_ADDRESS);
    }
    
    // If empty-string, disables Management security (!) by excluding the security-realm attribute
    public String getHttpManagementInterfaceSecurityRealm() {
        return "";
    }

    public int getDeploymentTimeoutSecs() {
        return getConfig(DEPLOYMENT_TIMEOUT);
    }

    /** Path of the keystore file on the AS7 server */
    public String getHttpsSslKeystoreFile() {
        return getDriver().getSslKeystoreFile();
    }
    
    @Override
    public String getShortName() {
        return "JBossAS7";
    }
}

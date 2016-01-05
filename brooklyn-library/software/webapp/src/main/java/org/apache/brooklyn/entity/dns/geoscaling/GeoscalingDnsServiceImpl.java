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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_CITY_INFO;

import java.net.URI;
import java.util.Collection;
import java.util.Set;

import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.location.geo.HostGeoInfo;
import org.apache.brooklyn.entity.dns.AbstractGeoDnsServiceImpl;
import org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient.Domain;
import org.apache.brooklyn.entity.dns.geoscaling.GeoscalingWebClient.SmartSubdomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;

public class GeoscalingDnsServiceImpl extends AbstractGeoDnsServiceImpl implements GeoscalingDnsService {

    private static final Logger log = LoggerFactory.getLogger(GeoscalingDnsServiceImpl.class);

    // Must remember any desired redirection targets if they're specified before configure() has been called.
    private Set<HostGeoInfo> rememberedTargetHosts;
    private GeoscalingWebClient webClient;
    
    // These are available only after the configure() method has been invoked.
    private boolean randomizeSmartSubdomainName;
    private String username;
    private String password;
    private String primaryDomainName;
    private String smartSubdomainName;

    public GeoscalingDnsServiceImpl() {
    }

    @Override
    public void init() {
        super.init();
        
        // defaulting to randomized subdomains makes deploying multiple applications easier
        if (config().get(RANDOMIZE_SUBDOMAIN_NAME) == null) {
            config().set(RANDOMIZE_SUBDOMAIN_NAME, true);
        }

        Boolean trustAll = config().get(SSL_TRUST_ALL);
        if (Boolean.TRUE.equals(trustAll)) {
            webClient = new GeoscalingWebClient(HttpTool.httpClientBuilder().trustAll().build());
        } else {
            webClient = new GeoscalingWebClient();
        }
    }
    
    // Ensure our configure() method gets called; may be able to remove this if/when the framework detects this
    // and invokes the configure() method automatically?
    @Override
    public void onManagementBecomingMaster() {
        try {
            applyConfig();
        } catch (Exception e) {
            // don't prevent management coming up, but do mark it as on fire
            log.error("Geoscaling did not come up correctly: "+e, e);
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
        }
        super.onManagementBecomingMaster();
    }

    boolean isConfigured = false;
    
    public synchronized void applyConfig() {        
        randomizeSmartSubdomainName = config().get(RANDOMIZE_SUBDOMAIN_NAME);
        username = config().get(GEOSCALING_USERNAME);
        password = config().get(GEOSCALING_PASSWORD);
        primaryDomainName = config().get(GEOSCALING_PRIMARY_DOMAIN_NAME);
        smartSubdomainName = config().get(GEOSCALING_SMART_SUBDOMAIN_NAME);

        // Ensure all mandatory configuration is provided.
        checkNotNull(username, "The GeoScaling username is not specified");
        checkNotNull(password, "The GeoScaling password is not specified");
        checkNotNull(primaryDomainName, "The GeoScaling primary domain name is not specified");
        
        if (randomizeSmartSubdomainName) {
            // if no smart subdomain specified, but random is, use something random
            if (smartSubdomainName != null) smartSubdomainName += "-";
            else smartSubdomainName = "";
            smartSubdomainName += Identifiers.makeRandomId(8);
        }
        checkNotNull(smartSubdomainName, "The GeoScaling smart subdomain name is not specified or randomized");
        
        String fullDomain = smartSubdomainName+"."+primaryDomainName;
        log.info("GeoScaling service will configure redirection for '"+fullDomain+"' domain");
        sensors().set(GEOSCALING_ACCOUNT, username);
        sensors().set(MANAGED_DOMAIN, fullDomain);
        sensors().set(HOSTNAME, getHostname());
        
        isConfigured = true;
        
        if (rememberedTargetHosts != null) {
            reconfigureService(rememberedTargetHosts);
            rememberedTargetHosts = null;
        }
    }
    
    @Override
    public String getHostname() {
        String result = getAttribute(MANAGED_DOMAIN);
        return (Strings.isBlank(result)) ? null : result;
    }
    
    /** minimum/default TTL here is 300s = 5m */
    public long getTimeToLiveSeconds() { return 5*60; }
    
    @Override
    public void destroy() {
        setServiceState(Lifecycle.STOPPING);
        if (!isConfigured) return;
        
        // Don't leave randomized subdomains configured on our GeoScaling account.
        if (randomizeSmartSubdomainName) {
            webClient.login(username, password);
            Domain primaryDomain = webClient.getPrimaryDomain(primaryDomainName);
            SmartSubdomain smartSubdomain = (primaryDomain != null) ? primaryDomain.getSmartSubdomain(smartSubdomainName) : null;
            if (smartSubdomain != null) {
                log.info("Deleting randomized GeoScaling smart subdomain '"+smartSubdomainName+"."+primaryDomainName+"'");
                smartSubdomain.delete();
            }
            webClient.logout();
        }
        
        super.destroy();
        
        isConfigured = false;
    }
    
    protected void reconfigureService(Collection<HostGeoInfo> targetHosts) {
        if (!isConfigured) {
            this.rememberedTargetHosts = MutableSet.copyOf(targetHosts);
            return;
        }
        
        webClient.login(username, password);
        Domain primaryDomain = webClient.getPrimaryDomain(primaryDomainName);
        if (primaryDomain==null) 
            throw new NullPointerException(this+" got null from web client for primary domain "+primaryDomainName);
        SmartSubdomain smartSubdomain = primaryDomain.getSmartSubdomain(smartSubdomainName);
        
        if (smartSubdomain == null) {
            log.info("GeoScaling {} smart subdomain '{}.{}' does not exist, creating it now", new Object[] {this, smartSubdomainName, primaryDomainName});
            // TODO use WithMutexes to ensure this is single-entrant
            primaryDomain.createSmartSubdomain(smartSubdomainName);
            smartSubdomain = primaryDomain.getSmartSubdomain(smartSubdomainName);
        }
        
        if (smartSubdomain != null) {
            log.debug("GeoScaling {} being reconfigured to use {}", this, targetHosts);
            String script = GeoscalingScriptGenerator.generateScriptString(targetHosts);
            smartSubdomain.configure(PROVIDE_CITY_INFO, script);
            if (targetHosts.isEmpty()) {
                setServiceState(Lifecycle.CREATED);
                sensors().set(ROOT_URL, null);
                sensors().set(MAIN_URI, null);
            } else {
                setServiceState(Lifecycle.RUNNING);
                String domain = getAttribute(MANAGED_DOMAIN);
                if (!Strings.isEmpty(domain)) {
                    sensors().set(ROOT_URL, "http://"+domain+"/");
                    sensors().set(MAIN_URI, URI.create("http://"+domain+"/"));
                }
            }
        } else {
            log.warn("Failed to retrieve or create GeoScaling smart subdomain '"+smartSubdomainName+"."+primaryDomainName+
                    "', aborting attempt to configure service");
            setServiceState(Lifecycle.ON_FIRE);
        }
        
        webClient.logout();
    }

}

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
package org.apache.brooklyn.entity.proxy.nginx;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.mgmt.SubscriptionHandle;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ServiceNotUpLogic;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import org.apache.brooklyn.entity.proxy.AbstractControllerImpl;
import org.apache.brooklyn.entity.proxy.ProxySslConfig;
import org.apache.brooklyn.entity.proxy.nginx.NginxController.NginxControllerInternal;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.file.ArchiveUtils;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.brooklyn.util.http.HttpToolResponse;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * Implementation of the {@link NginxController} entity.
 */
public class NginxControllerImpl extends AbstractControllerImpl implements NginxController, NginxControllerInternal {

    private static final Logger LOG = LoggerFactory.getLogger(NginxControllerImpl.class);

    private volatile HttpFeed httpFeed;
    private final Set<String> installedKeysCache = Sets.newLinkedHashSet();
    protected UrlMappingsMemberTrackerPolicy urlMappingsMemberTrackerPolicy;
    protected SubscriptionHandle targetAddressesHandler;

    @Override
    public void reload() {
        NginxSshDriver driver = (NginxSshDriver)getDriver();
        if (driver==null) {
            Lifecycle state = getAttribute(NginxController.SERVICE_STATE_ACTUAL);
            throw new IllegalStateException("Cannot reload (no driver instance; stopped? (state="+state+")");
        }

        driver.reload();
    }

    @Override
    public boolean isSticky() {
        return getConfig(STICKY);
    }

    private class UrlInferencer implements Supplier<URI> {
        private Map<String, String> parameters;
        private UrlInferencer(Map<String,String> parameters) {
            this.parameters = parameters;
        }
        @Override public URI get() { 
            String baseUrl = inferUrl(true);
            if (parameters==null || parameters.isEmpty())
                return URI.create(baseUrl);
            return URI.create(baseUrl+"?"+HttpTool.encodeUrlParams(parameters));
        }
    }
    
    @Override
    public void connectSensors() {
        super.connectSensors();

        ConfigToAttributes.apply(this);

        // "up" is defined as returning a valid HTTP response from nginx (including a 404 etc)
        httpFeed = addFeed(HttpFeed.builder()
                .uniqueTag("nginx-poll")
                .entity(this)
                .period(getConfig(HTTP_POLL_PERIOD))
                .baseUri(new UrlInferencer(null))
                .poll(new HttpPollConfig<Boolean>(NGINX_URL_ANSWERS_NICELY)
                        // Any response from Nginx is good.
                        .checkSuccess(Predicates.alwaysTrue())
                        // Accept any nginx response (don't assert specific version), so that sub-classing
                        // for a custom nginx build is not strict about custom version numbers in headers
                        .onResult(HttpValueFunctions.containsHeader("Server"))
                        .setOnException(false)
                        .suppressDuplicates(true))
                .build());
        
        // TODO PERSISTENCE WORKAROUND kept anonymous function in case referenced in persisted state
        new Function<HttpToolResponse, Boolean>() {
            @Override
            public Boolean apply(HttpToolResponse input) {
                // Accept any nginx response (don't assert specific version), so that sub-classing
                // for a custom nginx build is not strict about custom version numbers in headers
                List<String> actual = input.getHeaderLists().get("Server");
                return actual != null && actual.size() == 1;
            }
        };
        
        if (!Lifecycle.RUNNING.equals(getAttribute(SERVICE_STATE_ACTUAL))) {
            // TODO when updating the map, if it would change from empty to empty on a successful run
            // gate with the above check to prevent flashing on ON_FIRE during rebind (this is invoked on rebind as well as during start)
            ServiceNotUpLogic.updateNotUpIndicator(this, NGINX_URL_ANSWERS_NICELY, "No response from nginx yet");
        }
        enrichers().add(Enrichers.builder().updatingMap(Attributes.SERVICE_NOT_UP_INDICATORS)
            .uniqueTag("not-up-unless-url-answers")
            .from(NGINX_URL_ANSWERS_NICELY)
            .computing(Functionals.ifNotEquals(true).value("URL where nginx listens is not answering correctly (with expected header)") )
            .build());
        connectServiceUpIsRunning();

        // Can guarantee that parent/managementContext has been set
        Group urlMappings = getConfig(URL_MAPPINGS);
        if (urlMappings!=null && urlMappingsMemberTrackerPolicy==null) {
            // Listen to the targets of each url-mapping changing
            targetAddressesHandler = subscriptions().subscribeToMembers(urlMappings, UrlMapping.TARGET_ADDRESSES, new SensorEventListener<Collection<String>>() {
                    @Override public void onEvent(SensorEvent<Collection<String>> event) {
                        updateNeeded();
                    }
                });

            // Listen to url-mappings being added and removed
            urlMappingsMemberTrackerPolicy = policies().add(PolicySpec.create(UrlMappingsMemberTrackerPolicy.class)
                    .configure("group", urlMappings));
        }
    }

    protected void removeUrlMappingsMemberTrackerPolicy() {
        if (urlMappingsMemberTrackerPolicy != null) {
            policies().remove(urlMappingsMemberTrackerPolicy);
            urlMappingsMemberTrackerPolicy = null;
        }
        Group urlMappings = getConfig(URL_MAPPINGS);
        if (urlMappings!=null && targetAddressesHandler!=null) {
            subscriptions().unsubscribe(urlMappings, targetAddressesHandler);
            targetAddressesHandler = null;
        }
    }
    
    public static class UrlMappingsMemberTrackerPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityEvent(EventType type, Entity entity) {
            // relies on policy-rebind injecting the implementation rather than the dynamic-proxy
            ((NginxControllerImpl)super.entity).updateNeeded();
        }
    }

    @Override
    protected void preStop() {
        super.preStop();
        removeUrlMappingsMemberTrackerPolicy();
    }
    
    @Override
    protected void postStop() {
        // TODO don't want stop to race with the last poll.
        super.postStop();
        sensors().set(SERVICE_UP, false);
    }

    @Override
    protected void disconnectSensors() {
        if (httpFeed != null) httpFeed.stop();
        disconnectServiceUpIsRunning();
        super.disconnectSensors();
    }

    @Override
    public Class<?> getDriverInterface() {
        return NginxDriver.class;
    }

    @Override
    public NginxDriver getDriver() {
        return (NginxDriver) super.getDriver();
    }

    public void doExtraConfigurationDuringStart() {
        computePortsAndUrls();
        reconfigureService();
        // reconnect sensors if ports have changed
        connectSensors();
    }

    @Override
    @Effector(description="Gets the current server configuration (by brooklyn recalculating what the config should be); does not affect the server")
    public String getCurrentConfiguration() {
        return getConfigFile();
    }

    @Override
    @Effector(description="Deploys an archive of static content to the server")
    public void deploy(String archiveUrl) {
        NginxSshDriver driver = (NginxSshDriver) getDriver();
        if (driver==null) {
            if (LOG.isDebugEnabled())
                LOG.debug("No driver for {}, so not deploying archive (is entity stopping? state={})",
                        this, getAttribute(NginxController.SERVICE_STATE_ACTUAL));
            return;
        }

        // Copy to the destination machine and extract contents
        ArchiveUtils.deploy(archiveUrl, driver.getMachine(), driver.getRunDir());
    }

    @Override
    public void reconfigureService() {
        String cfg = getConfigFile();
        if (cfg == null) return;

        if (LOG.isDebugEnabled()) LOG.debug("Reconfiguring {}, targetting {} and {}", new Object[] {this, getServerPoolAddresses(), getUrlMappings()});
        if (LOG.isTraceEnabled()) LOG.trace("Reconfiguring {}, config file:\n{}", this, cfg);

        NginxSshDriver driver = (NginxSshDriver) getDriver();
        if (!driver.isCustomizationCompleted()) {
            if (LOG.isDebugEnabled()) LOG.debug("Reconfiguring {}, but driver's customization not yet complete so aborting", this);
            return;
        }

        driver.getMachine().copyTo(Streams.newInputStreamWithContents(cfg), driver.getRunDir()+"/conf/server.conf");

        installSslKeys("global", getSslConfig());

        for (UrlMapping mapping : getUrlMappings()) {
            //cache ensures only the first is installed, which is what is assumed below
            installSslKeys(mapping.getDomain(), mapping.getConfig(UrlMapping.SSL_CONFIG));
        }
    }

    /**
     * Installs SSL keys named as {@code id.crt} and {@code id.key} where nginx can find them.
     * <p>
     * Currently skips re-installs (does not support changing)
     */
    public void installSslKeys(String id, ProxySslConfig ssl) {
        if (ssl == null) return;

        if (installedKeysCache.contains(id)) return;

        NginxSshDriver driver = (NginxSshDriver) getDriver();

        if (!Strings.isEmpty(ssl.getCertificateSourceUrl())) {
            String certificateDestination = Strings.isEmpty(ssl.getCertificateDestination()) ? driver.getRunDir() + "/conf/" + id + ".crt" : ssl.getCertificateDestination();
            driver.getMachine().copyTo(ImmutableMap.of("permissions", "0600"),
                    ResourceUtils.create(this).getResourceFromUrl(ssl.getCertificateSourceUrl()),
                    certificateDestination);
        }

        if (!Strings.isEmpty(ssl.getKeySourceUrl())) {
            String keyDestination = Strings.isEmpty(ssl.getKeyDestination()) ? driver.getRunDir() + "/conf/" + id + ".key" : ssl.getKeyDestination();
            driver.getMachine().copyTo(ImmutableMap.of("permissions", "0600"),
                    ResourceUtils.create(this).getResourceFromUrl(ssl.getKeySourceUrl()),
                    keyDestination);
        }

        installedKeysCache.add(id);
    }

    @Override
    public String getConfigFile() {
        NginxSshDriver driver = (NginxSshDriver) getDriver();
        if (driver==null) {
            LOG.debug("No driver for {}, so not generating config file (is entity stopping? state={})",
                    this, getAttribute(NginxController.SERVICE_STATE_ACTUAL));
            return null;
        }

        NginxConfigFileGenerator templateGenerator = getConfig(NginxController.SERVER_CONF_GENERATOR);
        return templateGenerator.generateConfigFile(driver, this);
    }

    @Override
    public Iterable<UrlMapping> getUrlMappings() {
        // For mapping by URL
        Group urlMappingGroup = getConfig(NginxController.URL_MAPPINGS);
        if (urlMappingGroup != null) {
            return Iterables.filter(urlMappingGroup.getMembers(), UrlMapping.class);
        } else {
            return Collections.<UrlMapping>emptyList();
        }
    }

    @Override
    public String getShortName() {
        return "Nginx";
    }

    public boolean appendSslConfig(String id,
            StringBuilder out,
            String prefix,
            ProxySslConfig ssl,
            boolean sslBlock,
            boolean certificateBlock) {
        if (ssl == null)
            return false;
        if (sslBlock) {
            out.append(prefix);
            out.append("ssl on;\n");
        }
        if (ssl.getReuseSessions()) {
            out.append(prefix);
            out.append("proxy_ssl_session_reuse on;");
        }
        if (certificateBlock) {
            String cert;
            if (Strings.isEmpty(ssl.getCertificateDestination())) {
                cert = "" + id + ".crt";
            } else {
                cert = ssl.getCertificateDestination();
            }

            out.append(prefix);
            out.append("ssl_certificate " + cert + ";\n");

            String key;
            if (!Strings.isEmpty(ssl.getKeyDestination())) {
                key = ssl.getKeyDestination();
            } else if (!Strings.isEmpty(ssl.getKeySourceUrl())) {
                key = "" + id + ".key";
            } else {
                key = null;
            }

            if (key != null) {
                out.append(prefix);
                out.append("ssl_certificate_key " + key + ";\n");
            }
        }
        return true;
    }
}

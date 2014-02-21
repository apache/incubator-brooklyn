/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.proxy.nginx;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.java.JarBuilder;
import brooklyn.entity.proxy.AbstractControllerImpl;
import brooklyn.entity.proxy.ProxySslConfig;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpPollValue;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Urls;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.stream.Streams;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * Implementation of the {@link NginxController} entity.
 */
public class NginxControllerImpl extends AbstractControllerImpl implements NginxController {

    private static final Logger LOG = LoggerFactory.getLogger(NginxControllerImpl.class);

    private volatile HttpFeed httpFeed;
    private final Set<String> installedKeysCache = Sets.newLinkedHashSet();

    @Override
    public void reload() {
        NginxSshDriver driver = (NginxSshDriver)getDriver();
        if (driver==null) {
            Lifecycle state = getAttribute(NginxController.SERVICE_STATE);
            throw new IllegalStateException("Cannot reload (no driver instance; stopped? (state="+state+")");
        }

        driver.reload();
    }

    public boolean isSticky() {
        return getConfig(STICKY);
    }

    @Override
    public void connectSensors() {
        super.connectSensors();

        ConfigToAttributes.apply(this);
        String accessibleRootUrl = inferUrl(true);

        // "up" is defined as returning a valid HTTP response from nginx (including a 404 etc)
        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(getConfig(HTTP_POLL_PERIOD))
                .baseUri(accessibleRootUrl)
                .baseUriVars(ImmutableMap.of("include-runtime", "true"))
                .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                        // Any response from Nginx is good.
                        .checkSuccess(Predicates.alwaysTrue())
                        .onResult(new Function<HttpPollValue, Boolean>() {
                                @Override
                                public Boolean apply(HttpPollValue input) {
                                    // Accept any nginx response (don't assert specific version), so that sub-classing
                                    // for a custom nginx build is not strict about custom version numbers in headers
                                    List<String> actual = input.getHeaderLists().get("Server");
                                    return actual != null && actual.size() == 1 && actual.get(0).startsWith("nginx");
                                }})
                        .setOnException(false))
                .build();

        // Can guarantee that parent/managementContext has been set
        Group urlMappings = getConfig(URL_MAPPINGS);
        if (urlMappings != null) {
            // Listen to the targets of each url-mapping changing
            subscribeToMembers(urlMappings, UrlMapping.TARGET_ADDRESSES, new SensorEventListener<Collection<String>>() {
                    @Override public void onEvent(SensorEvent<Collection<String>> event) {
                        updateNeeded();
                    }
                });

            // Listen to url-mappings being added and removed
            AbstractMembershipTrackingPolicy policy = new AbstractMembershipTrackingPolicy() {
                @Override protected void onEntityChange(Entity member) { updateNeeded(); }
                @Override protected void onEntityAdded(Entity member) { updateNeeded(); }
                @Override protected void onEntityRemoved(Entity member) { updateNeeded(); }
            };
            addPolicy(policy);
            policy.setGroup(urlMappings);
        }
    }

    @Override
    protected void doStop() {
        // TODO Want http.poll to set SERVICE_UP to false on IOException. How?
        // And don't want stop to race with the last poll.
        super.doStop();
        setAttribute(SERVICE_UP, false);
    }

    @Override
    protected void disconnectSensors() {
        if (httpFeed != null) httpFeed.stop();
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
        reconfigureService();
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
                        this, getAttribute(NginxController.SERVICE_STATE));
            return;
        }
        SshMachineLocation machine = driver.getMachine();

        // If a local folder, then jar it up
        if (new File(archiveUrl).isDirectory()) {
            try {
                File jarFile = JarBuilder.buildJar(new File(archiveUrl));
                archiveUrl = jarFile.getAbsolutePath();
            } catch (IOException e) {
                throw new IllegalStateException("Error archiving directory "+archiveUrl, e);
            }
        }

        String runDir = getDriver().getRunDir();
        String destName = archiveUrl;
        destName = destName.contains("?") ? destName.substring(0, destName.indexOf('?')) : destName;
        destName = destName.substring(destName.lastIndexOf('/') + 1);

        // Use the location mutex to prevent package manager locking issues
        try {
            machine.acquireMutex("installing", "installing static archive "+getId());

            int result = machine.installTo(ResourceUtils.create(this), archiveUrl, Urls.mergePaths(runDir, destName));
            if (result != 0)
                throw new IllegalStateException(format("unable to install static content archive %s for %s at %s", archiveUrl, this, machine));

            // if it's a jar, zip or tgz then expand
            if (destName.toLowerCase().endsWith(".zip") || destName.toLowerCase().endsWith(".jar")) {
                result = machine.execCommands("unzipping", ImmutableList.of(BashCommands.INSTALL_UNZIP, format("cd %s && unzip %s", runDir, destName)));
            } else if (destName.toLowerCase().endsWith(".tgz") || destName.toLowerCase().endsWith(".tar.gz")) {
                result = machine.execCommands("untarring gz", ImmutableList.of(BashCommands.INSTALL_TAR, format("cd %s && tar xvfz %s", runDir, destName)));
            } else if (destName.toLowerCase().endsWith(".tar")) {
                result = machine.execCommands("untarring", ImmutableList.of(BashCommands.INSTALL_TAR, format("cd %s && tar xvf %s", runDir, destName)));
            }
            if (result != 0)
                throw new IllegalStateException(format("unable to install static content for %s at %s (failed to expand archive %s)", this, machine, archiveUrl));
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } finally {
            machine.releaseMutex("installing");
        }
    }

    @Override
    public void reconfigureService() {
        String cfg = getConfigFile();
        if (cfg == null) return;

        if (LOG.isDebugEnabled()) LOG.debug("Reconfiguring {}, targetting {} and {}", new Object[] {this, serverPoolAddresses, getUrlMappings()});
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
            driver.getMachine().copyTo(ImmutableMap.of("permissions", "0400"),
                    ResourceUtils.create(this).getResourceFromUrl(ssl.getCertificateSourceUrl()),
                    certificateDestination);
        }

        if (!Strings.isEmpty(ssl.getKeySourceUrl())) {
            String keyDestination = Strings.isEmpty(ssl.getKeyDestination()) ? driver.getRunDir() + "/conf/" + id + ".key" : ssl.getKeyDestination();
            driver.getMachine().copyTo(ImmutableMap.of("permissions", "0400"),
                    ResourceUtils.create(this).getResourceFromUrl(ssl.getKeySourceUrl()),
                    keyDestination);
        }

        installedKeysCache.add(id);
    }

    @Override
    public String getConfigFile() {
        NginxSshDriver driver = (NginxSshDriver) getDriver();
        if (driver==null) {
            if (LOG.isDebugEnabled())
                LOG.debug("No driver for {}, so not generating config file (is entity stopping? state={})",
                        this, getAttribute(NginxController.SERVICE_STATE));
            return null;
        }

        String templateUrl = getConfig(NginxController.SERVER_CONF_TEMPLATE_URL);
        if (templateUrl != null) {
            return NginxConfigTemplate.generator(driver).configFile();
        } else {
            return NginxConfigFileGenerator.generator(driver).configFile();
        }
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

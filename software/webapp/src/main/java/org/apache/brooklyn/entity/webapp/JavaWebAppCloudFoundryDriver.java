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
package org.apache.brooklyn.entity.webapp;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.entity.java.JavaSoftwareProcessDriver;
import org.apache.brooklyn.location.cloudfoundry.CloudFoundryPaasLocation;
import com.google.common.collect.ImmutableSet;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.Staging;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class JavaWebAppCloudFoundryDriver extends AbstractApplicationCloudFoundryDriver
implements JavaSoftwareProcessDriver, JavaWebAppDriver {

    private static final int HTTP_PORT = 8080;
    private static final int HTTP_PORTS = 443;
    private static final  Set<String> ENABLED_PROTOCOLS;
    static{
        ENABLED_PROTOCOLS = ImmutableSet.of("http", "https");
    }

    private String applicationName;
    private String applicationWarUrl;

    public JavaWebAppCloudFoundryDriver(EntityLocal entity, CloudFoundryPaasLocation location) {
        super(entity, location);
    }

    public JavaWebAppSoftwareProcessImpl getEntity() {
        return (JavaWebAppSoftwareProcessImpl) super.getEntity();
    }

    @Override
    protected void init() {
        super.init();
        initApplicationParameters();
        initAttributes();
    }

    @SuppressWarnings("unchecked")
    /**
     * It allows to init parameters necessary for the drivers.
     */
    protected void initApplicationParameters() {
        //TODO: Probably, this method could be moved to the super class.
        //but, a new configkey should be necessary to specify the deployment
        //artifact (war) without using the java service.
        applicationWarUrl = getEntity().getConfig(JavaWebAppService.ROOT_WAR);

        String nameWithExtension=getFilenameContextMapper()
                .findArchiveNameFromUrl(applicationWarUrl, true);
        applicationName  = nameWithExtension.substring(0, nameWithExtension.indexOf('.'));

        //These values shouldn't be null or empty
        checkNotNull(applicationWarUrl, "application war url");
        checkNotNull(applicationName, "application name");
    }

    /**
     * It allows to change the value to the generic entity attributes
     * according to the PaaS constraint
     */
    protected void initAttributes(){
        getEntity().setAttribute(Attributes.HTTP_PORT, HTTP_PORT);
        getEntity().setAttribute(Attributes.HTTPS_PORT, HTTP_PORTS);

        getEntity().setAttribute(WebAppServiceConstants.ENABLED_PROTOCOLS, ENABLED_PROTOCOLS);
    }

    @Override
    public String getBuildpack(){
        return getEntity().getBuildpack();
    }

    protected String getApplicationUrl(){
        return applicationWarUrl;
    }

    protected String getApplicationName(){
        return applicationName;
    }

    @Override
    public Set<String> getEnabledProtocols() {
        return entity.getAttribute(JavaWebAppSoftwareProcess.ENABLED_PROTOCOLS);
    }

    @Override
    public Integer getHttpPort() {
        return getEntity().getHttpPort();
    }

    @Override
    public Integer getHttpsPort() {
        return getEntity().getHttpsPort();
    }

    @Override
    public HttpsSslConfig getHttpsSslConfig() {
        return null;
    }

    @Override
    public void postLaunch() {
        super.postLaunch();
        String domainUrl = inferRootUrl();
        getEntity().setAttribute(Attributes.MAIN_URI, URI.create(domainUrl));
        entity.setAttribute(WebAppService.ROOT_URL,  domainUrl);
    }

    protected String inferRootUrl() {
        //TODO: this method is copied from JavaWebAppSshDriver, so it could be moved to any super class. It could be moved to the entity too

        CloudApplication application = getClient().getApplication(getApplicationName());
        String domainUri = application.getUris().get(0);

        if (isProtocolEnabled("https")) {
            Integer port = getHttpsPort();
            checkNotNull(port, "HTTPS_PORT sensors not set; is an acceptable port available?");
            return String.format("https://%s", domainUri);
        } else if (isProtocolEnabled("http")) {
            Integer port = getHttpPort();
            checkNotNull(port, "HTTP_PORT sensors not set; is an acceptable port available?");
            return String.format("http://%s", domainUri);
        } else {
            throw new IllegalStateException("HTTP and HTTPS protocols not enabled for "+entity+"; enabled protocols are "+getEnabledProtocols());
        }
    }

    protected boolean isProtocolEnabled(String protocol) {
        //TODO: this method is copied from JavaWebAppSshDriver, so it could be moved to any super class. . It could be moved to the entity too
        Set<String> protocols = getEnabledProtocols();
        for (String contender : protocols) {
            if (protocol.equalsIgnoreCase(contender)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void deploy() {
        getEntity().deploy(applicationWarUrl, applicationName);
    }

    @Override
    public void deploy(File file) {
        deploy(file, null);
    }

    @Override
    public void deploy(File f, String targetName) {
        if (targetName == null) {
            targetName = f.getName();
        }
        deploy(f.toURI().toASCIIString(), targetName);
    }

    @Override
    public String deploy(String url, String targetName) {
        List<String> uris = new ArrayList<String>();
        Staging staging;
        File war;

        try {
            staging = new Staging(null, getBuildpack());
            uris.add(inferApplicationDomainUri(getApplicationName()));

            war=LocalResourcesDownloader
                    .downloadResourceInLocalDir(getApplicationUrl());

            getClient().createApplication(getApplicationName(), staging,
                    getLocation().getConfig(CloudFoundryPaasLocation.REQUIRED_MEMORY),
                    uris, null);
            getClient().uploadApplication(getApplicationName(), war.getCanonicalPath());
        } catch (IOException e) {
            log.error("Error deploying application {} managed by driver {}",
                    new Object[]{getEntity(), this});
        }

        return targetName;
    }

    @Override
    public void undeploy(String targetName) {
        //TODO: complete
    }

    @Override
    public FilenameToWebContextMapper getFilenameContextMapper() {
        return new FilenameToWebContextMapper();
    }

    @Override
    public boolean isJmxEnabled() {
        return false;
    }


}

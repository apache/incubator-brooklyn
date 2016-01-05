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

import java.util.Map;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.objs.HasShortName;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.proxy.AbstractController;
import org.apache.brooklyn.entity.proxy.ProxySslConfig;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import com.google.common.collect.ImmutableMap;

/**
 * An entity that represents an Nginx proxy (e.g. for routing requests to servers in a cluster).
 * <p>
 * The default driver *builds* nginx from source (because binaries are not reliably available, esp not with sticky sessions).
 * This requires gcc and other build tools installed. The code attempts to install them but inevitably 
 * this entity may be more finicky about the OS/image where it runs than others.
 * <p>
 * Paritcularly on OS X we require Xcode and command-line gcc installed and on the path.
 * <p>
 * See {@link http://library.linode.com/web-servers/nginx/configuration/basic} for useful info/examples
 * of configuring nginx.
 * <p>
 * https configuration is supported, with the certificates providable on a per-UrlMapping basis or a global basis.
 * (not supported to define in both places.) 
 * per-Url is useful if different certificates are used for different server names,
 * or different ports if that is supported.
 * see more info on Ssl in {@link ProxySslConfig}.
 */
@Catalog(name="Nginx Server", description="A single Nginx server. Provides HTTP and reverse proxy services", iconUrl="classpath:///nginx-logo.jpeg")
@ImplementedBy(NginxControllerImpl.class)
public interface NginxController extends AbstractController, HasShortName {

    MethodEffector<String> GET_CURRENT_CONFIGURATION =
            new MethodEffector<String>(NginxController.class, "getCurrentConfiguration");

    MethodEffector<Void> DEPLOY =
            new MethodEffector<Void>(NginxController.class, "deploy");
    
    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION =
            ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.8.0");

    @SetFromFlag("stickyVersion")
    ConfigKey<String> STICKY_VERSION = ConfigKeys.newStringConfigKey(
            "nginx.sticky.version", "Version of ngnix-sticky-module to be installed, if required", "1.2.5");

    @SetFromFlag("pcreVersion")
    ConfigKey<String> PCRE_VERSION = ConfigKeys.newStringConfigKey(
            "pcre.version", "Version of PCRE to be installed, if required", "8.37");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://nginx.org/download/nginx-${version}.tar.gz");

    @SetFromFlag("downloadAddonUrls")
    BasicAttributeSensorAndConfigKey<Map<String,String>> DOWNLOAD_ADDON_URLS = new BasicAttributeSensorAndConfigKey<Map<String,String>>(
            SoftwareProcess.DOWNLOAD_ADDON_URLS, ImmutableMap.of(
                    "stickymodule", "https://bitbucket.org/nginx-goodies/nginx-sticky-module-ng/get/${addonversion}.tar.gz",
                    "pcre", "ftp://ftp.csx.cam.ac.uk/pub/software/programming/pcre/pcre-${addonversion}.tar.gz"));

    @SetFromFlag("sticky")
    ConfigKey<Boolean> STICKY = ConfigKeys.newBooleanConfigKey(
            "nginx.sticky", "Whether to use sticky sessions", true);

    @SetFromFlag("httpPollPeriod")
    ConfigKey<Long> HTTP_POLL_PERIOD = ConfigKeys.newLongConfigKey(
            "nginx.sensorpoll.http", "Poll period (in milliseconds)", 1000L);

    @SetFromFlag("withLdOpt")
    ConfigKey<String> WITH_LD_OPT = ConfigKeys.newStringConfigKey(
            "nginx.install.withLdOpt", "String to pass in with --with-ld-opt=\"<val>\" (and for OS X has pcre auto-appended to this)", "-L /usr/local/lib");

    @SetFromFlag("withCcOpt")
    ConfigKey<String> WITH_CC_OPT = ConfigKeys.newStringConfigKey(
            "nginx.install.withCcOpt", "String to pass in with --with-cc-opt=\"<val>\"", "-I /usr/local/include");

    @SetFromFlag("configGenerator")
    ConfigKey<NginxConfigFileGenerator> SERVER_CONF_GENERATOR = ConfigKeys.newConfigKey(NginxConfigFileGenerator.class,
            "nginx.config.generator", "The server.conf generator class", new NginxDefaultConfigGenerator());

    @SetFromFlag("configTemplate")
    ConfigKey<String> SERVER_CONF_TEMPLATE_URL = NginxTemplateConfigGenerator.SERVER_CONF_TEMPLATE_URL;

    @SetFromFlag("staticContentArchive")
    ConfigKey<String> STATIC_CONTENT_ARCHIVE_URL = ConfigKeys.newStringConfigKey(
            "nginx.config.staticContentArchiveUrl", "The URL of an archive file of static content (To be copied to the server)");

    BasicAttributeSensorAndConfigKey<String> ACCESS_LOG_LOCATION = new BasicAttributeSensorAndConfigKey<String>(String.class,
            "nginx.log.access", "Nginx access log file location", "logs/access.log");

    BasicAttributeSensorAndConfigKey<String> ERROR_LOG_LOCATION = new BasicAttributeSensorAndConfigKey<String>(String.class,
            "nginx.log.error", "Nginx error log file location", "logs/error.log");

    boolean isSticky();

    @Effector(description="Gets the current server configuration (by brooklyn recalculating what the config should be); does not affect the server")
    String getCurrentConfiguration();

    @Effector(description="Deploys an archive of static content to the server")
    void deploy(@EffectorParam(name="archiveUrl", description="The URL of the static content archive to deploy") String archiveUrl);

    String getConfigFile();

    Iterable<UrlMapping> getUrlMappings();

    boolean appendSslConfig(String id, StringBuilder out, String prefix, ProxySslConfig ssl, boolean sslBlock, boolean certificateBlock);
    
    public static final AttributeSensor<Boolean> NGINX_URL_ANSWERS_NICELY = Sensors.newBooleanSensor( "nginx.url.answers.nicely");
    public static final AttributeSensor<String> PID_FILE = Sensors.newStringSensor( "nginx.pid.file", "PID file");
    
    public interface NginxControllerInternal {
        public void doExtraConfigurationDuringStart();
    }

}

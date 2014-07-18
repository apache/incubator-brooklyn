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
package brooklyn.entity.brooklynnode;

import java.net.URI;
import java.util.List;
import java.util.Map;

import brooklyn.BrooklynVersion;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.java.UsesJava;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.ssh.BashCommands;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;

@ImplementedBy(BrooklynNodeImpl.class)
public interface BrooklynNode extends SoftwareProcess, UsesJava {

    @SuppressWarnings("serial")
    @SetFromFlag("copyToRundir")
    public static final BasicAttributeSensorAndConfigKey<Map<String,String>> COPY_TO_RUNDIR = new BasicAttributeSensorAndConfigKey<Map<String,String>>(
            new TypeToken<Map<String,String>>() {}, "brooklynnode.copytorundir", "URLs of resources to be copied across to the server, giving the path they are to be copied to", MutableMap.<String,String>of());
    
    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.SUGGESTED_VERSION, "0.7.0-SNAPSHOT"); // BROOKLYN_VERSION

    // Takes precedence over downloadUrl, if non-null
    @SetFromFlag("distroUploadUrl")
    public static final ConfigKey<String> DISTRO_UPLOAD_URL = ConfigKeys.newStringConfigKey(
            "brooklynnode.distro.uploadurl", "URL for uploading the brooklyn distro (retrieved locally and pushed to remote install location)", null);
    
    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new StringAttributeSensorAndConfigKey(
            SoftwareProcess.DOWNLOAD_URL,
            "<#if version?contains(\"SNAPSHOT\")>"+
                "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn&v=${version}&a=brooklyn-dist&c=dist&e=tar.gz" +
    		"<#else>"+
    		    "http://search.maven.org/remotecontent?filepath=io/brooklyn/brooklyn-dist/${version}/brooklyn-dist-${version}-dist.tar.gz"+
    		"</#if>");

    @SetFromFlag("subpathInArchive")
    ConfigKey<String> SUBPATH_IN_ARCHIVE = ConfigKeys.newStringConfigKey("brooklynnode.download.archive.subpath",
        "Path to the main directory in the archive being supplied for installation; "
        + "to use the root of an archive, specify '.'; "
        + "default value if left blank is the appropriate value for brooklyn,"
        + "e.g. 'brooklyn-"+BrooklynVersion.INSTANCE.getVersion()+"'", null);

    @SetFromFlag("managementUser")
    ConfigKey<String> MANAGEMENT_USER = ConfigKeys.newConfigKey("brooklynnode.managementUser",
            "The user for logging into the brooklyn web-console (also used for health-checks)",
            "admin");

    @SetFromFlag("managementPassword")
    ConfigKey<String> MANAGEMENT_PASSWORD =
            ConfigKeys.newStringConfigKey("brooklynnode.managementPassword", "Password for MANAGEMENT_USER", "password");

    /** useful e.g. with {@link BashCommands#generateKeyInDotSshIdRsaIfNotThere() } */
    @SetFromFlag("extraCustomizationScript")
    ConfigKey<String> EXTRA_CUSTOMIZATION_SCRIPT = ConfigKeys.newStringConfigKey("brooklynnode.customization.extraScript",
        "Optional additional script commands to run as part of customization; this might e.g. ensure id_rsa is set up",
        null);

    static enum ExistingFileBehaviour {
        USE_EXISTING, OVERWRITE, FAIL
    }
    
    @SetFromFlag("onExistingProperties")
    ConfigKey<ExistingFileBehaviour> ON_EXISTING_PROPERTIES_FILE = ConfigKeys.newConfigKey(ExistingFileBehaviour.class,
        "brooklynnode.properties.file.ifExists",
        "What to do in the case where brooklyn.properties already exists", 
        ExistingFileBehaviour.FAIL);

    @SetFromFlag("launchCommand")
    ConfigKey<String> LAUNCH_COMMAND = ConfigKeys.newStringConfigKey("brooklynnode.launch.command",
        "Path to the script to launch Brooklyn / the app relative to the subpath in the archive, defaulting to 'bin/brooklyn'", 
        "bin/brooklyn");

    @SetFromFlag("launchCommandCreatesPidFile")
    ConfigKey<Boolean> LAUNCH_COMMAND_CREATES_PID_FILE = ConfigKeys.newBooleanConfigKey("brooklynnode.launch.command.pid.updated",
        "Whether the launch script creates/updates the PID file, if not the entity will do so, "
        + "but note it will not necessarily kill sub-processes", 
        true);

    @SetFromFlag("app")
    public static final BasicAttributeSensorAndConfigKey<String> APP = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "brooklynnode.app", "Application (fully qualified class name) to launch using the brooklyn CLI", null);
    
    @SetFromFlag("locations")
    public static final BasicAttributeSensorAndConfigKey<String> LOCATIONS = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "brooklynnode.locations", "Locations to use when launching the app", null);
    
    /**
     * Exposed just for testing; remote path is not passed into the launched brooklyn so this won't be used!
     * This will likely change in a future version.
     */
    @VisibleForTesting
    @SetFromFlag("brooklynGlobalPropertiesRemotePath")
    public static final ConfigKey<String> BROOKLYN_GLOBAL_PROPERTIES_REMOTE_PATH = ConfigKeys.newStringConfigKey(
            "brooklynnode.brooklynproperties.global.remotepath", "Remote path for the global brooklyn.properties file to be uploaded", "${HOME}/.brooklyn/brooklyn.properties");
    
    @SetFromFlag("brooklynGlobalPropertiesUri")
    public static final ConfigKey<String> BROOKLYN_GLOBAL_PROPERTIES_URI = ConfigKeys.newStringConfigKey(
            "brooklynnode.brooklynproperties.global.uri", "URI for the global brooklyn properties file (to upload to ~/.brooklyn/brooklyn.properties", null);

    @SetFromFlag("brooklynGlobalPropertiesContents")
    public static final ConfigKey<String> BROOKLYN_GLOBAL_PROPERTIES_CONTENTS = ConfigKeys.newStringConfigKey(
            "brooklynnode.brooklynproperties.global.contents", "Contents for the global brooklyn properties file (to upload to ~/.brooklyn/brooklyn.properties", null);

    @SetFromFlag("brooklynLocalPropertiesRemotePath")
    public static final ConfigKey<String> BROOKLYN_LOCAL_PROPERTIES_REMOTE_PATH = ConfigKeys.newStringConfigKey(
            "brooklynnode.brooklynproperties.local.remotepath", "Remote path for the launch-specific brooklyn.properties file to be uploaded", "${driver.runDir}/brooklyn-local.properties");
    
    @SetFromFlag("brooklynLocalPropertiesUri")
    public static final ConfigKey<String> BROOKLYN_LOCAL_PROPERTIES_URI = ConfigKeys.newStringConfigKey(
            "brooklynnode.brooklynproperties.local.uri", "URI for the launch-specific brooklyn properties file (to upload to ~/.brooklyn/brooklyn.properties", null);

    @SetFromFlag("brooklynLocalPropertiesContents")
    public static final ConfigKey<String> BROOKLYN_LOCAL_PROPERTIES_CONTENTS = ConfigKeys.newStringConfigKey(
            "brooklynnode.brooklynproperties.local.contents", "Contents for the launch-specific brooklyn properties file (to upload to ~/.brooklyn/brooklyn.properties", null);
    
    // For use in testing primarily
    @SetFromFlag("brooklynCatalogRemotePath")
    public static final ConfigKey<String> BROOKLYN_CATALOG_REMOTE_PATH = ConfigKeys.newStringConfigKey(
            "brooklynnode.brooklyncatalog.remotepath", "Remote path for the brooklyn catalog.xml file to be uploaded", "${HOME}/.brooklyn/catalog.xml");
    
    @SetFromFlag("brooklynCatalogUri")
    public static final ConfigKey<String> BROOKLYN_CATALOG_URI = ConfigKeys.newStringConfigKey(
            "brooklynnode.brooklyncatalog.uri", "URI for the brooklyn catalog.xml file (to upload to ~/.brooklyn/catalog.xml", null);

    @SetFromFlag("brooklynCatalogContents")
    public static final ConfigKey<String> BROOKLYN_CATALOG_CONTENTS = ConfigKeys.newStringConfigKey(
            "brooklynnode.brooklyncatalog.contents", "Contents for the brooklyn catalog.xml file (to upload to ~/.brooklyn/catalog.xml", null);

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @SetFromFlag("enabledHttpProtocols")
    public static final BasicAttributeSensorAndConfigKey<List<String>> ENABLED_HTTP_PROTOCOLS = new BasicAttributeSensorAndConfigKey(
            List.class, "brooklynnode.webconsole.enabledHttpProtocols", "List of enabled protocols (e.g. http, https)", ImmutableList.of("http"));

    @SetFromFlag("httpPort")
    public static final PortAttributeSensorAndConfigKey HTTP_PORT = new PortAttributeSensorAndConfigKey(
            "brooklynnode.webconsole.httpPort", "HTTP Port for the brooklyn web-console", "8081+");
    
    @SetFromFlag("httpsPort")
    public static final PortAttributeSensorAndConfigKey HTTPS_PORT = new PortAttributeSensorAndConfigKey(
            "brooklynnode.webconsole.httpsPort", "HTTPS Port for the brooklyn web-console", "8443+");

    @SetFromFlag("noWebConsoleSecurity")
    public static final BasicAttributeSensorAndConfigKey<Boolean> NO_WEB_CONSOLE_AUTHENTICATION = new BasicAttributeSensorAndConfigKey<Boolean>(
            Boolean.class, "brooklynnode.webconsole.nosecurity", "Whether to start the web console with no security", false);

    @SetFromFlag("bindAddress")
    public static final BasicAttributeSensorAndConfigKey<String> WEB_CONSOLE_BIND_ADDRESS = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "brooklynnode.webconsole.bindAddress", "Specifies the IP address of the NIC to bind the Brooklyn Management Console to", null);

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @SetFromFlag("classpath")
    public static final BasicAttributeSensorAndConfigKey<List<String>> CLASSPATH = new BasicAttributeSensorAndConfigKey(
            List.class, "brooklynnode.classpath", "classpath to use, as list of URL entries", Lists.newArrayList());

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @SetFromFlag("portMapper")
    public static final ConfigKey<Function<? super Integer, ? extends Integer>> PORT_MAPPER = (ConfigKey) ConfigKeys.newConfigKey(Function.class,
            "brooklynnode.webconsole.portMapper", "Function for mapping private to public ports, for use in inferring the brooklyn URI", Functions.<Integer>identity());

    public static final AttributeSensor<URI> WEB_CONSOLE_URI = new BasicAttributeSensor<URI>(
            URI.class, "brooklynnode.webconsole.url", "URL of the brooklyn web-console");
    
    @SetFromFlag("noShutdownOnExit")
    public static final ConfigKey<Boolean> NO_SHUTDOWN_ON_EXIT = ConfigKeys.newBooleanConfigKey("brooklynnode.noshutdownonexit", 
        "Whether to shutdown entities on exit", false);
    
    public interface DeployBlueprintEffector {
        ConfigKey<Map<String,Object>> BLUEPRINT_CAMP_PLAN = new MapConfigKey<Object>(Object.class, "blueprintPlan",
            "CAMP plan for the blueprint to be deployed; currently only supports Java map or JSON string (not yet YAML)");
        ConfigKey<String> BLUEPRINT_TYPE = ConfigKeys.newStringConfigKey("blueprintType");
        ConfigKey<Map<String,Object>> BLUEPRINT_CONFIG = new MapConfigKey<Object>(Object.class, "blueprintConfig");
        Effector<String> DEPLOY_BLUEPRINT = Effectors.effector(String.class, "deployBlueprint")
            .description("Deploy a blueprint, either given a plan (as Java map or JSON string for a map), or given URL and optional config")
            .parameter(BLUEPRINT_TYPE)
            .parameter(BLUEPRINT_CONFIG)
            .parameter(BLUEPRINT_CAMP_PLAN)
            .buildAbstract();
    }
    
    public static Effector<String> DEPLOY_BLUEPRINT = DeployBlueprintEffector.DEPLOY_BLUEPRINT;
    
}

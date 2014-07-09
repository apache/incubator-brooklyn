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
package brooklyn.entity.webapp.jboss;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.HasShortName;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.javalang.JavaClassNames;

@Catalog(name="JBoss Application Server 7", description="AS7: an open source Java application server from JBoss", iconUrl="classpath:///jboss-logo.png")
@ImplementedBy(JBoss7ServerImpl.class)
public interface JBoss7Server extends JavaWebAppSoftwareProcess, JavaWebAppService, HasShortName {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION =
            ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "7.1.1.Final");
    // note: 7.1.2.Final fixes many bugs but is not available for download,
    // see https://community.jboss.org/thread/197780
    // 7.2.0.Final should be out during Q3 2012

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new StringAttributeSensorAndConfigKey(
            SoftwareProcess.DOWNLOAD_URL, "http://download.jboss.org/jbossas/7.1/jboss-as-${version}/jboss-as-${version}.tar.gz");

    @SetFromFlag("bindAddress")
    BasicAttributeSensorAndConfigKey<String> BIND_ADDRESS =
            new StringAttributeSensorAndConfigKey("jboss.bind.address",
                "Address of interface JBoss should listen on, defaulting 0.0.0.0 (but could set e.g. to attributeWhenReady(HOSTNAME)", 
                "0.0.0.0");

    @SetFromFlag("managementHttpPort")
    PortAttributeSensorAndConfigKey MANAGEMENT_HTTP_PORT =
            new PortAttributeSensorAndConfigKey("webapp.jboss.managementHttpPort", "Management port", "9990+");

    @SetFromFlag("managementHttpsPort")
    PortAttributeSensorAndConfigKey MANAGEMENT_HTTPS_PORT =
            new PortAttributeSensorAndConfigKey("webapp.jboss.managementHttpsPort", "Management port", "9443+");

    @SetFromFlag("managementNativePort")
    PortAttributeSensorAndConfigKey MANAGEMENT_NATIVE_PORT =
            new PortAttributeSensorAndConfigKey("webapp.jboss.managementNativePort", "Management native port", "10999+");

    /**
     * Port increments are the standard way to run multiple instances of AS7 on the same machine.
     */
    @SetFromFlag("portIncrement")
    ConfigKey<Integer> PORT_INCREMENT =
            ConfigKeys.newConfigKey("webapp.jboss.portIncrement", "Port increment for all ports in config file", 0);

    @SetFromFlag("deploymentTimeout")
    ConfigKey<Integer> DEPLOYMENT_TIMEOUT =
            ConfigKeys.newConfigKey("webapp.jboss.deploymentTimeout", "Deployment timeout, in seconds", 600);
    
    ConfigKey<String> TEMPLATE_CONFIGURATION_URL = ConfigKeys.newConfigKey(
            "webapp.jboss.templateConfigurationUrl", "Template file (in freemarker format) for the standalone.xml file", 
            JavaClassNames.resolveClasspathUrl(JBoss7Server.class, "jboss7-standalone.xml"));

    @SetFromFlag("managementUser")
    ConfigKey<String> MANAGEMENT_USER = ConfigKeys.newConfigKey("webapp.jboss.managementUser",
            "A user to be placed in the management realm. Brooklyn will use this user to poll sensors",
            "brooklyn");

    @SetFromFlag("managementPassword")
    ConfigKey<String> MANAGEMENT_PASSWORD =
            ConfigKeys.newStringConfigKey("webapp.jboss.managementPassword", "Password for MANAGEMENT_USER.");

    AttributeSensor<String> MANAGEMENT_URL =
            Sensors.newStringSensor("webapp.jboss.managementUrl", "URL where management endpoint is available");

    AttributeSensor<Integer> MANAGEMENT_STATUS =
            Sensors.newIntegerSensor("webapp.jboss.managementStatus", "HTTP response code for the management server");

    AttributeSensor<Boolean> MANAGEMENT_URL_UP = 
            Sensors.newBooleanSensor("webapp.jboss.managementUp", "Management server is responding with OK");
    
    public static final AttributeSensor<String> PID_FILE = Sensors.newStringSensor( "jboss.pid.file", "PID file");
}

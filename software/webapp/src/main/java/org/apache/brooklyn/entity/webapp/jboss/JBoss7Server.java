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

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.objs.HasShortName;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import org.apache.brooklyn.util.core.ResourcePredicates;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.javalang.JavaClassNames;

@Catalog(name="JBoss Application Server 7", description="AS7: an open source Java application server from JBoss", iconUrl="classpath:///jboss-logo.png")
@ImplementedBy(JBoss7ServerImpl.class)
public interface JBoss7Server extends JavaWebAppSoftwareProcess, HasShortName {

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

    ConfigKey<String> TEMPLATE_CONFIGURATION_URL = ConfigKeys.builder(String.class)
            .name("webapp.jboss.templateConfigurationUrl")
            .description("Template file (in freemarker format) for the standalone.xml file")
            .defaultValue(JavaClassNames.resolveClasspathUrl(JBoss7Server.class, "jboss7-standalone.xml"))
            .constraint(ResourcePredicates.urlExists())
            .build();

    @SetFromFlag("managementUser")
    ConfigKey<String> MANAGEMENT_USER = ConfigKeys.newConfigKey("webapp.jboss.managementUser",
            "A user to be placed in the management realm. Brooklyn will use this user to poll sensors",
            "brooklyn");

    @SetFromFlag("managementPassword")
    ConfigKey<String> MANAGEMENT_PASSWORD =
            ConfigKeys.newStringConfigKey("webapp.jboss.managementPassword", "Password for MANAGEMENT_USER.");

    @SetFromFlag("useHttpMonitoring")
    ConfigKey<Boolean> USE_HTTP_MONITORING = ConfigKeys.newConfigKey("httpMonitoring.enabled", "HTTP(S) monitoring enabled", Boolean.TRUE);

    AttributeSensor<String> MANAGEMENT_URL =
            Sensors.newStringSensor("webapp.jboss.managementUrl", "URL where management endpoint is available");

    AttributeSensor<Integer> MANAGEMENT_STATUS =
            Sensors.newIntegerSensor("webapp.jboss.managementStatus", "HTTP response code for the management server");

    AttributeSensor<Boolean> MANAGEMENT_URL_UP = 
            Sensors.newBooleanSensor("webapp.jboss.managementUp", "Management server is responding with OK");
    
    public static final AttributeSensor<String> PID_FILE = Sensors.newStringSensor("jboss.pid.file", "PID file");

}

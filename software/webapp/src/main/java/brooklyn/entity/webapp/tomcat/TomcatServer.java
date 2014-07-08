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
package brooklyn.entity.webapp.tomcat;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.HasShortName;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Tomcat instance.
 */
@Catalog(name="Tomcat Server", description="Apache Tomcat is an open source software implementation of the Java Servlet and JavaServer Pages technologies", iconUrl="classpath:///tomcat-logo.png")
@ImplementedBy(TomcatServerImpl.class)
public interface TomcatServer extends JavaWebAppSoftwareProcess, UsesJmx, HasShortName {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "7.0.53");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://download.nextag.com/apache/tomcat/tomcat-7/v${version}/bin/apache-tomcat-${version}.tar.gz");

    /**
     * Tomcat insists on having a port you can connect to for the sole purpose of shutting it down.
     * Don't see an easy way to disable it; causes collisions in its out-of-the-box location of 8005,
     * so override default here to a high-numbered port.
     */
    @SetFromFlag("shutdownPort")
    PortAttributeSensorAndConfigKey SHUTDOWN_PORT =
            ConfigKeys.newPortSensorAndConfigKey("tomcat.shutdownport", "Suggested shutdown port", PortRanges.fromString("31880+"));

    ConfigKey<Duration> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.START_TIMEOUT, Duration.FIVE_MINUTES);

    AttributeSensor<String> CONNECTOR_STATUS =
            new BasicAttributeSensor<String>(String.class, "webapp.tomcat.connectorStatus", "Catalina connector state name");

    AttributeSensor<String> JMX_SERVICE_URL = UsesJmx.JMX_URL;

}

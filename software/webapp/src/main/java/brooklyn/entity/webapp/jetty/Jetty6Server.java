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
package brooklyn.entity.webapp.jetty;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.HasShortName;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Jetty instance.
 */
@Catalog(name="Jetty6 Server", description="Old version (v6 @ Mortbay) of the popular Jetty webapp container", iconUrl="classpath:///jetty-logo.png")
@ImplementedBy(Jetty6ServerImpl.class)
public interface Jetty6Server extends JavaWebAppSoftwareProcess, UsesJmx, HasShortName {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "6.1.26");

    ConfigKey<Duration> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.START_TIMEOUT, Duration.FIVE_MINUTES);

    @SetFromFlag("configXmlTemplateUrl")
    ConfigKey<String> CONFIG_XML_TEMPLATE_URL = ConfigKeys.newStringConfigKey("jetty.configXml.templateUrl", "Extra XML configuration file template URL if required");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://dist.codehaus.org/jetty/jetty-${version}/jetty-${version}.zip");

    AttributeSensor<Integer> RESPONSES_4XX_COUNT =
            Sensors.newIntegerSensor("webapp.responses.4xx", "Responses in the 400's");

    AttributeSensor<Integer> RESPONSES_5XX_COUNT =
            Sensors.newIntegerSensor("webapp.responses.5xx", "Responses in the 500's");

}

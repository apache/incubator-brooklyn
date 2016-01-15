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
package org.apache.brooklyn.entity.proxy;

import java.util.Set;

import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.group.Cluster;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
@ImplementedBy(AbstractControllerImpl.class)
public interface AbstractController extends SoftwareProcess, LoadBalancer {

    @SetFromFlag("domain")
    BasicAttributeSensorAndConfigKey<String> DOMAIN_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "proxy.domainName", "Domain name that this controller responds to, or null if it responds to all domains", null);

    @SetFromFlag("ssl")
    ConfigKey<ProxySslConfig> SSL_CONFIG = ConfigKeys.newConfigKey(ProxySslConfig.class,
            "proxy.ssl.config", "Configuration (e.g. certificates) for SSL; causes server to run with HTTPS instead of HTTP");
    

    @SetFromFlag("serviceUpUrlPath")
    ConfigKey<String> SERVICE_UP_URL_PATH = ConfigKeys.newStringConfigKey(
            "controller.config.serviceUpUrlPath", "The path that will be appended to the root URL to determine SERVICE_UP", "");

    boolean isActive();

    ProxySslConfig getSslConfig();

    boolean isSsl();

    String getProtocol();

    /** returns primary domain this controller responds to, or null if it responds to all domains */
    String getDomain();

    Integer getPort();

    /** primary URL this controller serves, if one can / has been inferred */
    String getUrl();

    AttributeSensor<Integer> getPortNumberSensor();

    AttributeSensor<String> getHostnameSensor();

    AttributeSensor<String> getHostAndPortSensor();
    
    Set<String> getServerPoolAddresses();
}

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
package org.apache.brooklyn.entity.dns.geoscaling;

import java.net.URI;

import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.dns.AbstractGeoDnsService;
import org.apache.brooklyn.entity.webapp.WebAppServiceConstants;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * A geo-DNS service using geoscaling.com.
 * <p>
 * AWS users should note that if the Brooklyn server managing this entity is in the same
 * region as the server being geoscaled then they must set {@link #INCLUDE_HOMELESS_ENTITIES}
 * to true, as IP lookups of the server will resolve the private address and it will be
 * ignored by default.
 */
@ImplementedBy(GeoscalingDnsServiceImpl.class)
public interface GeoscalingDnsService extends AbstractGeoDnsService {
    
    @SetFromFlag("sslTrustAll")
    ConfigKey<Boolean> SSL_TRUST_ALL = ConfigKeys.newBooleanConfigKey(
            "ssl.trustAll",
            "Whether to trust all certificates, or to fail with 'peer not authenticated' if untrusted (default false)",
            false);

    @SetFromFlag("randomizeSubdomainName")
    ConfigKey<Boolean> RANDOMIZE_SUBDOMAIN_NAME = ConfigKeys.newBooleanConfigKey(
            "randomize.subdomain.name");

    @SetFromFlag("username")
    ConfigKey<String> GEOSCALING_USERNAME = ConfigKeys.newStringConfigKey(
            "geoscaling.username");

    @SetFromFlag("password")
    ConfigKey<String> GEOSCALING_PASSWORD = ConfigKeys.newStringConfigKey(
            "geoscaling.password");

    @SetFromFlag("primaryDomainName")
    ConfigKey<String> GEOSCALING_PRIMARY_DOMAIN_NAME = ConfigKeys.newStringConfigKey(
            "geoscaling.primary.domain.name");

    @SetFromFlag("smartSubdomainName")
    ConfigKey<String> GEOSCALING_SMART_SUBDOMAIN_NAME = ConfigKeys.newStringConfigKey(
            "geoscaling.smart.subdomain.name");
    
    AttributeSensor<String> GEOSCALING_ACCOUNT = Sensors.newStringSensor(
            "geoscaling.account", "Active user account for the GeoScaling.com service");

    AttributeSensor<URI> MAIN_URI = Attributes.MAIN_URI;

    AttributeSensor<String> ROOT_URL = WebAppServiceConstants.ROOT_URL;

    AttributeSensor<String> MANAGED_DOMAIN = Sensors.newStringSensor(
            "geoscaling.managed.domain",
            "Fully qualified domain name that will be geo-redirected; " +
                    "this will be the same as "+ROOT_URL.getName()+" but the latter will only be set when the domain has active targets");
    
    void applyConfig();
    
    /** minimum/default TTL here is 300s = 5m */
    long getTimeToLiveSeconds();
}

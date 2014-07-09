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
package brooklyn.entity.dns.geoscaling;

import brooklyn.config.ConfigKey;
import brooklyn.entity.dns.AbstractGeoDnsService;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.webapp.WebAppServiceConstants;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(GeoscalingDnsServiceImpl.class)
public interface GeoscalingDnsService extends AbstractGeoDnsService {
    
    @SetFromFlag("randomizeSubdomainName")
    public static final ConfigKey<Boolean> RANDOMIZE_SUBDOMAIN_NAME = new BasicConfigKey<Boolean>(
            Boolean.class, "randomize.subdomain.name");
    @SetFromFlag("username")
    public static final ConfigKey<String> GEOSCALING_USERNAME = new BasicConfigKey<String>(
            String.class, "geoscaling.username");
    @SetFromFlag("password")
    public static final ConfigKey<String> GEOSCALING_PASSWORD = new BasicConfigKey<String>(
            String.class, "geoscaling.password");
    @SetFromFlag("primaryDomainName")
    public static final ConfigKey<String> GEOSCALING_PRIMARY_DOMAIN_NAME = new BasicConfigKey<String>(
            String.class, "geoscaling.primary.domain.name");
    @SetFromFlag("smartSubdomainName")
    public static final ConfigKey<String> GEOSCALING_SMART_SUBDOMAIN_NAME = new BasicConfigKey<String>(
            String.class, "geoscaling.smart.subdomain.name");
    
    public static final AttributeSensor<String> GEOSCALING_ACCOUNT = new BasicAttributeSensor<String>(
            String.class, "geoscaling.account", "Active user account for the GeoScaling.com service");
    public static final AttributeSensor<String> ROOT_URL = WebAppServiceConstants.ROOT_URL;
    public static final AttributeSensor<String> MANAGED_DOMAIN = new BasicAttributeSensor<String>(
            String.class, "geoscaling.managed.domain", "Fully qualified domain name that will be geo-redirected; " +
            		"this will be the same as "+ROOT_URL.getName()+" but the latter will only be set when the domain has active targets");
    
    public void applyConfig();
    
    /** minimum/default TTL here is 300s = 5m */
    public long getTimeToLiveSeconds();
}

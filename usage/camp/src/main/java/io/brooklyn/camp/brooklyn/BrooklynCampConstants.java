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
package io.brooklyn.camp.brooklyn;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

import io.brooklyn.camp.CampPlatform;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;

public class BrooklynCampConstants {

    /* These are only advertised as ConfigKeys currently, as they are not automatically set as sensors. 
     * To fix if EntitySpec allows us to specify sensor values, or there is an automatic way they get converted from config. */
    
    public static final String PLAN_ID_FLAG = "planId";
    public static final HasConfigKey<String> PLAN_ID = new BasicAttributeSensorAndConfigKey<String>(String.class, "camp.plan.id", 
        "Identifier supplied in the deployment plan for component to which this entity corresponds "
        + "(human-readable, for correlating across plan, template, and instance)");

    public static final HasConfigKey<String> TEMPLATE_ID = new BasicAttributeSensorAndConfigKey<String>(String.class, "camp.template.id", 
        "UID of the component in the CAMP template from which this entity was created");

    public static final ConfigKey<CampPlatform> CAMP_PLATFORM = BrooklynServerConfig.CAMP_PLATFORM;

    public static final Set<String> YAML_URL_PROTOCOL_WHITELIST = ImmutableSet.of("classpath", "http");
}

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
package org.apache.brooklyn.camp.brooklyn;

import java.util.Set;

import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.config.ConfigInheritance;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;

import com.google.common.collect.ImmutableSet;

public class BrooklynCampConstants {

    public static final String PLAN_ID_FLAG = "planId";

    public static final ConfigKey<String> PLAN_ID = ConfigKeys.builder(String.class, "camp.plan.id")
            .description("Identifier supplied in the deployment plan for component to which this entity corresponds "
                        + "(human-readable, for correlating across plan, template, and instance)")
            .inheritance(ConfigInheritance.NONE)
            .build();

    public static final ConfigKey<String> TEMPLATE_ID = ConfigKeys.builder(String.class, "camp.template.id")
            .description("UID of the component in the CAMP template from which this entity was created")
            .inheritance(ConfigInheritance.NONE)
            .build();

    public static final ConfigKey<CampPlatform> CAMP_PLATFORM = ConfigKeys.newConfigKey(CampPlatform.class, "brooklyn.camp.platform",
            "Config set at brooklyn management platform to find the CampPlatform instance (bi-directional)");

    public static final Set<String> YAML_URL_PROTOCOL_WHITELIST = ImmutableSet.of("classpath", "http");
}

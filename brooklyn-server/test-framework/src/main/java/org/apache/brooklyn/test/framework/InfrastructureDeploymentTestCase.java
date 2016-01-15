/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.test.framework;

import java.util.List;

import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;

/**
 * Created by graememiller on 04/12/2015.
 */
@ImplementedBy(value = InfrastructureDeploymentTestCaseImpl.class)
public interface InfrastructureDeploymentTestCase extends TargetableTestComponent {

    /**
     * Entity specs to deploy. These will be deployed second, after the INFRASTRUCTURE_SPEC has been deployed. These specs will be deployed to the DEPLOYMENT_LOCATION.
     * All children will be deployed after this
     */
    ConfigKey<List<EntitySpec<? extends SoftwareProcess>>> ENTITY_SPEC_TO_DEPLOY = ConfigKeys.newConfigKey(new TypeToken<List<EntitySpec<? extends SoftwareProcess>>>(){}, "infrastructure.deployment.entity.specs", "Entity specs to deploy to infrastructure");


    /**
     * Infrastructure to deploy. This will be deployed first, then the ENTITY_SPEC_TO_DEPLOY will be deployed, then any children
     */
    ConfigKey<EntitySpec<? extends StartableApplication>> INFRASTRUCTURE_SPEC = ConfigKeys.newConfigKey(new TypeToken<EntitySpec<? extends StartableApplication>>(){}, "infrastructure.deployment.spec", "Infrastructure to deploy");


    /**
     * The The location to deploy ENTITY_SPEC_TO_DEPLOY.
     */
    ConfigKey<String> DEPLOYMENT_LOCATION_SENSOR_NAME = ConfigKeys.newStringConfigKey("infrastructure.deployment.location.sensor", "Name of the sensor of INFRASTRUCTURE_SPEC to retrieve the Location to deploy the entity to");


}

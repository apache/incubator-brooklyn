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

import com.google.common.collect.ImmutableList;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;

import java.util.Collection;

/**
 * Created by graememiller on 04/12/2015.
 */
public class InfrastructureDeploymentTestCaseImpl extends TestCaseImpl implements InfrastructureDeploymentTestCase {

    @Override
    public void start(@EffectorParam(name = "locations") Collection<? extends Location> locations) {
        //Create the infrastructure
        EntitySpec<StartableApplication> infrastructureSpec = config().get(INFRASTRUCTURE_SPEC);
        StartableApplication infrastructure = this.addChild(infrastructureSpec);
        infrastructure.start(locations);

        //Get the location
        String deploymentLocationSensorName = config().get(DEPLOYMENT_LOCATION_SENSOR_NAME);
        Location locationToDeployTo = infrastructure.sensors().get(Sensors.newSensor(Location.class, deploymentLocationSensorName));


        //Start the child entity
        EntitySpec<SoftwareProcess> entityToDeploySpec = config().get(ENTITY_SPEC_TO_DEPLOY);
        SoftwareProcess entityToDeploy = this.addChild(entityToDeploySpec);
        entityToDeploy.start(ImmutableList.of(locationToDeployTo));


        //Defer to super class to start children
        super.start(locations);
    }
}

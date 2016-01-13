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

import java.util.Collection;
import java.util.List;

import com.google.common.collect.ImmutableList;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;

/**
 * Created by graememiller on 04/12/2015.
 */
public class InfrastructureDeploymentTestCaseImpl extends TestCaseImpl implements InfrastructureDeploymentTestCase {

    @Override
    public void start(@EffectorParam(name = "locations") Collection<? extends Location> locations) {
        setServiceState(false, Lifecycle.STARTING);

        //Create the infrastructure
        EntitySpec<? extends StartableApplication> infrastructureSpec = config().get(INFRASTRUCTURE_SPEC);
        if (infrastructureSpec == null) {
            setServiceState(false, Lifecycle.ON_FIRE);
            throw new IllegalArgumentException(INFRASTRUCTURE_SPEC + " not configured");
        }

        StartableApplication infrastructure = this.addChild(infrastructureSpec);
        infrastructure.start(locations);

        //Get the location
        String deploymentLocationSensorName = config().get(DEPLOYMENT_LOCATION_SENSOR_NAME);
        if (deploymentLocationSensorName == null) {
            setServiceState(false, Lifecycle.ON_FIRE);
            throw new IllegalArgumentException(DEPLOYMENT_LOCATION_SENSOR_NAME + " not configured");
        }

        Location locationToDeployTo = infrastructure.sensors().get(Sensors.newSensor(Location.class, deploymentLocationSensorName));
        if (locationToDeployTo == null) {
            setServiceState(false, Lifecycle.ON_FIRE);
            throw new IllegalArgumentException("Infrastructure does not have a location configured on sensor "+deploymentLocationSensorName);
        }

        //Start the child entity
        List<EntitySpec<? extends SoftwareProcess>> entitySpecsToDeploy = config().get(ENTITY_SPEC_TO_DEPLOY);
        if (entitySpecsToDeploy == null || entitySpecsToDeploy.isEmpty()) {
            setServiceState(false, Lifecycle.ON_FIRE);
            throw new IllegalArgumentException(ENTITY_SPEC_TO_DEPLOY + " not configured");
        }
        for (EntitySpec<? extends SoftwareProcess> softwareProcessEntitySpec : entitySpecsToDeploy) {
            SoftwareProcess entityToDeploy = this.addChild(softwareProcessEntitySpec);
            entityToDeploy.start(ImmutableList.of(locationToDeployTo));
        }

        //Defer to super class to start children
        super.start(locations);
        setServiceState(true, Lifecycle.RUNNING);
    }

    /**
     * Sets the state of the Entity. Useful so that the GUI shows the correct icon.
     *
     * @param serviceUpState     Whether or not the entity is up.
     * @param serviceStateActual The actual state of the entity.
     */
    private void setServiceState(final boolean serviceUpState, final Lifecycle serviceStateActual) {
        sensors().set(Attributes.SERVICE_UP, serviceUpState);
        sensors().set(Attributes.SERVICE_STATE_ACTUAL, serviceStateActual);
    }
}

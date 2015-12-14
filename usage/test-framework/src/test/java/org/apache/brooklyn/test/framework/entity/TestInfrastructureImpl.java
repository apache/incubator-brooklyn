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

package org.apache.brooklyn.test.framework.entity;

import java.util.Collection;

import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.stock.BasicApplicationImpl;

/**
 * Created by graememiller on 17/12/2015.
 */
public class TestInfrastructureImpl extends BasicApplicationImpl implements TestInfrastructure {

    private final AttributeSensorAndConfigKey<Location, Location> DEPLOYMENT_LOCATION = ConfigKeys.newSensorAndConfigKey(
            new TypeToken<Location>() {
            },
            "deploymentLocationSensor", "The location to deploy to");

    @Override
    public void postStart(Collection<? extends Location> locations) {
        super.postStart(locations);
        sensors().set(DEPLOYMENT_LOCATION, config().get(DEPLOYMENT_LOCATION));
    }
}

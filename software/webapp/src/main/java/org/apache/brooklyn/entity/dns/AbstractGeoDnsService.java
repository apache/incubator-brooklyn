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
package org.apache.brooklyn.entity.dns;

import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.geo.HostGeoInfo;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import com.google.common.reflect.TypeToken;

public interface AbstractGeoDnsService extends Entity {
    
    @SetFromFlag("includeHomelessEntities")
    ConfigKey<Boolean> INCLUDE_HOMELESS_ENTITIES = ConfigKeys.newBooleanConfigKey(
            "geodns.includeHomeless", "Whether to include entities whose geo-coordinates cannot be inferred", false);

    @SetFromFlag("useHostnames")
    ConfigKey<Boolean> USE_HOSTNAMES = ConfigKeys.newBooleanConfigKey(
            "geodns.useHostnames", "Whether to use the hostname for the returned value for routing, rather than IP address (defaults to true)", true);

    @SetFromFlag("provider")
    ConfigKey<Group> ENTITY_PROVIDER = ConfigKeys.newConfigKey(Group.class,
            "geodns.entityProvider", "The group whose members should be tracked");

    AttributeSensor<Lifecycle> SERVICE_STATE_ACTUAL = Attributes.SERVICE_STATE_ACTUAL;
    AttributeSensor<Boolean> SERVICE_UP = Startable.SERVICE_UP;
    AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    AttributeSensor<String> ADDRESS = Attributes.ADDRESS;

    AttributeSensor<Map<String,String>> TARGETS = Sensors.newSensor(new TypeToken<Map<String, String>>() {},
            "geodns.targets", "Map of targets currently being managed (entity ID to URL)");

    void setServiceState(Lifecycle state);
    
    /** sets target to be a group whose *members* will be searched (non-Group items not supported) */
    // prior to 0.7.0 the API accepted non-group items, but did not handle them correctly
    void setTargetEntityProvider(final Group entityProvider);
    
    /** should return the hostname which this DNS service is configuring */
    String getHostname();
    
    Map<Entity, HostGeoInfo> getTargetHosts();
}

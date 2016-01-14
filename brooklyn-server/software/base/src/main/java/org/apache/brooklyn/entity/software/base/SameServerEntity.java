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
package org.apache.brooklyn.entity.software.base;

import java.util.Collection;
import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ComputeServiceIndicatorsFromChildrenAndMembers;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.sensor.BasicAttributeSensor;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.QuorumCheck;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import com.google.common.reflect.TypeToken;

/**
 * An entity that, on start({@link MachineProvisioningLocation}), will obtain a machine
 * and pass that to each of its children by calling their {@link Startable#start(java.util.Collection)}
 * methods with that machine.
 * 
 * Thus multiple entities can be set up to run on the same machine.
 * 
 * @author aled
 */
@ImplementedBy(SameServerEntityImpl.class)
@SuppressWarnings("serial")
public interface SameServerEntity extends Entity, Startable {

    @SetFromFlag("provisioningProperties")
    ConfigKey<Map<String,Object>> PROVISIONING_PROPERTIES = ConfigKeys.newConfigKey(
            new TypeToken<Map<String, Object>>() {},
            "provisioning.properties", "Custom properties to be passed in when provisioning a new machine",
            MutableMap.<String, Object>of());
    
    ConfigKey<QuorumCheck> UP_QUORUM_CHECK = ComputeServiceIndicatorsFromChildrenAndMembers.UP_QUORUM_CHECK;
    ConfigKey<QuorumCheck> RUNNING_QUORUM_CHECK = ComputeServiceIndicatorsFromChildrenAndMembers.RUNNING_QUORUM_CHECK;

    ConfigKey<Collection<Integer>> REQUIRED_OPEN_LOGIN_PORTS = SoftwareProcess.REQUIRED_OPEN_LOGIN_PORTS;

    ConfigKey<String> INBOUND_PORTS_CONFIG_REGEX = SoftwareProcess.INBOUND_PORTS_CONFIG_REGEX;

    ConfigKey<Boolean> INBOUND_PORTS_AUTO_INFER = SoftwareProcess.INBOUND_PORTS_AUTO_INFER;

    AttributeSensor<Lifecycle> SERVICE_STATE_ACTUAL = Attributes.SERVICE_STATE_ACTUAL;

    @SuppressWarnings("rawtypes")
    AttributeSensor<MachineProvisioningLocation> PROVISIONING_LOCATION = new BasicAttributeSensor<MachineProvisioningLocation>(
            MachineProvisioningLocation.class, "softwareservice.provisioningLocation", "Location used to provision a machine where this is running");
    
    AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    
    AttributeSensor<String> ADDRESS = Attributes.ADDRESS;
}

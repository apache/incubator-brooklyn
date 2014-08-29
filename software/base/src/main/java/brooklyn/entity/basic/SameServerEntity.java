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
package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ServiceStateLogic.ComputeServiceIndicatorsFromChildrenAndMembers;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

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

    AttributeSensor<Lifecycle> SERVICE_STATE_ACTUAL = Attributes.SERVICE_STATE_ACTUAL;

    @SuppressWarnings("rawtypes")
    AttributeSensor<MachineProvisioningLocation> PROVISIONING_LOCATION = new BasicAttributeSensor<MachineProvisioningLocation>(
            MachineProvisioningLocation.class, "softwareservice.provisioningLocation", "Location used to provision a machine where this is running");
    
    AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    
    AttributeSensor<String> ADDRESS = Attributes.ADDRESS;
}

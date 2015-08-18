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
package brooklyn.policy.os;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.internal.EntityLocal;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.entity.core.AbstractEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.location.basic.WinRmMachineLocation;
import org.apache.brooklyn.policy.core.AbstractPolicy;
import org.apache.brooklyn.sensor.core.Sensors;

import com.google.common.annotations.Beta;

/**
 * When attached to an entity, this will monitor for when an {@link WinRmMachineLocation} is added to that entity
 * (e.g. when a VM has been provisioned for it).
 * 
 * The policy will then add a sensor that advertises the Administrator login details.
 * 
 * A preferred mechanism would be for an external key-management tool to provide access to the credentials.
 */
@Beta
public class AdvertiseWinrmLoginPolicy extends AbstractPolicy implements SensorEventListener<Location> {

    // TODO Would like support user-creation over WinRM
    
    private static final Logger LOG = LoggerFactory.getLogger(AdvertiseWinrmLoginPolicy.class);

    public static final AttributeSensor<String> VM_USER_CREDENTIALS = Sensors.newStringSensor(
            "vm.user.credentials",
            "The \"<user> : <password> @ <hostname>:<port>\"");

    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        subscribe(entity, AbstractEntity.LOCATION_ADDED, this);
    }

    @Override
    public void onEvent(SensorEvent<Location> event) {
        final Entity entity = event.getSource();
        final Location loc = event.getValue();
        if (loc instanceof WinRmMachineLocation) {
            advertiseUserAsync(entity, (WinRmMachineLocation)loc);
        }
    }

    protected void advertiseUserAsync(final Entity entity, final WinRmMachineLocation machine) {
        String user = machine.getUser();
        String hostname = machine.getHostname();
        int port = machine.config().get(WinRmMachineLocation.WINRM_PORT);
        String password = machine.config().get(WinRmMachineLocation.PASSWORD);
        
        String creds = user + " : " + password + " @ " +hostname + ":" + port;
        
        LOG.info("Advertising user "+user+" @ "+hostname+":"+port);
        
        ((EntityLocal)entity).setAttribute(VM_USER_CREDENTIALS, creds);
    }
}

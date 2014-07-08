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
package brooklyn.entity.driver;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcessDriver;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;

public class MockSshDriver implements SoftwareProcessDriver {

    public int numCallsToRunApp = 0;
    private final EntityLocal entity;
    private final SshMachineLocation machine;

    public MockSshDriver(EntityLocal entity, SshMachineLocation machine) {
        this.entity = entity;
        this.machine = machine;
    }
    
    @Override
    public void start() {
        numCallsToRunApp++;
    }

    @Override
    public boolean isRunning() {
        return numCallsToRunApp>0;
    }

    @Override
    public EntityLocal getEntity() {
        return entity;
    }

    @Override
    public Location getLocation() {
        return machine;
    }

    @Override
    public void rebind() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void restart() {
    }
    
    @Override
    public void kill() {
    }
}

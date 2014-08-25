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
package brooklyn.test.entity;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEventListener;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.SubscriptionHandle;
import brooklyn.util.logging.LoggingSetup;

/**
 * Mock application for testing.
 */
public class TestApplicationImpl extends AbstractApplication implements TestApplication {
    private static final Logger LOG = LoggerFactory.getLogger(TestApplication.class);

    static {
        // our tests should redirect the j.u.l logging messages to logback 
        LoggingSetup.installJavaUtilLoggingBridge();
    }

    public TestApplicationImpl() {
        super();
    }

    public TestApplicationImpl(Map<?,?> flags) {
        super(flags);
    }

    @Override
    public <T extends Entity> T createAndManageChild(EntitySpec<T> spec) {
        if (!getManagementSupport().isDeployed()) throw new IllegalStateException("Entity "+this+" not managed");
        T child = addChild(spec);
        getEntityManager().manage(child);
        return child;
    }
    
    @Override
    public <T> SubscriptionHandle subscribeToMembers(Group parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return getSubscriptionContext().subscribeToMembers(parent, sensor, listener);
    }

    @Override
    public String toString() {
        String id = getId();
        return "Application["+id.substring(Math.max(0, id.length()-8))+"]";
    }

    @Override
    public LocalhostMachineProvisioningLocation newLocalhostProvisioningLocation() {
        return (LocalhostMachineProvisioningLocation) getManagementContext().getLocationRegistry().resolve("localhost");
    }
    @Override
    public LocalhostMachineProvisioningLocation newLocalhostProvisioningLocation(Map<?,?> flags) {
        return (LocalhostMachineProvisioningLocation) getManagementContext().getLocationManager().createLocation(
            LocationSpec.create(LocalhostMachineProvisioningLocation.class).configure(flags));
    }

    @Override
    protected void logApplicationLifecycle(String message) {
        // for tests, log this at debug so we see test info more
        log.debug(message+" application "+this);
    }
    
}

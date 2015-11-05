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
package org.apache.brooklyn.test.framework;

import com.google.common.base.Supplier;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.entity.software.base.lifecycle.MachineLifecycleEffectorTasks;
import org.apache.http.util.Asserts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;

public class SimpleCommandLifecycleEffectorTasks extends MachineLifecycleEffectorTasks {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleCommandLifecycleEffectorTasks.class);
    private MachineLocation location;

    protected Location getLocation(@Nullable Collection<? extends Location> locations) {
        return super.getLocation(entity().filterLocations(locations));
    }

    @Override
    protected void preStartCustom(MachineLocation machine) {
        location = machine;
        super.preStartCustom(location);
        LOG.debug("Performing lifecycle preStartCustom on simple command");
        entity().initDriver(location);
    }

    @Override
    protected void preRestartCustom() {
        LOG.debug("Performing lifecycle preStartCustom on simple command");
        Asserts.notNull(location, "Cannot restart with no location");
        entity().initDriver(location);
    }

    @Override
    protected String startProcessesAtMachine(Supplier<MachineLocation> machineS) {
        LOG.debug("Performing lifecycle startProcessesAtMachine on simple command");
        entity().getDriver().start();
        return "Started with driver " + entity().getDriver();
    }

    @Override
    protected String stopProcessesAtMachine() {
        LOG.debug("Performing lifecycle stopProcessesAtMachine on simple command");
        entity().getDriver().stop();
        return "Stopped";
    }

    protected SimpleCommandImpl entity() {
        return (SimpleCommandImpl) super.entity();
    }
}

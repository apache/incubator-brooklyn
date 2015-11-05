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

import org.apache.brooklyn.api.entity.drivers.DriverDependentEntity;
import org.apache.brooklyn.api.entity.drivers.EntityDriverManager;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import static org.apache.brooklyn.core.entity.lifecycle.Lifecycle.*;
import static org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.setExpectedState;

/**
 * Implementation for {@link SimpleCommand}.
 */
public class SimpleCommandImpl extends AbstractEntity
        implements SimpleCommand, DriverDependentEntity<SimpleCommandDriver> {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleCommandImpl.class);
    private static final int A_LINE = 80;
    private transient SimpleCommandDriver driver;

    private Collection<? extends Location> locations;

    @Override
    public SimpleCommandDriver getDriver() {
        return driver;
    }

    @Override
    public Class<SimpleCommandDriver> getDriverInterface() {
        return SimpleCommandDriver.class;
    }

    /**
     * Gives the opportunity to sub-classes to do additional work based on the result of the command.
     */
    protected void handle(SimpleCommandDriver.Result result) {
        LOG.debug("Result is {}\nwith output [\n{}\n] and error [\n{}\n]", new Object[] {
                result.getExitCode(), shorten(result.getStdout()), shorten(result.getStderr())
        });
    }

    private String shorten(String text) {
        return Strings.maxlenWithEllipsis(text, A_LINE);
    }

    /**
     * Does nothing in this class but gives sub-classes the opportunity to filter locations according to some criterion.
     */
    public Collection<? extends Location> filterLocations(Collection<? extends Location> locations) {
        return locations;
    }


    @Override
    public void init() {
        super.init();
        getLifecycleEffectorTasks().attachLifecycleEffectors(this);
    }


    protected void initDriver(MachineLocation machine) {
        LOG.debug("Initializing simple command driver");
        SimpleCommandDriver newDriver = doInitDriver(machine);
        if (newDriver == null) {
            throw new UnsupportedOperationException("cannot start "+this+" on "+machine+": no driver available");
        }
        driver = newDriver;
    }

    protected SimpleCommandDriver doInitDriver(MachineLocation machine) {
        if (driver!=null) {
            if (machine.equals(driver.getLocation())) {
                return driver; //just reuse
            } else {
                LOG.warn("driver/location change is untested for {} at {}; changing driver and continuing", this, machine);
                return newDriver(machine);
            }
        } else {
            return newDriver(machine);
        }
    }

    protected SimpleCommandDriver newDriver(MachineLocation machine) {
        LOG.debug("Creating new simple command driver for {} from management context", machine);
        EntityDriverManager entityDriverManager = getManagementContext().getEntityDriverManager();
        return entityDriverManager.build(this, machine);
    }

    @Override
    public void start(@EffectorParam(name = "locations") Collection<? extends Location> locations) {
        this.locations = locations;
        startOnLocations();
    }

    protected void startOnLocations() {
        setExpectedState(this, STARTING);
        int size = locations.size();
        LOG.debug("Starting simple command at {} locations{}", size,
                size > 0 ? " beginning " + locations.iterator().next() : "");
        try {
            execute(locations);
            setUpAndRunState(true, RUNNING);

        } catch (final Exception e) {
            setUpAndRunState(false, ON_FIRE);
            throw Exceptions.propagate(e);
        }
    }

    private void execute(Collection<? extends Location> locations) {
        SimpleCommandDriver.Result result = null;
        String downloadUrl = getConfig(DOWNLOAD_URL);
        if (Strings.isNonBlank(downloadUrl)) {
            String scriptDir = getConfig(SCRIPT_DIR);
            result = getDriver().executeDownloadedScript(locations, downloadUrl, scriptDir);

        } else {
            String command = getConfig(DEFAULT_COMMAND);
            if (Strings.isBlank(command)) {
                throw new IllegalArgumentException("No default command and no downloadUrl provided");
            }

            result = getDriver().execute(locations, command);
        }
        handle(result);
    }


    @Override
    public void stop() {
        LOG.debug("Stopping simple command");
        setUpAndRunState(false, STOPPED);
    }

    @Override
    public void restart() {
        LOG.debug("Restarting simple command");
        setUpAndRunState(true, RUNNING);
    }

    private void setUpAndRunState(boolean up, Lifecycle status) {
        sensors().set(SERVICE_UP, up);
        setExpectedState(this, status);
    }

    protected SimpleCommandLifecycleEffectorTasks getLifecycleEffectorTasks () {
        return new SimpleCommandLifecycleEffectorTasks();
    }

}

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

import com.google.common.annotations.Beta;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.TaskAdaptable;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.trait.StartableMethods;
import org.apache.brooklyn.entity.software.base.lifecycle.AbstractLifecycleEffectorTasks;
import org.apache.brooklyn.location.paas.PaasLocation;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.Callable;

@Beta
public class PaasLifecycleEffectorTasks extends AbstractLifecycleEffectorTasks {

    private static final Logger log = LoggerFactory.getLogger(PaasLifecycleEffectorTasks.class);

    @Override
    protected SoftwareProcessImpl entity() {
        return (SoftwareProcessImpl) super.entity();
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        ServiceStateLogic.setExpectedState(entity(), Lifecycle.STARTING);
        try {

            PaasLocation location = (PaasLocation) entity().getLocation(locations);

            preStartProcess(location);
            startProcess();
            postStartProcess();

            ServiceStateLogic.setExpectedState(entity(), Lifecycle.RUNNING);
        } catch (Throwable t) {
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.ON_FIRE);
            log.error("Error error starting entity {}", entity());
            throw Exceptions.propagate(t);
        }
    }

    //TODO: This method could be uploaded to the super class.
    protected void preStartProcess(PaasLocation location) {
        createDriver(location);
        entity().preStart();
    }

    /**
     * Create the driver ensuring that the location is ready.
     */
    private void createDriver(PaasLocation location) {
        if (location != null) {
            entity().initDriver(location);
        } else {
            throw new ExceptionInInitializerError("Location should not be null in " + this +
                    " the driver needs a initialized Location");
        }
    }

    protected void startProcess() {
        entity().getDriver().start();
    }

    protected void postStartProcess() {
        entity().postDriverStart();
        if (entity().connectedSensors) {
            log.debug("skipping connecting sensors for " + entity() + " " +
                    "in driver-tasks postStartCustom because already connected (e.g. restarting)");
        } else {
            log.debug("connecting sensors for " + entity() + " in driver-tasks postStartCustom because already connected (e.g. restarting)");
            entity().connectSensors();
        }
        entity().waitForServiceUp();
        entity().postStart();
    }

    /**
     * Default restart implementation for an entity.
     * <p/>
     * Stops processes if possible, then starts the entity again.
     */
    @Override
    public void restart(ConfigBag parameters) {
        //TODO
    }

    @Override
    public void stop(ConfigBag parameters) {

        log.info("Stopping {} in {}", entity(), entity().getLocations());
        try {

            DynamicTasks.queue("pre-stop", new Callable<String>() {
                public String call() {
                    if (entity().getAttribute(SoftwareProcess.SERVICE_STATE_ACTUAL) == Lifecycle.STOPPED) {
                        log.debug("Skipping stop of entity " + entity() + " when already stopped");
                        return "Already stopped";
                    }
                    ServiceStateLogic.setExpectedState(entity(), Lifecycle.STOPPING);
                    entity().setAttribute(SoftwareProcess.SERVICE_UP, false);
                    preStopProcess();
                    return null;
                }
            });

            stopProcess();
            postStopProcess();
            entity().setAttribute(SoftwareProcess.SERVICE_UP, false);
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.STOPPED);
        } catch (Throwable t) {
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.ON_FIRE);
            log.error("Error error starting entity {}", entity());
            throw Exceptions.propagate(t);
        }
    }

    @Override
    public void suspend(ConfigBag parameters) {
        //TODO
    }

    protected void preStopProcess() {
        entity().preStop();
    }

    protected String stopProcess() {
        //TODO: This method was copied from SoftwareProcessDriverLifecycleEffectorTasks. It should
        //be moved to any super class
        String result;

        SoftwareProcess.ChildStartableMode mode = getChildrenStartableModeEffective();
        TaskAdaptable<?> children = null;
        Exception childException = null;

        if (!mode.isDisabled) {
            children = StartableMethods.stoppingChildren(entity());

            if (mode.isBackground || !mode.isLate) Entities.submit(entity(), children);
            else {
                DynamicTasks.queue(children);
                try {
                    DynamicTasks.waitForLast();
                } catch (Exception e) {
                    childException = e;
                }
            }
        }

        if (entity().getDriver() != null) {
            entity().getDriver().stop();
            result = "Driver stop completed";
        } else {
            result = "No driver (nothing to do here)";
        }

        if (!mode.isDisabled && !mode.isBackground) {
            try {
                children.asTask().get();
            } catch (Exception e) {
                childException = e;
                log.debug("Error stopping children; continuing and will rethrow if no other errors", e);
            }
        }

        if (childException != null)
            throw new IllegalStateException(result + "; but error stopping child: " + childException, childException);

        return result;
    }

    protected void postStopProcess() {
        entity().postStop();
    }

}

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
package org.apache.brooklyn.test.framework;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.TaskAdaptable;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.trait.StartableMethods;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.exceptions.Exceptions;

/**
 * This implementation will start all child entities in parallel.
 * 
 * @author Chris Burke
 */
public class ParallelTestCaseImpl extends TargetableTestComponentImpl implements ParallelTestCase {

    private static final Logger logger = LoggerFactory.getLogger(ParallelTestCaseImpl.class);

    /**
     * {@inheritDoc}
     */
    public void start(final Collection<? extends Location> locations) {
        // Let everyone know we're starting up (so that the GUI shows the correct icon).
        sensors().set(Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STARTING);
        try {
            // Get an unsubmitted task for starting all the children of this entity in parallel,
            // at the same location as this entity.
            final TaskAdaptable<?> taskAdaptable = StartableMethods.startingChildren(this, locations);
            logger.trace("{}, TaskAdaptable: {}", this, taskAdaptable);

            // Submit the task to the ExecutionManager so that they actually get started
            // and then wait until all the parallel child entities have completed.
            submitTaskAndWait(taskAdaptable);

            // Let everyone know we've started up successfully (changes the icon in the GUI).
            logger.debug("Tasks successfully run. Update state of {} to RUNNING.", this);
            setServiceState(true, Lifecycle.RUNNING);
        } catch (Throwable t) {
            logger.debug("Tasks NOT successfully run. Update state of {} to ON_FIRE.", this);
            setServiceState(false, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void stop() {
        // Let everyone know we're stopping (so that the GUI shows the correct icon).
        sensors().set(Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPING);

        // Get an unsubmitted task for stopping all the children of this entity in parallel.
        final TaskAdaptable<?> taskAdaptable = StartableMethods.stoppingChildren(this);
        logger.trace("{}, TaskAdaptable: {}", this, taskAdaptable);
        try {
            // Submit the task to the ExecutionManager so that they actually get stopped
            // and then wait until all the parallel entities have completed.
            submitTaskAndWait(taskAdaptable);
            
            // Let everyone know we've stopped successfully (changes the icon in the GUI).
            logger.debug("Tasks successfully run. Update state of {} to STOPPED.", this);
            setServiceState(false, Lifecycle.STOPPED);
        } catch (Throwable t) {
            logger.debug("Tasks NOT successfully run. Update state of {} to ON_FIRE.", this);
            setServiceState(false, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void restart() {
        // Let everyone know we're restarting (so that the GUI shows the correct icon).
        setServiceState(false, Lifecycle.STARTING);

        // Get an unsubmitted task for restarting all the children of this entity in parallel.
        final TaskAdaptable<?> taskAdaptable = StartableMethods.restartingChildren(this);
        logger.trace("{}, TaskAdaptable: {}", this, taskAdaptable);

        try {
            // Submit the task to the ExecutionManager so that they actually get stopped
            // and then wait until all the parallel entities have completed.
            submitTaskAndWait(taskAdaptable);

            // Let everyone know we've started up successfully (changes the icon in the GUI).
            logger.debug("Tasks successfully run. Update state of {} to RUNNING.", this);
            setServiceState(true, Lifecycle.RUNNING);
        } catch (Throwable t) {
            logger.debug("Tasks NOT successfully run. Update state of {} to ON_FIRE.", this);
            setServiceState(false, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
    }

    /**
     * Submits the task to the ExecutionManager and then waits until the task has completed.
     * 
     * @param taskAdaptable the TaskAdaptable to submit for execution.
     */
    private void submitTaskAndWait(final TaskAdaptable<?> taskAdaptable) {
        // Submit the task to the ExecutionManager.
        DynamicTasks.queue(taskAdaptable);
        // Block until the task has completed. This will also throw if anything went wrong.
        DynamicTasks.waitForLast();
    }

    /**
     * Sets the state of the Entity. Useful so that the GUI shows the correct icon.
     * 
     * @param serviceUpState Whether or not the entity is up.
     * @param serviceStateActual The actual state of the entity.
     */
    private void setServiceState(final boolean serviceUpState, final Lifecycle serviceStateActual) {
        sensors().set(Attributes.SERVICE_UP, serviceUpState);
        sensors().set(Attributes.SERVICE_STATE_ACTUAL, serviceStateActual);
    }
}

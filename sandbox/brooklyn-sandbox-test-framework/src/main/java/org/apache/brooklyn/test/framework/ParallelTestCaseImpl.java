package org.apache.brooklyn.test.framework;

import java.util.Collection;
import java.util.concurrent.ExecutionException;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.mgmt.TaskAdaptable;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.trait.StartableMethods;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This implementation will start all child entities in parallel.
 * 
 * @author Chris Burke
 */
public class ParallelTestCaseImpl extends AbstractEntity implements ParallelTestCase {

    private static final Logger logger = LoggerFactory.getLogger(ParallelTestCaseImpl.class);

    /**
     * {@inheritDoc}
     */
    public void start(Collection<? extends Location> locations) {
        // Let everyone know we're starting up (so that the GUI shows the correct icon).
        sensors().set(Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STARTING);
        try {
            // Get an unsubmitted task for starting all the children of this entity in parallel,
            // at the same location as this entity.
            final TaskAdaptable<?> taskAdaptable = StartableMethods.startingChildren(this);
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
     * @throws ExecutionException if the task threw an exception
     * @throws InterruptedException if the current thread was interrupted while waiting
     */
    private void submitTaskAndWait(final TaskAdaptable<?> taskAdaptable)
            throws InterruptedException, ExecutionException {
        logger.debug("{}, Submitting taskAdaptable: {}", this, taskAdaptable);
        // Submit the task to the ExecutionManager.
        final Task<?> task = DynamicTasks.submit(taskAdaptable, this);

        // Block until the task has completed.
        logger.debug("{}, Blocking until task complete.", this);
        task.blockUntilEnded();
        logger.debug("{}, Task complete.", this);

        // Get the result of the task. We don't really care about the
        // actual result but this will throw an exception if the task failed.
        task.get();
    }

    private void setServiceState(final boolean serviceUpState, final Lifecycle serviceStateActual) {
        sensors().set(SERVICE_UP, serviceUpState);
        sensors().set(Attributes.SERVICE_STATE_ACTUAL, serviceStateActual);
    }
}

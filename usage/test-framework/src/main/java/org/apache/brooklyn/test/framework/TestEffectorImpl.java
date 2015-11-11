package org.apache.brooklyn.test.framework;

import com.google.common.collect.Lists;
import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.mgmt.internal.EffectorUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

/**
 *
 */
public class TestEffectorImpl extends AbstractTest implements TestEffector {
    private static final Logger LOG = LoggerFactory.getLogger(TestEffectorImpl.class);


    /**
     * {@inheritDoc}
     */
    public void start(Collection<? extends Location> locations) {
        if (!getChildren().isEmpty()) {
            throw new RuntimeException(String.format("The entity [%s] cannot have child entities", getClass().getName()));
        }
        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);
        final Entity targetEntity = resolveTarget();
        final String effectorName = getConfig(EFFECTOR_NAME);
        final Map<String, ?> effectorParams = getConfig(EFFECTOR_PARAMS);
        final Duration timeout = getConfig(TIMEOUT);
        try {
            Maybe<Effector<?>> effector = EffectorUtils.findEffectorDeclared(targetEntity, effectorName);
            if (effector.isAbsentOrNull()) {
                throw new AssertionError(String.format("No effector with name [%s]", effectorName));
            }
            final Task<?> effectorResult;
            if (effectorParams == null || effectorParams.isEmpty()) {
                effectorResult = Entities.invokeEffector(this, targetEntity, effector.get());
            } else {
                effectorResult = Entities.invokeEffector(this, targetEntity, effector.get(), effectorParams);
            }
            //Add result of effector to sensor
            sensors().set(EFFECTOR_RESULT, effectorResult.get(timeout));
            sensors().set(SERVICE_UP, true);
            ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        } catch (Throwable t) {
            sensors().set(SERVICE_UP, false);
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void stop() {
        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPING);
        sensors().set(SERVICE_UP, false);
    }

    /**
     * {@inheritDoc}
     */
    public void restart() {
        final Collection<Location> locations = Lists.newArrayList(getLocations());
        stop();
        start(locations);
    }
}

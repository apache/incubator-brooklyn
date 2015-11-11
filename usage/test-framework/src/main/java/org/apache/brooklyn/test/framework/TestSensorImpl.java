package org.apache.brooklyn.test.framework;

import com.google.api.client.util.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

import static org.apache.brooklyn.core.entity.EntityAsserts.assertAttributeEventually;

/**
 * {@inheritDoc}
 */
public class TestSensorImpl extends AbstractTest implements TestSensor {

    private static final Logger LOG = LoggerFactory.getLogger(TestSensorImpl.class);

    /**
     * {@inheritDoc}
     */
    public void start(Collection<? extends Location> locations) {
        if (!getChildren().isEmpty()) {
            throw new RuntimeException(String.format("The entity [%s] cannot have child entities", getClass().getName()));
        }
        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);
        final Entity target = resolveTarget();
        final String sensor = getConfig(SENSOR_NAME);
        final Duration timeout = getConfig(TIMEOUT);
        final Map assertions = getConfig(ASSERTIONS);
        try {
            checkAssertions(target, Sensors.newSensor(Object.class, sensor), ImmutableMap.of("timeout", timeout),
                    assertions);
            sensors().set(SERVICE_UP, true);
            ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        } catch (Throwable t) {
            LOG.info("Sensor [{}] test failed", sensor);
            sensors().set(SERVICE_UP, false);
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
    }

    /**
     * Tests sensor values match assertions
     *
     * @param target     The {@link Entity} that has the sensor under test
     * @param sensor     The sensor to test
     * @param flags      Passed to {@link org.apache.brooklyn.core.entity.EntityAsserts#assertAttributeEventually(Map, Entity, AttributeSensor, Predicate)}
     * @param assertions The map of assertions
     */
    private void checkAssertions(final Entity target, final AttributeSensor<Object> sensor, final Map<?, ?> flags, final Map<?, ?> assertions) {
        for (final Map.Entry<?, ?> entry : assertions.entrySet()) {
            if (Objects.equal(entry.getKey(), "equals")) {
                assertAttributeEventually(flags, target, sensor, isEqualTo(entry.getValue()));
            } else if (Objects.equal(entry.getKey(), "regex")) {
                assertAttributeEventually(flags, target, sensor, regexMatches(entry.getValue()));
            } else if (Objects.equal(entry.getKey(), "isNull")) {
                assertAttributeEventually(flags, target, sensor, isNull(entry.getValue()));
            }
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

    /**
     * Predicate to check the equality of object
     *
     * @param value
     * @return The created {@link Predicate}
     */
    private Predicate<Object> isEqualTo(final Object value) {
        return new Predicate<Object>() {
            public boolean apply(final Object input) {
                return (input != null) && Objects.equal(TypeCoercions.coerce(value, input.getClass()), input);
            }
        };
    }

    /**
     * Predicate to check if a sensor matches a regex pattern
     *
     * @param patternValue
     * @return
     */
    private Predicate<Object> regexMatches(final Object patternValue) {
        final Pattern pattern = Pattern.compile(TypeCoercions.coerce(patternValue, String.class));
        return new Predicate<Object>() {
            public boolean apply(final Object input) {
                return (input != null) && pattern.matcher(input.toString()).matches();
            }
        };
    }

    /**
     * Predicate to check if a sensor value is null
     *
     * @param isNullValue
     * @return
     */
    private Predicate<Object> isNull(final Object isNullValue) {
        return new Predicate<Object>() {
            public boolean apply(final Object input) {
                return (input == null) == TypeCoercions.coerce(isNullValue, Boolean.class);
            }
        };
    }

}

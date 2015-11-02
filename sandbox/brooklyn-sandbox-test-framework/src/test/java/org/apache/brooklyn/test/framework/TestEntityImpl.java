package org.apache.brooklyn.test.framework;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.entity.AbstractEntity;

import java.util.Collection;

/**
 * @author m4rkmckenna on 27/10/2015.
 */
public class TestEntityImpl extends AbstractEntity implements TestEntity {
    @Override
    public void start(final Collection<? extends Location> locations) {
    }

    @Override
    public void stop() {

    }

    @Override
    public void restart() {
    }

    @Override
    public void simpleEffector() {
        sensors().set(SIMPLE_EFFECTOR_INVOKED, Boolean.TRUE);
    }

    @Override
    public TestPojo complexEffector(@EffectorParam(name = "stringValue") final String stringValue, @EffectorParam(name = "booleanValue") final Boolean booleanValue, @EffectorParam(name = "longValue") final Long longValue) {
        sensors().set(COMPLEX_EFFECTOR_INVOKED, Boolean.TRUE);
        sensors().set(COMPLEX_EFFECTOR_STRING, stringValue);
        sensors().set(COMPLEX_EFFECTOR_BOOLEAN, booleanValue);
        sensors().set(COMPLEX_EFFECTOR_LONG, longValue);
        return new TestPojo(stringValue, booleanValue, longValue);
    }
}

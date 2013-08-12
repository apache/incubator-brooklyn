package brooklyn.entity.trait;

import java.util.Collection;

import brooklyn.location.Location;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.exceptions.Exceptions;

public class FailingEntityImpl extends TestEntityImpl implements FailingEntity {

    public FailingEntityImpl() {
    }
    
    @Override
    public void start(Collection<? extends Location> locs) {
        getConfig(LISTENER).onEvent(this, "start", new Object[] {locs});
        if (getConfig(FAIL_ON_START) || (getConfig(FAIL_ON_START_CONDITION) != null && getConfig(FAIL_ON_START_CONDITION).apply(this))) {
            throw newException("Simulating entity start failure for test");
        }
    }
    
    @Override
    public void stop() {
        getConfig(LISTENER).onEvent(this, "stop", new Object[0]);
        if (getConfig(FAIL_ON_STOP) || (getConfig(FAIL_ON_STOP_CONDITION) != null && getConfig(FAIL_ON_STOP_CONDITION).apply(this))) {
            throw newException("Simulating entity stop failure for test");
        }
    }
    
    private RuntimeException newException(String msg) {
        try {
            return getConfig(EXCEPTION_CLAZZ).getConstructor(String.class).newInstance("Simulating entity stop failure for test");
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
}

package brooklyn.entity.database.rubyrep;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Functions;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;

public class RubyRepNodeImpl extends SoftwareProcessImpl implements RubyRepNode {
    public RubyRepNodeImpl(Entity owner) {
        this(new HashMap(), owner);
    }

    public RubyRepNodeImpl(Map flags, Entity owner) {
        super(flags, owner);
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        FunctionFeed.builder()
                .entity(this)
                .poll(new FunctionPollConfig<Object, Boolean>(SERVICE_UP)
                        .period(500, TimeUnit.MILLISECONDS)
                        .callable(new Callable<Object>() {
                            @Override
                            public Object call() throws Exception {
                                return getDriver().isRunning();
                            }
                        })
                        .onError(Functions.constant(Boolean.FALSE)))
                .build();
    }

    @Override
    public Class getDriverInterface() {
        return RubyRepDriver.class;
    }
}

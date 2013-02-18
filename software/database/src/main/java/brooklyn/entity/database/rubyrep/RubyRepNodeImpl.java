package brooklyn.entity.database.rubyrep;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import com.google.common.base.Functions;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class RubyRepNodeImpl extends SoftwareProcessImpl implements RubyRepNode {

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

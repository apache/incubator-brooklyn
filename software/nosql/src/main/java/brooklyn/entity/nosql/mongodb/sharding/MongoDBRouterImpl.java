package brooklyn.entity.nosql.mongodb.sharding;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.nosql.mongodb.MongoDBClientSupport;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;

import com.google.common.base.Functions;

public class MongoDBRouterImpl extends SoftwareProcessImpl implements MongoDBRouter {
    
    private volatile FunctionFeed serviceUp;

    @Override
    public Class<?> getDriverInterface() {
        return MongoDBRouterDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        serviceUp = FunctionFeed.builder()
                .entity(this)
                .poll(new FunctionPollConfig<Boolean, Boolean>(RUNNING)
                        .period(5, TimeUnit.SECONDS)
                        .callable(new Callable<Boolean>() {
                            @Override
                            public Boolean call() throws Exception {
                                MongoDBClientSupport clientSupport = MongoDBClientSupport.forServer(MongoDBRouterImpl.this);
                                return clientSupport.ping();
                            }
                        })
                        .onException(Functions.<Boolean>constant(false)))
                .poll(new FunctionPollConfig<Boolean, Boolean>(SERVICE_UP)
                        .period(5, TimeUnit.SECONDS)
                        .callable(new Callable<Boolean>() {
                            @Override
                            public Boolean call() throws Exception {
                                // TODO: This is the same as in AbstractMongoDBSshDriver.isRunning. 
                                // This feels like the right place. But feels like can be more consistent with different 
                                // MongoDB types using the FunctionFeed.
                                MongoDBClientSupport clientSupport = MongoDBClientSupport.forServer(MongoDBRouterImpl.this);
                                return clientSupport.ping() && MongoDBRouterImpl.this.getAttribute(SHARD_COUNT) > 0;
                            }
                        })
                        .onException(Functions.<Boolean>constant(false)))
                .poll(new FunctionPollConfig<Integer, Integer>(SHARD_COUNT)
                        .period(5, TimeUnit.SECONDS)
                        .callable(new Callable<Integer>() {
                            public Integer call() throws Exception {
                                MongoDBClientSupport clientSupport = MongoDBClientSupport.forServer(MongoDBRouterImpl.this);
                                return (int) clientSupport.getShardCount();
                            }    
                        })
                        .onException(Functions.<Integer>constant(-1)))
                .build();
    }
    
    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        serviceUp.stop();
    }
}

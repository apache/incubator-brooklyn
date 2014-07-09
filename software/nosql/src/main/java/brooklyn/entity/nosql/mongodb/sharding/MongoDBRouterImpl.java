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
package brooklyn.entity.nosql.mongodb.sharding;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.nosql.mongodb.MongoDBClientSupport;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;

import com.google.common.base.Functions;

public class MongoDBRouterImpl extends SoftwareProcessImpl implements MongoDBRouter {
    
    private volatile FunctionFeed functionFeed;

    @Override
    public Class<?> getDriverInterface() {
        return MongoDBRouterDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        functionFeed = FunctionFeed.builder()
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
        if (functionFeed != null) functionFeed.stop();
    }
}

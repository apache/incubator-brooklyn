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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.nosql.mongodb.AbstractMongoDBSshDriver;
import brooklyn.location.basic.SshMachineLocation;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

public class MongoDBRouterSshDriver extends AbstractMongoDBSshDriver implements MongoDBRouterDriver {
    
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBRouterSshDriver.class);

    public MongoDBRouterSshDriver(MongoDBRouterImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }
    
    @Override
    public void launch() {
        String configdb = Joiner.on(",").join(getEntity().getConfig(MongoDBRouter.CONFIG_SERVERS));
        ImmutableList.Builder<String> argsBuilder = getArgsBuilderWithDefaults(MongoDBRouterImpl.class.cast(getEntity()))
                .add("--configdb", configdb);
        
        String args = Joiner.on(" ").join(argsBuilder.build());
        String command = String.format("%s/bin/mongos %s > out.log 2> err.log < /dev/null", getExpandedInstallDir(), args);
        LOG.info(command);
        newScript(LAUNCHING)
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(command).execute();
    }

}

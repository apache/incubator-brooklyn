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
package brooklyn.demo;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.nosql.mongodb.MongoDBReplicaSet;
import brooklyn.entity.nosql.mongodb.MongoDBServer;
import brooklyn.entity.proxying.EntitySpec;

public class SimpleMongoDBReplicaSet extends ApplicationBuilder {

    protected void doBuild() {
        addChild(EntitySpec.create(MongoDBReplicaSet.class)
            .configure("name", "Simple MongoDB replica set")
            .configure("initialSize", 3)
            .configure("replicaSetName", "simple-replica-set")
            .configure("memberSpec", EntitySpec.create(MongoDBServer.class)
                    .configure("mongodbConfTemplateUrl", "classpath:///mongodb.conf")
                    .configure("enableRestInterface", true)
                    .configure("port", "27017+")));
    }

}

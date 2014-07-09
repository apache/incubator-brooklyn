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
package brooklyn.entity.nosql.couchbase;

import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(CouchbaseLoadGeneratorImpl.class)
public interface CouchbaseLoadGenerator extends SoftwareProcess {
    public static final MethodEffector<Void> PILLOWFIGHT = new MethodEffector<Void>(CouchbaseLoadGenerator.class, "pillowfight");
    
    /**
     * NOTE: EffectorParam names match the documentation at http://www.couchbase.com/autodocs/couchbase-c-client-2.1.1/cbc.1.html
     */
    @Effector(description = "Runs cbc pillowfight")
    public void pillowfight(
            @EffectorParam(name = "host", defaultValue="127.0.0.1:8091", 
                description = "list of hosts to connect to") String targetHostnameAndPort,
            @EffectorParam(name = "bucket", defaultValue = "default",
                description = "bucket to use") String bucket,
            @EffectorParam(name = "username", description = "username used for authentication to the cluster") String username, 
            @EffectorParam(name = "password", description = "password used for authentication to the cluster") String password,
            @EffectorParam(name = "iterations", defaultValue = "1000",
                description = "number of iterations to run") Integer iterations,
            @EffectorParam(name = "num-items", defaultValue = "1000",
                description = "number of items to operate on") Integer numItems,
            @EffectorParam(name = "key-prefix", description = "prefix for keys") String keyPrefix,
            @EffectorParam(name = "num-threads", defaultValue = "1",
                description = "number of threads to use") Integer numThreads,
            @EffectorParam(name = "num-instances", defaultValue = "1",
                description = "number of connection instances to put into the shared connection pool") Integer numInstances,
            @EffectorParam(name = "random-seed", defaultValue = "0", description = "random seed") Integer randomSeed,
            @EffectorParam(name = "ratio", defaultValue = "33",
                description = "Specify SET/GET command ratio (default: 33, i.e. 33% SETs and 67% GETs)") Integer ratio,
            @EffectorParam(name = "min-size", defaultValue = "50",
                description = "minimum size of payload, i.e. document body") Integer minSize,
            @EffectorParam(name = "max-size", defaultValue = "5120",
                description = "maximum size of payload, i.e. document body") Integer maxSize
    );
}

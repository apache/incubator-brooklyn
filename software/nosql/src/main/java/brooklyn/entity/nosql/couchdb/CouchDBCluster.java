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
package brooklyn.entity.nosql.couchdb;

import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

/**
 * A cluster of {@link CouchDBNode}s based on {@link DynamicCluster} which can be resized by a policy if required.
 *
 * TODO add sensors with aggregated CouchDB statistics from cluster
 */
@ImplementedBy(CouchDBClusterImpl.class)
public interface CouchDBCluster extends DynamicCluster {

    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class, "couchdb.cluster.name", "Name of the CouchDB cluster", "BrooklynCluster");

    AttributeSensor<String> HOSTNAME = Sensors.newStringSensor("couchdb.cluster.hostname", "Hostname to connect to cluster with");

    AttributeSensor<Integer> HTTP_PORT = Sensors.newIntegerSensor("couchdb.cluster.http.port", "CouchDB HTTP port to connect to cluster with");

    /**
     * The name of the cluster.
     */
    String getClusterName();

}

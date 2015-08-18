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
package org.apache.brooklyn.entity.nosql.elasticsearch;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.proxying.ImplementedBy;
import org.apache.brooklyn.core.util.flags.SetFromFlag;

import brooklyn.entity.group.DynamicCluster;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;

/**
 * A cluster of {@link ElasticSearchNode}s based on {@link DynamicCluster} which can be resized by a policy if required.
 */
@Catalog(name="Elastic Search Cluster", description="Elasticsearch is an open-source search server based on Lucene. "
        + "It provides a distributed, multitenant-capable full-text search engine with a RESTful web interface and "
        + "schema-free JSON documents.")
@ImplementedBy(ElasticSearchClusterImpl.class)
public interface ElasticSearchCluster extends DynamicCluster {
    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class, 
            "elasticsearch.cluster.name", "Name of the ElasticSearch cluster", "BrooklynCluster");
    
    String getClusterName();
}

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
package brooklyn.entity.webapp;

import brooklyn.entity.group.DynamicFabric;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;

/**
 * DynamicWebAppFabric provide a fabric of clusters, aggregating the entity attributes.  Currently totals and averages:
 * <ul>
 *   <li>Entity request counts</li>
 *   <li>Entity error counts</li>
 *   <li>Requests per second</li>
 *   <li>Entity processing time</li>
 * </ul>
 */
@ImplementedBy(DynamicWebAppFabricImpl.class)
public interface DynamicWebAppFabric extends DynamicFabric, WebAppService {

    public static final AttributeSensor<Double> REQUEST_COUNT_PER_NODE = new BasicAttributeSensor<Double>(
            Double.class, "webapp.reqs.total.perNode", "Fabric entity request average");

    public static final AttributeSensor<Integer> ERROR_COUNT_PER_NODE = new BasicAttributeSensor<Integer>(
            Integer.class, "webapp.reqs.errors.perNode", "Fabric entity request error average");

    public static final AttributeSensor<Double> REQUESTS_PER_SECOND_LAST_PER_NODE = DynamicWebAppCluster.REQUESTS_PER_SECOND_LAST_PER_NODE;
    public static final AttributeSensor<Double> REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE = DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE;
    public static final AttributeSensor<Integer> TOTAL_PROCESSING_TIME_PER_NODE = DynamicWebAppCluster.TOTAL_PROCESSING_TIME_PER_NODE;

}

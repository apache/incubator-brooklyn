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
package org.apache.brooklyn.entity.messaging.storm;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

@Catalog(name="Storm Deployment", description="A Storm cluster. Apache Storm is a distributed realtime computation system. "
        + "Storm makes it easy to reliably process unbounded streams of data, doing for realtime processing "
        + "what Hadoop did for batch processing")
@ImplementedBy(StormDeploymentImpl.class)
public interface StormDeployment extends Entity, Startable {

    @SetFromFlag("supervisors.count")
    ConfigKey<Integer> SUPERVISORS_COUNT = ConfigKeys.newConfigKey("storm.supervisors.count", "Number of supervisor nodes", 3);

    @SetFromFlag("zookeepers.count")
    ConfigKey<Integer> ZOOKEEPERS_COUNT = ConfigKeys.newConfigKey("storm.zookeepers.count", "Number of zookeeper nodes", 1);
    
}

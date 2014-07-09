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
package brooklyn.entity.group;

import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(DynamicRegionsFabricImpl.class)
public interface DynamicRegionsFabric extends DynamicFabric {

    MethodEffector<String> ADD_REGION = new MethodEffector<String>(DynamicRegionsFabric.class, "addRegion");
    MethodEffector<String> REMOVE_REGION = new MethodEffector<String>(DynamicRegionsFabric.class, "removeRegion");

    @Effector(description="Extends the fabric with a new instance of the fabric's underlying blueprint in a new region, "+
            "returning the id of the new entity")
    public String addRegion(
            @EffectorParam(name="location", description="Location spec string "
                    + "(e.g. aws-ec2:us-west-1)") String location);

    @Effector(description="Stops and removes a region")
    public void removeRegion(
            @EffectorParam(name="id", description="ID of the child entity to stop and remove") String id);
    
}

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
package brooklyn.entity.trait;


import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.MethodEffector;

/**
 * Defines an entity group that can be re-sized dynamically.
 * <p/>
 * By invoking the {@link #resize(Integer)} effector, the number of child nodes
 * can be reduced (by shutting down some of them) or increased (by provisioning new entities.)
 */
public interface Resizable {

    MethodEffector<Integer> RESIZE = new MethodEffector<Integer>(Resizable.class, "resize");

    /**
     * Grow or shrink this entity to the desired size.
     *
     * @param desiredSize the new size of the entity group.
     * @return the new size of the group.
     */
    @Effector(description="Changes the size of the entity (e.g. the number of nodes in a cluster)")
    Integer resize(@EffectorParam(name="desiredSize", description="The new size of the cluster") Integer desiredSize);

    /**
     * @return the current size of the group.
     */
    Integer getCurrentSize();
}


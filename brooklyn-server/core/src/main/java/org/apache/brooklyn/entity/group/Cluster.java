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
package org.apache.brooklyn.entity.group;

import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.entity.trait.Resizable;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * Intended to represent a group of homogeneous entities in a single location.
 */
public interface Cluster extends Group, Startable, Resizable {
    
    @SetFromFlag("initialSize")
    BasicConfigKey<Integer> INITIAL_SIZE = new BasicConfigKey<Integer>(
            Integer.class, "cluster.initial.size", "Initial cluster size", 1);
}

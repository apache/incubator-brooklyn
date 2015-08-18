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
package org.apache.brooklyn.location.cloud.names;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.util.config.ConfigBag;
import org.apache.brooklyn.location.cloud.CloudLocationConfig;

/**
 * Interface used to construct names for individual cloud machines and for groups of machines.
 * <p>
 * Implementations <b>must</b> provide a constructor which takes a single argument,
 * being the {@link ConfigBag} for the context where the machine is being created
 * (usually a {@link Location}).
 * <p>
 * With that bag, the config key {@link CloudLocationConfig#CALLER_CONTEXT}
 * typically contains the {@link Entity} for which the machine is being created.   
 */
public interface CloudMachineNamer {

    /**
     * Generate a name for a new machine, based on context.
     * <p>
     * The name should normally be unique, as a context might produce multiple machines,
     * for example basing it partially on information from the context but also including some random salt.
     */
    public String generateNewMachineUniqueName(ConfigBag setup);
    /**
     * Generate a name stem for a group of machines, based on context.
     * <p>
     * The name does not need to be unique, as uniqueness will be applied by {@link #generateNewMachineUniqueNameFromGroupId(String)}.
     */
    public String generateNewGroupId(ConfigBag setup);
    
    /**
     * Generate a unique name from the given name stem.
     * <p>
     * The name stem is normally based on context information so the usual
     * function of this method is to apply a suffix which helps to uniquely distinguish between machines
     * in cases where the same name stem ({@link #generateNewGroupId()}) is used for multiple machines.
     */
    public String generateNewMachineUniqueNameFromGroupId(ConfigBag setup, String groupId);
    
}

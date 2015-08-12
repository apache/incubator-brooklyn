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
package org.apache.brooklyn.management.ha;

/** Specifies the HA mode that a mgmt node should run in */
public enum HighAvailabilityMode {
    /** 
     * Means HA mode should not be operational.
     * <p>
     * When specified for the initial HA mode, this simply turns off HA.
     * <p>
     * However if being used to {@link HighAvailabilityManager#changeMode(HighAvailabilityMode)},
     * this will cause the node to transition to a {@link ManagementNodeState#FAILED} state.
     * Switching to a "master-but-not-part-of-HA" state is not currently supported, as this would be
     * problematic if another node (which was part of HA) then tries to become master,
     * and the more common use of this at runtime is to disable a node from being part of the HA plane. 
     */
    DISABLED,
    
    /**
     * Means auto-detect whether to be master or standby; if there is already a master then start as standby, 
     * otherwise start as master.
     */
    AUTO,
    
    /**
     * Means node must be lukewarm standby; if there is not already a master then fail fast on startup.
     * See {@link ManagementNodeState#STANDBY}. 
     */
    STANDBY,
    
    /**
     * Means node must be hot standby; if there is not already a master then fail fast on startup.
     * See {@link ManagementNodeState#HOT_STANDBY}. 
     */
    HOT_STANDBY,
    
    /**
     * Means node must be hot backup; do not attempt to become master (but it <i>can</i> start without a master).
     * See {@link ManagementNodeState#HOT_BACKUP}. 
     */
    HOT_BACKUP,
    
    /**
     * Means node must be master; if there is already a master then fail fast on startup.
     * See {@link ManagementNodeState#MASTER}.
     */
    // TODO when multi-master supported we will of course not fail fast on startup when there is already a master;
    // instead the responsibility for master entities will be divided among masters
    MASTER;
}

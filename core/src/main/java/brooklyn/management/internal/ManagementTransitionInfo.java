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
package brooklyn.management.internal;

import brooklyn.management.ManagementContext;

public class ManagementTransitionInfo {

    final ManagementContext mgmtContext;
    
    final ManagementTransitionMode mode;
    
    /** true if this transition is an entity whose mastering is migrating from one node to another;
     * false if the brooklyn mgmt plane is just starting managing of this entity for the very first time  
     */

    public enum ManagementTransitionMode {
        /** Entity is being created fresh, for the first time, or stopping permanently */ 
        NORMAL,
        /** Entity management is moving from one location to another (ie stopping at one location / starting at another) */
        MIGRATORY, 
        /** Entity is being created, from a serialized/specified state */
        REBIND
    }
    
    public ManagementTransitionInfo(ManagementContext mgmtContext, ManagementTransitionMode mode) {
        this.mgmtContext = mgmtContext;
        this.mode = mode;
    }
    
    
    public ManagementContext getManagementContext() {
        return mgmtContext;
    }

    public ManagementTransitionMode getMode() {
        return mode;
    }
}

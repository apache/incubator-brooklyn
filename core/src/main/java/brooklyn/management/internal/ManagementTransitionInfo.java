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
        /** Item is being created fresh, for the first time */ 
        CREATING(false, false),
        /** Item is being destroyed / stopping permanently */ 
        DESTROYING(false, false),
        
        /** Item is being mirrored (or refreshed) here from a serialized/specified state */
        REBINDING_READONLY(true, true),
        /** Item management is stopping here, going elsewhere */
        REBINDING_NO_LONGER_PRIMARY(false, true), 
        /** Item management is starting here, having previously been running elsewhere */
        REBINDING_BECOMING_PRIMARY(true, false),
        /** Item was being mirrored but has now been destroyed  */
        REBINDING_DESTROYED(true, true);
        
        private boolean wasReadOnly;
        private boolean isReadOnly;

        ManagementTransitionMode(boolean wasReadOnly, boolean isReadOnly) {
            this.wasReadOnly = wasReadOnly;
            this.isReadOnly = isReadOnly;
        }
        
        public boolean wasReadOnly() { return wasReadOnly; }
        public boolean isReadOnly() { return isReadOnly; }
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

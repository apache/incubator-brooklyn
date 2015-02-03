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

import com.google.common.base.Preconditions;

public class ManagementTransitionInfo {

    final ManagementContext mgmtContext;
    final ManagementTransitionMode mode;
    
    public enum BrooklynObjectManagementMode {
        /** item does not exist, not in memory, nor persisted (e.g. creating for first time, or finally destroying) */
        NONEXISTENT, 
        /** item exists or existed elsewhere, i.e. there is persisted state, but is not loaded here */
        UNMANAGED_PERSISTED, 
        
        @Deprecated /** @deprecated marking places where we aren't sure */
        /** either nonexistent or persisted by unmanaged */
        ITEM_UNKNOWN, 
        
        /** item is loaded but read-only (ie not actively managed here) */
        LOADED_READ_ONLY, 
        /** item is actively managed here */
        MANAGED_PRIMARY 
    }
    
    public static class ManagementTransitionMode {

        // XXX
//-        CREATING(false, false, false),
//-        DESTROYING(false, false, false),
//-        REBINDING_READONLY(true, true, true),
//-        REBINDING_NO_LONGER_PRIMARY(false, true, true), 
//-        REBINDING_BECOMING_PRIMARY(true, false, true),
//-        REBINDING_DESTROYED(true, true, true),
//-        REBINDING_CREATING(false, false, true);
//-        
//-        private final boolean wasReadOnly;
//-        private final boolean isReadOnly;
//-        private final boolean isRebinding;

//        /** Item is being created fresh, for the first time */ 
//        CREATING(NONEXISTENT, MANAGED_PRIMARY),
//        /** Item is being destroyed / stopping permanently */ 
//        DESTROYING(MANAGED_PRIMARY, NONEXISTENT),
//        
//        /** Item is being mirrored (refreshed or created) here from a serialized/specified state */
//        REBINDING_READONLY(LOADED_READ_ONLY, LOADED_READ_ONLY),
//        /** Item management is stopping here, going elsewhere */
//        REBINDING_NO_LONGER_PRIMARY(MANAGED_PRIMARY, LOADED_READ_ONLY), 
//        /** Item management is starting here, having previously been running elsewhere */
//        REBINDING_BECOMING_PRIMARY(LOADED_READ_ONLY, MANAGED_PRIMARY),
//        /** Item has been managed here, and is being re-read for management again (e.g. applying a transform) */
//        REBINDING_ACTIVE_AGAIN(MANAGED_PRIMARY, MANAGED_PRIMARY),
//        /** Item was being mirrored but has now been destroyed  */
//        REBINDING_DESTROYED(LOADED_READ_ONLY, NONEXISTENT),
//
//        /** Item management is starting here, from persisted state */
//        REBINDING_CREATING(NodeManagementMode.UNMANAGED_PERSISTED, NodeManagementMode.MANAGED_PRIMARY);
        
//        private final static ManagementTransitionTargetMode NONE = ManagementTransitionTargetMode.NONE;
        private final BrooklynObjectManagementMode modeBefore, modeAfter;

        private ManagementTransitionMode(BrooklynObjectManagementMode modeBefore, BrooklynObjectManagementMode modeAfter) {
            this.modeBefore = modeBefore;
            this.modeAfter = modeAfter;
        }
        
        public static ManagementTransitionMode transitioning(BrooklynObjectManagementMode modeBefore, BrooklynObjectManagementMode modeAfter) {
            return new ManagementTransitionMode(Preconditions.checkNotNull(modeBefore, "modeBefore"), Preconditions.checkNotNull(modeAfter, "modeAfter"));
        }

        @Deprecated /** @deprecated marking places where we aren't sure */
        public static ManagementTransitionMode guessing(BrooklynObjectManagementMode modeBefore, BrooklynObjectManagementMode modeAfter) {
            return transitioning(modeBefore, modeAfter);
        }

        public BrooklynObjectManagementMode getModeBefore() {
            return modeBefore;
        }
        
        public BrooklynObjectManagementMode getModeAfter() {
            return modeAfter;
        }
        
        public boolean wasNotLoaded() {
            return getModeBefore()==BrooklynObjectManagementMode.NONEXISTENT || getModeBefore()==BrooklynObjectManagementMode.UNMANAGED_PERSISTED || getModeBefore()==BrooklynObjectManagementMode.ITEM_UNKNOWN;
        }

        public boolean isNoLongerLoaded() {
            return getModeAfter()==BrooklynObjectManagementMode.NONEXISTENT || getModeAfter()==BrooklynObjectManagementMode.UNMANAGED_PERSISTED || getModeAfter()==BrooklynObjectManagementMode.ITEM_UNKNOWN;
        }

        public boolean wasPrimary() {
            return getModeBefore()==BrooklynObjectManagementMode.MANAGED_PRIMARY;
        }
        
        public boolean isPrimary() {
            return getModeAfter()==BrooklynObjectManagementMode.MANAGED_PRIMARY;
        }

        public boolean wasReadOnly() {
            return getModeBefore()==BrooklynObjectManagementMode.LOADED_READ_ONLY;
        }
        
        public boolean isReadOnly() {
            return getModeAfter()==BrooklynObjectManagementMode.LOADED_READ_ONLY;
        }

        public boolean isDestroying() {
            return getModeAfter()==BrooklynObjectManagementMode.NONEXISTENT;
        }

        public boolean isCreating() {
            return getModeBefore()==BrooklynObjectManagementMode.NONEXISTENT;
        }
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

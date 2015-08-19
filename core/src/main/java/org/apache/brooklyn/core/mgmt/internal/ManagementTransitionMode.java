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
package org.apache.brooklyn.core.mgmt.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * Records details of a management transition, specifically the {@link BrooklynObjectManagementMode} before and after,
 * and allows easy checking of various aspects of that.
 * <p>
 * This helps make code readable and keep correct logic if we expand/change the management modes.
 */
public class ManagementTransitionMode {

    private static final Logger log = LoggerFactory.getLogger(ManagementTransitionMode.class);
    
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

    /** @return the mode this object was previously managed as */
    public BrooklynObjectManagementMode getModeBefore() {
        return modeBefore;
    }
    
    /** @return the mode this object is now being managed as */
    public BrooklynObjectManagementMode getModeAfter() {
        return modeAfter;
    }
    
    /** This management node was previously not loaded here, 
     * either it did not exist (and is just being created) or it was in persisted state but
     * not loaded at this node. */
    public boolean wasNotLoaded() {
        return getModeBefore()==BrooklynObjectManagementMode.NONEXISTENT || getModeBefore()==BrooklynObjectManagementMode.UNMANAGED_PERSISTED;
    }

    /** This management node is now not going to be loaded here, either it is being destroyed
     * (not known anywhere, not even persisted) or simply forgotten here */
    public boolean isNoLongerLoaded() {
        return getModeAfter()==BrooklynObjectManagementMode.NONEXISTENT || getModeAfter()==BrooklynObjectManagementMode.UNMANAGED_PERSISTED;
    }

    /** This management node was the master for the given object */
    public boolean wasPrimary() {
        return getModeBefore()==BrooklynObjectManagementMode.MANAGED_PRIMARY;
    }

    /** This management node is now the master for the given object */
    public boolean isPrimary() {
        return getModeAfter()==BrooklynObjectManagementMode.MANAGED_PRIMARY;
    }

    /** Object was previously loaded as read-only at this management node;
     * active management was occurring elsewhere (or not at all)
     */
    public boolean wasReadOnly() {
        return getModeBefore()==BrooklynObjectManagementMode.LOADED_READ_ONLY;
    }
    
    /** Object is now being loaded as read-only at this management node;
     * expect active management to be occurring elsewhere
     */
    public boolean isReadOnly() {
        return getModeAfter()==BrooklynObjectManagementMode.LOADED_READ_ONLY;
    }
    
    /** Object is being created:
     * previously did not exist (not even in persisted state);
     * implies that we are the active manager creating it,
     * i.e. {@link #getModeAfter()} should indicate {@link BrooklynObjectManagementMode#MANAGED_PRIMARY}.
     * (if we're read-only and the manager has just created it, 
     * {@link #getModeBefore()} should indicate {@link BrooklynObjectManagementMode#UNMANAGED_PERSISTED})
     */
    public boolean isCreating() {
        if (getModeBefore()!=BrooklynObjectManagementMode.NONEXISTENT)
            return false;
        
        if (getModeAfter()==BrooklynObjectManagementMode.LOADED_READ_ONLY) {
            log.warn("isCreating set on RO object; highly irregular!");
        }
        return true;
    }

    /** Object is being destroyed:
     * either destroyed elsewhere and we're catching up (in read-only mode),
     * or we've been the active manager and are destroying it */
    public boolean isDestroying() {
        return getModeAfter()==BrooklynObjectManagementMode.NONEXISTENT;
    }
    
    @Override
    public String toString() {
        return ManagementTransitionMode.class.getSimpleName()+"["+getModeBefore()+"->"+getModeAfter()+"]";
    }
}
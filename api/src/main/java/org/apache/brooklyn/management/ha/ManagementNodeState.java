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

import brooklyn.util.guava.Maybe;

public enum ManagementNodeState {
    /** Node is either coming online, or is in some kind of recovery/transitioning mode */
    INITIALIZING,
    
    /** Node is in "lukewarm standby" mode, where it is available to be promoted to master,
     * but does not have entities loaded and will require some effort to be promoted */
    STANDBY,
    /** Node is acting as read-only proxy available to be promoted to master on existing master failure */
    HOT_STANDBY,
    /** Node is acting as a read-only proxy but not making itself available for promotion to master */
    HOT_BACKUP,
    /** Node is running as primary/master, able to manage entities and create new ones */
    // the semantics are intended to support multi-master here; we could have multiple master nodes,
    // but we need to look up who is master for any given entity
    MASTER,

    /** Node has failed and requires maintenance attention */
    FAILED,
    /** Node has gone away; maintenance not possible */
    TERMINATED;

    /** Converts a {@link HighAvailabilityMode} to a {@link ManagementNodeState}, if possible */
    public static Maybe<ManagementNodeState> of(HighAvailabilityMode startMode) {
        switch (startMode) {
        case AUTO:
        case DISABLED:
            return Maybe.absent("Requested "+HighAvailabilityMode.class+" mode "+startMode+" cannot be converted to "+ManagementNodeState.class);
        case HOT_BACKUP:
            return Maybe.of(HOT_BACKUP);
        case HOT_STANDBY:
            return Maybe.of(HOT_STANDBY);
        case MASTER:
            return Maybe.of(MASTER);
        case STANDBY:
            return Maybe.of(STANDBY);
        }
        // above should be exhaustive
        return Maybe.absent("Requested "+HighAvailabilityMode.class+" mode "+startMode+" was not expected");
    }

    /** true for hot non-master modes, where we are proxying the data from the persistent store */
    public static boolean isHotProxy(ManagementNodeState state) {
        return state==HOT_BACKUP || state==HOT_STANDBY;
    }

    /** true for non-master modes which can be promoted to master */
    public static boolean isStandby(ManagementNodeState state) {
        return state==ManagementNodeState.STANDBY || state==ManagementNodeState.HOT_STANDBY;
    }
}

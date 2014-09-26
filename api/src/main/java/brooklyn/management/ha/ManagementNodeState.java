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
package brooklyn.management.ha;

public enum ManagementNodeState {
    /** @deprecated since 0.7.0 synonym for maintenance (plus, it should have been UK english!) */
    UNINITIALISED,
    /** node is either coming online, or is in some kind of recovery/transitioning mode */
    INITIALIZING,
    
    /** node is in "lukewarm standby" mode, where it is available to be promoted to master,
     * but does not have entities loaded and will require some effort to be promoted */
    STANDBY,
    /** node is acting as read-only proxy */
    HOT_STANDBY,
    /** node is running as primary/master, able to manage entities and create new ones */
    // the semantics are intended to support multi-master here; we could have multiple master nodes,
    // but we need to look up who is master for any given entity
    MASTER,

    /** node has failed and requires maintenance attention */
    FAILED,
    /** node has gone away; maintenance not possible */
    TERMINATED;
}

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

import java.net.URI;

import com.google.common.annotations.Beta;

/**
 * Represents the state of a management-node.
 * 
 * @see {@link ManagementPlaneSyncRecord#getManagementNodes()}
 * 
 * @since 0.7.0
 * 
 * @author aled
 */
@Beta
public interface ManagementNodeSyncRecord {

    // TODO Not setting URI currently; ManagementContext doesn't know its URI; only have one if web-console was enabled.
    
    // TODO Add getPlaneId(); but first need to set it in a sensible way
    
    String getBrooklynVersion();
    
    String getNodeId();
    
    URI getUri();
    
    ManagementNodeState getStatus();

    /** timestamp set by the originating management machine */
    long getLocalTimestamp();

    /** timestamp set by shared persistent store, if available
     * <p>
     * this will not be set on records originating at this machine, nor will it be persisted,
     * but it will be populated for records being read */
    Long getRemoteTimestamp();
    
    String toVerboseString();

}

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

import java.util.Map;

import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoPersister;

import com.google.common.annotations.Beta;

/**
 * Meta-data about the management plane - the management nodes and who is currently master.
 * Does not contain any data about the entities under management.
 * <p>
 * This is very similar to how {@link BrooklynMemento} is used by {@link BrooklynMementoPersister},
 * but it is not a memento in the sense it does not reconstitute the entire management plane
 * (so is not called Memento although it can be used by the same memento-serializers).
 * 
 * @since 0.7.0
 * 
 * @author aled
 */
@Beta
public interface ManagementPlaneSyncRecord {

    // TODO Add getPlaneId(); but first need to set it sensibly on each management node
    
    String getMasterNodeId();
    
    /** returns map of {@link ManagementNodeSyncRecord} instances keyed by the nodes' IDs */
    Map<String, ManagementNodeSyncRecord> getManagementNodes();

    String toVerboseString();
}
